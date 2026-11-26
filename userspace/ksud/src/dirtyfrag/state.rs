//! Boot-scoped state, idempotency/dedup, and corrupt-file recovery.
//!
//! The decision logic (`interpret`, `dedup_decision`, `is_exposed`) is pure and
//! host-testable. Only the `disk` submodule touches the filesystem / Unix APIs.

use serde_json::{Value, json};

use super::error::{STATE_CORRUPT, fail};
use super::report::{AttemptView, S_IDLE, S_SUCCEEDED};

pub const SCHEMA: u32 = 1;
/// Spec §5.4 / §8.4: two calls inside 70s never run the engine twice.
pub const DEDUP_WINDOW_MS: u64 = 70_000;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DState {
    pub schema: u32,
    pub boot_id: String,
    /// Boot in which root was last confirmed exposed.
    pub active_boot: String,
    pub state: &'static str,
    pub last_attempt: Option<AttemptView>,
    pub load_error: Option<(i32, String)>,
}

impl Default for DState {
    fn default() -> Self {
        Self {
            schema: SCHEMA,
            boot_id: String::from("unknown"),
            active_boot: String::new(),
            state: S_IDLE,
            last_attempt: None,
            load_error: None,
        }
    }
}

impl DState {
    pub fn to_json(&self) -> Value {
        json!({
            "schema": self.schema,
            "boot_id": self.boot_id,
            "active_boot": self.active_boot,
            "state": self.state,
            "last_attempt": match &self.last_attempt {
                Some(a) => json!({
                    "request_id": a.request_id,
                    "result": a.result,
                    "code": a.code,
                    "started_ms": a.started_ms,
                    "finished_ms": a.finished_ms,
                }),
                None => Value::Null,
            },
        })
    }
}

fn u64_field(value: &Value, key: &str) -> u64 {
    value.get(key).and_then(Value::as_u64).unwrap_or(0)
}

fn string_field(value: &Value, key: &str) -> String {
    value
        .get(key)
        .and_then(Value::as_str)
        .unwrap_or_default()
        .to_string()
}

fn attempt_from(value: &Value) -> Option<AttemptView> {
    let raw = value.get("last_attempt")?;
    if raw.is_null() {
        return None;
    }
    Some(AttemptView {
        request_id: string_field(raw, "request_id"),
        result: match raw.get("result").and_then(Value::as_str) {
            Some("succeeded") => S_SUCCEEDED,
            _ => "failed",
        },
        code: raw.get("code").and_then(Value::as_i64).unwrap_or(0) as i32,
        started_ms: u64_field(raw, "started_ms"),
        finished_ms: u64_field(raw, "finished_ms"),
    })
}

/// Parse an on-disk state blob. Unknown/missing fields are treated as defaults;
/// a wrong schema or unparseable JSON is corruption (fail-closed, never guess).
pub fn parse_bytes(bytes: &[u8], boot_id: &str) -> Result<DState, ()> {
    let value = serde_json::from_slice::<Value>(bytes).map_err(|_| ())?;
    let schema = value.get("schema").and_then(Value::as_u64).unwrap_or(0);
    if schema != u64::from(SCHEMA) {
        return Err(());
    }
    let mut state = DState {
        schema: SCHEMA,
        boot_id: boot_id.to_string(),
        active_boot: string_field(&value, "active_boot"),
        state: S_IDLE,
        last_attempt: attempt_from(&value),
        load_error: None,
    };
    state.state = match value.get("state").and_then(Value::as_str) {
        Some("running") => "running",
        Some("succeeded") => S_SUCCEEDED,
        Some("failed") => "failed",
        Some("timeout") => "timeout",
        _ => S_IDLE,
    };
    Ok(state)
}

pub fn default_state(boot_id: &str) -> DState {
    DState {
        boot_id: boot_id.to_string(),
        ..DState::default()
    }
}

pub fn reset_from_corrupt(boot_id: &str) -> DState {
    let mut state = default_state(boot_id);
    state.load_error = Some((
        STATE_CORRUPT,
        String::from("状态文件损坏，已备份并重置为默认（state=idle, exposed=false）"),
    ));
    state
}

pub enum LoadOutcome {
    Fresh(DState),
    Loaded(DState),
    Corrupt(DState),
}

