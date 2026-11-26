//! `state.json` — the single source of truth for both built-in components.
//!
//! Layout and field semantics: `docs/specs/builtin-components.md` §3. The file is
//! parsed by hand into `serde_json` values so that component ids this build does not
//! know about survive a write (downgrade must not drop user intent, §3.2).

use crate::defs;
use log::{info, warn};
use serde_json::{Map, Value, json};
use std::{
    fs::{self, File},
    io::{ErrorKind, Write},
    os::unix::fs::{PermissionsExt, chown},
    path::Path,
    time::{SystemTime, UNIX_EPOCH},
};

use super::error::{BResult, IO_ERROR, READ_ERROR, STATE_CORRUPT, fail};

pub const SCHEMA: u32 = 1;
const STATE_MODE: u32 = 0o600;

#[derive(Debug, Clone)]
pub struct ErrorInfo {
    pub code: i32,
    pub message: String,
}

impl ErrorInfo {
    pub fn new(code: i32, message: impl Into<String>) -> Self {
        Self {
            code,
            message: message.into(),
        }
    }

    fn from_json(value: &Value) -> Option<Self> {
        let code = value.get("code")?.as_i64()?;
        let message = value
            .get("message")
            .and_then(Value::as_str)
            .unwrap_or_default()
            .to_string();
        Some(Self {
            code: i32::try_from(code).ok()?,
            message,
        })
    }

    pub fn to_json(&self) -> Value {
        json!({ "code": self.code, "message": self.message })
    }
}

#[derive(Debug, Clone, Default)]
pub struct FeatureOverride {
    pub kernel_umount_saved: Option<u64>,
    pub applied: bool,
}

#[derive(Debug, Clone)]
pub struct ComponentState {
    pub enabled: bool,
    pub enabled_boot_id: Option<String>,
    pub applied_boot_id: Option<String>,
    pub payload_version: String,
    pub consecutive_boot_failures: u32,
    pub last_error: Option<ErrorInfo>,
    pub feature_override: FeatureOverride,
}

impl Default for ComponentState {
    fn default() -> Self {
        Self {
            enabled: false,
            enabled_boot_id: None,
            applied_boot_id: None,
            payload_version: String::from("0"),
            consecutive_boot_failures: 0,
            last_error: None,
            feature_override: FeatureOverride::default(),
        }
    }
}

fn opt_string(value: &Value, key: &str) -> Option<String> {
    value.get(key).and_then(Value::as_str).map(str::to_string)
}

fn feature_override_from(value: &Value) -> FeatureOverride {
    let Some(kernel_umount) = value.get("feature_override").and_then(|v| v.get("kernel_umount"))
    else {
        return FeatureOverride::default();
    };
    FeatureOverride {
        kernel_umount_saved: kernel_umount.get("saved").and_then(Value::as_u64),
        applied: kernel_umount
            .get("applied")
            .and_then(Value::as_bool)
            .unwrap_or(false),
    }
}

impl ComponentState {
    fn from_json(value: &Value) -> Self {
        Self {
            enabled: value
                .get("enabled")
                .and_then(Value::as_bool)
                .unwrap_or(false),
            enabled_boot_id: opt_string(value, "enabled_boot_id"),
            applied_boot_id: opt_string(value, "applied_boot_id"),
            payload_version: opt_string(value, "payload_version")
                .unwrap_or_else(|| String::from("0")),
            consecutive_boot_failures: value
                .get("consecutive_boot_failures")
                .and_then(Value::as_u64)
                .and_then(|v| u32::try_from(v).ok())
                .unwrap_or(0),
            last_error: value.get("last_error").and_then(ErrorInfo::from_json),
            feature_override: feature_override_from(value),
        }
    }

    fn to_json(&self) -> Value {
        let mut map = Map::new();
        map.insert(String::from("enabled"), Value::Bool(self.enabled));
        map.insert(
            String::from("enabled_boot_id"),
            json!(self.enabled_boot_id),
        );
        map.insert(
            String::from("applied_boot_id"),
            json!(self.applied_boot_id),
        );
        map.insert(
            String::from("payload_version"),
            Value::String(self.payload_version.clone()),
        );
        map.insert(
            String::from("consecutive_boot_failures"),
            json!(self.consecutive_boot_failures),
        );
        map.insert(
            String::from("last_error"),
            self.last_error.as_ref().map_or(Value::Null, ErrorInfo::to_json),
        );
        let mut over = Map::new();
        over.insert(
            String::from("kernel_umount"),
            json!({
                "saved": self.feature_override.kernel_umount_saved,
                "applied": self.feature_override.applied,
            }),
        );
        map.insert(String::from("feature_override"), Value::Object(over));
        Value::Object(map)
    }
}

pub struct State {
    pub schema: u32,
    pub boot_id: String,
    components: Map<String, Value>,
    /// Set when the on-disk state had to be repaired this run (code 1001 / 1800).
    pub load_error: Option<ErrorInfo>,
}

