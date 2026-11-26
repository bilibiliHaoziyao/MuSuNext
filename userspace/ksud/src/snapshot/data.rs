//! Data sources for snapshot backup/restore.
//!
//! Three sources, all read straight from their on-disk representation so this
//! module stays usable without the Android-only ksucalls/module modules:
//!   * modules: `/data/adb/modules/<id>/module.prop` + `disable` marker
//!   * allowlist: kernel-persisted `/data/adb/ksu/.allowlist` (uapi/app_profile.h v4)
//!   * builtins: `/data/adb/musu/state.json`

use anyhow::{Context, Result, bail};
use serde_json::{Value, json};
use std::{
    fs::{self, File, OpenOptions},
    io::Write,
    os::{fd::AsRawFd, unix::fs::{PermissionsExt, chown}},
    path::{Path, PathBuf},
};

#[cfg(target_os = "android")]
pub const MODULE_DIR: &str = crate::defs::MODULE_DIR;
#[cfg(not(target_os = "android"))]
pub const MODULE_DIR: &str = "/data/adb/modules/";

#[cfg(target_os = "android")]
pub const BUILTIN_STATE_PATH: &str = crate::defs::BUILTIN_STATE_PATH;
#[cfg(not(target_os = "android"))]
pub const BUILTIN_STATE_PATH: &str = "/data/adb/musu/state.json";

#[cfg(target_os = "android")]
const BUILTIN_LOCK_PATH: &str = crate::defs::BUILTIN_LOCK_PATH;
#[cfg(not(target_os = "android"))]
const BUILTIN_LOCK_PATH: &str = "/data/adb/musu/.lock";

pub const SNAPSHOT_DIR: &str = "/data/adb/ksu/snapshot/";

pub use super::allowlist::{read_profiles, write_profiles};

// --- modules ---------------------------------------------------------------

fn valid_module_id(id: &str) -> bool {
    !id.is_empty()
        && !id.starts_with('.')
        && id.len() <= 128
        && id
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '_' | '-' | '.'))
}

fn read_prop(path: &Path, key: &str) -> String {
    let Ok(text) = fs::read_to_string(path) else {
        return String::new();
    };
    for line in text.lines() {
        if let Some((k, v)) = line.split_once('=')
            && k.trim() == key
        {
            return v.trim().to_string();
        }
    }
    String::new()
}

pub fn read_modules() -> Result<Vec<Value>> {
    let entries = match fs::read_dir(MODULE_DIR) {
        Ok(entries) => entries,
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => return Ok(Vec::new()),
        Err(err) => return Err(err).context("read module dir"),
    };
    let mut modules = Vec::new();
    for entry in entries.flatten() {
        let path = entry.path();
        let id = entry.file_name().to_string_lossy().into_owned();
        if !path.is_dir() || !valid_module_id(&id) || !path.join("module.prop").is_file() {
            continue;
        }
        modules.push(json!({
            "id": id,
            "enabled": !path.join("disable").exists(),
            "name": read_prop(&path.join("module.prop"), "name"),
            "version": read_prop(&path.join("module.prop"), "version"),
        }));
    }
    modules.sort_by(|a, b| a["id"].as_str().cmp(&b["id"].as_str()));
    Ok(modules)
}

/// Restore only enable/disable markers. Missing modules are left untouched.
pub fn apply_modules(modules: &[Value]) -> Result<(usize, usize)> {
    let mut applied = 0;
    let mut missing = 0;
    for entry in modules {
        let Some(id) = entry.get("id").and_then(Value::as_str) else {
            continue;
        };
        if !valid_module_id(id) {
            log::warn!("snapshot: refusing invalid module id {id:?}");
            continue;
        }
        let dir = Path::new(MODULE_DIR).join(id);
        if !dir.is_dir() {
            missing += 1;
            log::warn!("snapshot: module {id} is not installed, skipping state restore");
            continue;
        }
        let marker = dir.join("disable");
        let enabled = entry.get("enabled").and_then(Value::as_bool).unwrap_or(false);
        if enabled {
            if marker.exists() {
                fs::remove_file(&marker).with_context(|| format!("enable module {id}"))?;
            }
        } else {
            File::create(&marker).with_context(|| format!("disable module {id}"))?;
        }
        applied += 1;
    }
    Ok((applied, missing))
}

// --- builtins --------------------------------------------------------------

fn blank_state() -> Value {
    json!({"schema": 1, "components": {}})
}

fn load_state() -> Value {
    fs::read(BUILTIN_STATE_PATH).map_or_else(|_| blank_state(), |bytes| {
        serde_json::from_slice::<Value>(&bytes).unwrap_or_else(|_| blank_state())
    })
}

pub fn read_builtins() -> Vec<Value> {
    let state = load_state();
    let mut builtins = Vec::new();
    for id in crate::builtin::ComponentId::ALL {
        let enabled = state
            .get("components")
            .and_then(|c| c.get(id.as_str()))
            .and_then(|c| c.get("enabled"))
            .and_then(Value::as_bool)
            .unwrap_or(false);
        builtins.push(json!({"id": id.as_str(), "enabled": enabled}));
    }
    builtins
}

/// Only the `enabled` flag is touched; every other field in state.json
/// (boot ids, overrides, errors) survives a downgrade untouched.
pub fn apply_builtins(builtins: &[Value]) -> Result<usize> {
    let lock = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .open(BUILTIN_LOCK_PATH)?;
    // SAFETY: lock is a live file descriptor owned by this call. Non-blocking
    // matches builtin::with_lock; we never enter a concurrent writer's state.
    if unsafe { libc::flock(lock.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } != 0 {
        bail!("builtin state is locked by another ksud command");
    }
    let result = apply_builtins_locked(builtins);
    let _ = unsafe { libc::flock(lock.as_raw_fd(), libc::LOCK_UN) };
    result
}

fn apply_builtins_locked(builtins: &[Value]) -> Result<usize> {
    let mut state = load_state();
    let components = state
        .get_mut("components")
        .and_then(Value::as_object_mut)
        .context("builtin state has no components object")?;
    let mut applied = 0;
    for entry in builtins {
        let Some(id) = entry.get("id").and_then(Value::as_str) else {
            continue;
        };
        if !crate::builtin::ComponentId::ALL
            .iter()
            .any(|component| component.as_str() == id)
        {
            log::warn!("snapshot: unknown builtin id {id:?}, skipping");
            continue;
        }
        let enabled = entry.get("enabled").and_then(Value::as_bool).unwrap_or(false);
        let component = components
            .entry(id.to_string())
            .or_insert_with(|| json!({}));
        component["enabled"] = json!(enabled);
        applied += 1;
    }

    let path = Path::new(BUILTIN_STATE_PATH);
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
        fs::set_permissions(parent, fs::Permissions::from_mode(0o755))?;
    }
    let mut body = serde_json::to_vec_pretty(&state)?;
    body.push(b'\n');
    let tmp = PathBuf::from(format!("{BUILTIN_STATE_PATH}.snapshot.tmp"));
    {
        let mut file = OpenOptions::new().write(true).create(true).truncate(true).open(&tmp)?;
        file.write_all(&body)?;
        file.sync_all()?;
    }
    fs::set_permissions(&tmp, fs::Permissions::from_mode(0o600))?;
    let _ = chown(&tmp, Some(0), Some(0));
    fs::rename(&tmp, path)?;
    Ok(applied)
}