/// Pure interpretation of a state file (or its absence). The disk layer calls
/// this and performs the backup/rewrite that the variant calls for; tests call
/// it directly without touching the filesystem.
pub fn interpret(bytes: Option<&[u8]>, boot_id: &str) -> LoadOutcome {
    match bytes {
        None => LoadOutcome::Fresh(default_state(boot_id)),
        Some(raw) => match parse_bytes(raw, boot_id) {
            Ok(state) => LoadOutcome::Loaded(state),
            Err(()) => LoadOutcome::Corrupt(reset_from_corrupt(boot_id)),
        },
    }
}

/// `exposed` is only true in the boot where root was actually confirmed.
pub fn is_exposed(state: &DState) -> bool {
    state.state == S_SUCCEEDED
        && !state.active_boot.is_empty()
        && state.active_boot == state.boot_id
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Dedup {
    Proceed,
    Busy,
    AlreadyRooted,
}

/// Decide whether a `run` may proceed. Any attempt inside the 70s window, or a
/// held lock, returns Busy — the engine must never execute twice concurrently.
pub fn dedup_decision(
    now_ms: u64,
    last: Option<&AttemptView>,
    lock_held: bool,
    exposed: bool,
) -> Dedup {
    if exposed {
        return Dedup::AlreadyRooted;
    }
    if lock_held {
        return Dedup::Busy;
    }
    if let Some(attempt) = last
        && now_ms.saturating_sub(attempt.started_ms) < DEDUP_WINDOW_MS
    {
        return Dedup::Busy;
    }
    Dedup::Proceed
}

pub fn now_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_or(0, |d| d.as_millis() as u64)
}

// ---- disk IO (Android / Unix only) ---------------------------------------

#[cfg(target_os = "android")]
mod disk {
    use super::{DState, LoadOutcome, interpret, now_ms, reset_from_corrupt};
    use crate::dirtyfrag::error::{DResult, IO_ERROR, fail};
    use crate::defs;
    use std::fs::{self, File, OpenOptions};
    use std::io::{ErrorKind, Write};

    fn ensure_dir() -> DResult<()> {
        fs::create_dir_all(defs::DIRTYFRAG_DIR)?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(defs::DIRTYFRAG_DIR, fs::Permissions::from_mode(0o700))?;
        }
        Ok(())
    }

    pub fn boot_id() -> String {
        fs::read_to_string(defs::BOOT_ID_PATH)
            .map(|raw| raw.trim().to_string())
            .ok()
            .filter(|id| !id.is_empty())
            .unwrap_or_else(|| String::from("unknown"))
    }

    fn write_state(state: &DState) -> DResult<()> {
        ensure_dir()?;
        let mut body = serde_json::to_vec_pretty(&state.to_json())
            .map_err(|e| fail(IO_ERROR, format!("serialize state: {e}")))?;
        body.push(b'\n');
        let tmp = format!("{}.tmp", defs::DIRTYFRAG_STATE_PATH);
        {
            let mut file = File::create(&tmp)?;
            file.write_all(&body)?;
            file.sync_all()?;
        }
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&tmp, fs::Permissions::from_mode(0o600))?;
        }
        fs::rename(&tmp, defs::DIRTYFRAG_STATE_PATH)?;
        Ok(())
    }

    fn backup_corrupt() {
        let path = defs::DIRTYFRAG_STATE_PATH;
        let backup = format!("{}.corrupt.{}", path, now_ms());
        if let Err(err) = fs::rename(path, &backup) {
            log::warn!("dirtyfrag: failed to back up corrupt state: {err}");
        }
    }

    /// Load state, repairing corruption fail-closed (backup + reset, §5.5).
    pub fn load() -> DState {
        let boot = boot_id();
        let bytes = match fs::read(defs::DIRTYFRAG_STATE_PATH) {
            Ok(bytes) => Some(bytes),
            Err(err) if err.kind() == ErrorKind::NotFound => None,
            Err(err) => {
                log::warn!("dirtyfrag: failed to read state: {err}");
                return reset_from_corrupt(&boot);
            }
        };
        match interpret(bytes.as_deref(), &boot) {
            LoadOutcome::Fresh(state) => {
                if let Err(err) = write_state(&state) {
                    log::warn!("dirtyfrag: failed to write default state: {err}");
                }
                state
            }
            LoadOutcome::Loaded(state) => state,
            LoadOutcome::Corrupt(state) => {
                backup_corrupt();
                if let Err(err) = write_state(&state) {
                    log::warn!("dirtyfrag: failed to write repaired state: {err}");
                }
                state
            }
        }
    }

    pub fn save(state: &DState) -> DResult<()> {
        write_state(state)
    }

    /// Atomically create the run lock. `None` when another run holds it.
    pub fn try_lock() -> Option<File> {
        ensure_dir().ok()?;
        OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(defs::DIRTYFRAG_LOCK_PATH)
            .ok()
    }

    pub fn release_lock() {
        let _ = fs::remove_file(defs::DIRTYFRAG_LOCK_PATH);
    }
}