impl State {
    /// Kernel generated boot id: unchanged by soft reboot, changes on a full reboot.
    pub fn current_boot_id() -> String {
        fs::read_to_string("/proc/sys/kernel/random/boot_id")
            .map(|raw| raw.trim().to_string())
            .ok()
            .filter(|id| !id.is_empty())
            .unwrap_or_else(|| {
                warn!("cannot read /proc/sys/kernel/random/boot_id, using fallback");
                String::from("unknown")
            })
    }

    fn blank(last_error: Option<ErrorInfo>) -> Self {
        let mut components = Map::new();
        for id in super::ComponentId::ALL {
            let component = ComponentState {
                last_error: last_error.clone(),
                ..ComponentState::default()
            };
            components.insert(String::from(id.as_str()), component.to_json());
        }
        Self {
            schema: SCHEMA,
            boot_id: Self::current_boot_id(),
            components,
            load_error: last_error.filter(|e| e.code == STATE_CORRUPT),
        }
    }

    fn from_json(value: &Value) -> Option<Self> {
        let schema = value.get("schema")?.as_u64().and_then(|v| u32::try_from(v).ok())?;
        if schema != SCHEMA {
            warn!("unknown state schema {schema}, treating state as corrupt");
            return None;
        }
        let components = value.get("components")?.as_object()?.clone();
        Some(Self {
            schema,
            boot_id: Self::current_boot_id(),
            components,
            load_error: None,
        })
    }

    /// Read the state file, repairing it in memory (and on disk) when it is broken.
    pub fn load() -> Self {
        let path = Path::new(defs::BUILTIN_STATE_PATH);
        let bytes = match fs::read(path) {
            Ok(bytes) => bytes,
            Err(err) if err.kind() == ErrorKind::NotFound => {
                info!("builtin state not found, writing defaults (both components off)");
                let mut state = Self::blank(None);
                if let Err(e) = state.save() {
                    warn!("failed to write default builtin state: {e}");
                }
                return state;
            }
            Err(err) => {
                warn!("failed to read builtin state: {err}");
                return Self {
                    load_error: Some(ErrorInfo::new(READ_ERROR, err.to_string())),
                    ..Self::blank(None)
                };
            }
        };

        let parsed = serde_json::from_slice::<Value>(&bytes)
            .ok()
            .as_ref()
            .and_then(Self::from_json);
        let Some(mut state) = parsed else {
            let backup = Self::backup_corrupt(path);
            warn!("builtin state is corrupt, backed up to {backup} and reset to defaults");
            let mut fresh = Self::blank(Some(ErrorInfo::new(
                STATE_CORRUPT,
                "状态文件损坏，已备份并重置为默认（两个组件均为关闭）",
            )));
            if let Err(e) = fresh.save() {
                warn!("failed to write repaired builtin state: {e}");
            }
            return fresh;
        };

        state.fix_permissions(path);
        state
    }

    fn backup_corrupt(path: &Path) -> String {
        let ts = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
        let backup = format!("{}.corrupt.{ts}", path.display());
        if let Err(e) = fs::rename(path, &backup) {
            warn!("failed to back up corrupt state: {e}");
        }
        backup
    }

    /// Ownership/permission tampering is repaired in place, never trusted (§3.2).
    fn fix_permissions(&mut self, path: &Path) {
        let Ok(meta) = fs::metadata(path) else {
            return;
        };
        if meta.permissions().mode() & 0o777 != STATE_MODE {
            info!("builtin state mode is not 0600, rewriting it");
            if let Err(e) = self.save() {
                warn!("failed to rewrite builtin state with mode 0600: {e}");
            }
        }
    }

    pub fn component(&self, id: &str) -> ComponentState {
        self.components
            .get(id)
            .map(ComponentState::from_json)
            .unwrap_or_default()
    }

    pub fn set_component(&mut self, id: &str, component: &ComponentState) {
        self.components
            .insert(String::from(id), component.to_json());
    }

    /// tmp + fsync + rename, mode 0600, root:root (§3.1, §3.2).
    pub fn save(&mut self) -> BResult<()> {
        Self::ensure_dir()?;
        self.schema = SCHEMA;
        self.boot_id = Self::current_boot_id();

        let value = json!({
            "schema": self.schema,
            "boot_id": self.boot_id,
            "components": Value::Object(self.components.clone()),
        });
        let mut body = serde_json::to_vec_pretty(&value)
            .map_err(|e| fail(IO_ERROR, format!("serialize state failed: {e}")))?;
        body.push(b'\n');

        let tmp = format!("{}.tmp", defs::BUILTIN_STATE_PATH);
        {
            let mut file = File::create(&tmp)?;
            file.write_all(&body)?;
            file.sync_all()?;
        }
        fs::set_permissions(&tmp, fs::Permissions::from_mode(STATE_MODE))?;
        let _ = chown(&tmp, Some(0), Some(0));
        fs::rename(&tmp, defs::BUILTIN_STATE_PATH)?;
        Ok(())
    }

    pub fn ensure_dir() -> BResult<()> {
        let dir = Path::new(defs::BUILTIN_DIR);
        fs::create_dir_all(dir)?;
        fs::set_permissions(dir, fs::Permissions::from_mode(0o755))?;
        Ok(())
    }
}
