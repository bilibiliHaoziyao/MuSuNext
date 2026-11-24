//! Error codes and the error type used by the built-in component commands.
//!
//! The codes are part of the CLI contract (`docs/specs/builtin-components.md` §4.5):
//! the manager displays the integer verbatim, so they must never be renumbered.

use std::fmt;

pub const OK: i32 = 0;
pub const IO_ERROR: i32 = 1000;
pub const STATE_CORRUPT: i32 = 1001;
pub const UNSUPPORTED_ARCH: i32 = 1100;
pub const UNSUPPORTED_ENV_MAGISK: i32 = 1101;
#[allow(dead_code)]
pub const UNSUPPORTED_KERNEL: i32 = 1102;
pub const CONFLICT_ZYGISK_OTHER: i32 = 1200;
pub const CONFLICT_ZYGISK_BUILTIN_ON: i32 = 1201;
pub const CONFLICT_META_OTHER: i32 = 1300;
pub const CONFLICT_META_BUILTIN_ON: i32 = 1301;
pub const PAYLOAD_MISSING: i32 = 1400;
pub const EXTRACT_FAILED: i32 = 1401;
pub const MONITOR_SPAWN_FAILED: i32 = 1500;
#[allow(dead_code)]
pub const INJECT_FAILED_64: i32 = 1501;
#[allow(dead_code)]
pub const INJECT_FAILED_32: i32 = 1502;
pub const NOT_FOUND: i32 = 1600;
pub const SAFE_MODE: i32 = 1601;
pub const METAMOUNT_FAILED: i32 = 1700;
#[allow(dead_code)]
pub const METAMOUNT_DEGRADED: i32 = 1701;
pub const BOOT_GUARD_TRIPPED: i32 = 1702;
pub const READ_ERROR: i32 = 1800;
pub const BUSY: i32 = 1801;

// Process exit codes, parallel to the codes above (§4.1).
pub const EXIT_OK: i32 = 0;
pub const EXIT_ERROR: i32 = 1;
pub const EXIT_CONFLICT: i32 = 2;
pub const EXIT_UNSUPPORTED: i32 = 3;
pub const EXIT_BUSY: i32 = 4;

#[derive(Debug, Clone)]
pub struct BuiltinError {
    pub code: i32,
    pub message: String,
}

impl BuiltinError {
    pub fn new(code: i32, message: impl Into<String>) -> Self {
        Self {
            code,
            message: message.into(),
        }
    }
}

impl fmt::Display for BuiltinError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{} (code {})", self.message, self.code)
    }
}

impl std::error::Error for BuiltinError {}

impl From<std::io::Error> for BuiltinError {
    fn from(err: std::io::Error) -> Self {
        Self::new(IO_ERROR, err.to_string())
    }
}

impl From<anyhow::Error> for BuiltinError {
    fn from(err: anyhow::Error) -> Self {
        Self::new(IO_ERROR, format!("{err:#}"))
    }
}

pub type BResult<T> = std::result::Result<T, BuiltinError>;

pub fn fail(code: i32, message: impl Into<String>) -> BuiltinError {
    BuiltinError::new(code, message)
}

/// Map an error code onto the process exit code a script sees.
pub const fn exit_code(code: i32) -> i32 {
    match code {
        OK => EXIT_OK,
        CONFLICT_ZYGISK_OTHER
        | CONFLICT_ZYGISK_BUILTIN_ON
        | CONFLICT_META_OTHER
        | CONFLICT_META_BUILTIN_ON => EXIT_CONFLICT,
        UNSUPPORTED_ARCH | UNSUPPORTED_ENV_MAGISK | UNSUPPORTED_KERNEL => EXIT_UNSUPPORTED,
        BUSY => EXIT_BUSY,
        _ => EXIT_ERROR,
    }
}