#[cfg(not(target_os = "android"))]
mod disk {
    use super::{DState, default_state};
    use crate::dirtyfrag::error::{DResult, UNSUPPORTED_PLATFORM, fail};

    pub fn boot_id() -> String {
        String::from("unknown")
    }
    pub fn load() -> DState {
        default_state("unknown")
    }
    pub fn save(_state: &DState) -> DResult<()> {
        Err(fail(UNSUPPORTED_PLATFORM, "state IO is Android-only"))
    }
    pub fn try_lock() -> Option<std::fs::File> {
        None
    }
    pub fn release_lock() {}
}

pub use disk::{boot_id, load, release_lock, save, try_lock};

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_valid_state_and_roundtrip() {
        let raw = json!({
            "schema": 1,
            "boot_id": "boot-1",
            "active_boot": "boot-1",
            "state": "succeeded",
            "last_attempt": {
                "request_id": "t1", "result": "succeeded", "code": 0,
                "started_ms": 1000, "finished_ms": 2000,
            },
        })
        .to_string();
        let state = parse_bytes(raw.as_bytes(), "boot-1").expect("valid state");
        assert_eq!(state.state, S_SUCCEEDED);
        assert!(is_exposed(&state));
        let again = parse_bytes(state.to_json().to_string().as_bytes(), "boot-1").unwrap();
        assert_eq!(again.last_attempt.as_ref().unwrap().request_id, "t1");
    }

    #[test]
    fn corrupt_state_resets_to_idle_not_guessed() {
        let garbage = b"{not valid json".to_vec();
        match interpret(Some(&garbage), "boot-2") {
            LoadOutcome::Corrupt(state) => {
                assert_eq!(state.state, S_IDLE);
                assert!(!is_exposed(&state));
                assert_eq!(state.load_error.unwrap().0, STATE_CORRUPT);
            }
            _ => panic!("garbage must be treated as corrupt"),
        }
    }

    #[test]
    fn wrong_schema_is_corrupt() {
        let raw = json!({"schema": 99, "state": "succeeded"}).to_string();
        assert!(parse_bytes(raw.as_bytes(), "b").is_err());
    }

    #[test]
    fn fresh_state_defaults_idle() {
        match interpret(None, "boot-3") {
            LoadOutcome::Fresh(state) => {
                assert_eq!(state.state, S_IDLE);
                assert_eq!(state.boot_id, "boot-3");
                assert!(!is_exposed(&state));
            }
            _ => panic!("absent file must be Fresh"),
        }
    }

    #[test]
    fn exposed_only_in_matching_boot() {
        let mut state = default_state("boot-a");
        state.state = S_SUCCEEDED;
        state.active_boot = String::from("boot-a");
        assert!(is_exposed(&state));
        state.boot_id = String::from("boot-b");
        assert!(!is_exposed(&state), "root is cleared on reboot (§5.2)");
    }

    #[test]
    fn dedup_window_blocks_repeat_engine_execution() {
        let recent = AttemptView {
            request_id: String::from("t1"),
            result: S_SUCCEEDED,
            code: 0,
            started_ms: 1000,
            finished_ms: 1100,
        };
        assert_eq!(dedup_decision(1000 + 69_000, Some(&recent), false, false), Dedup::Busy);
        assert_eq!(dedup_decision(1000 + 70_001, Some(&recent), false, false), Dedup::Proceed);
        assert_eq!(dedup_decision(5_000, None, true, false), Dedup::Busy);
        assert_eq!(dedup_decision(5_000, None, false, false), Dedup::Proceed);
    }

    #[test]
    fn dedup_exposed_beats_everything() {
        let recent = AttemptView {
            request_id: String::from("t1"),
            result: S_SUCCEEDED,
            code: 0,
            started_ms: 1000,
            finished_ms: 1100,
        };
        assert_eq!(dedup_decision(1000, Some(&recent), false, true), Dedup::AlreadyRooted);
    }
}
