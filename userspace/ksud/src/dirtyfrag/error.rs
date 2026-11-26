//! Error codes and result types for `ksud dirtyfrag`.
//!
//! The integers are part of the CLI contract (docs/specs/dirtyfrag.md §5.6):
//! the manager renders them verbatim, so they must never be renumbered.
//! This file is intentionally free of Unix-only APIs so it compiles on every
//! host (§5.7 / gate G-4).

use std::fmt;

// ---- contract error codes (§5.6) -----------------------------------------
pub const OK: i32 = 0;
pub const IO_ERROR: i32 = 2000;
pub const STATE_CORRUPT: i32 = 2001;
pub const BAD_REQUEST: i32 = 2002;
pub const UNSUPPORTED_PLATFORM: i32 = 2003;
pub const UNSUPPORTED_ARCH: i32 = 2100;
pub const KMI_UNKNOWN: i32 = 2101;
pub const KMI_NO_PAYLOAD: i32 = 2102;
pub const PAYLOAD_VERIFY_FAILED: i32 = 2103;
pub const PAYLOAD_MISSING: i32 = 2104;
pub const SELINUX_BLOCKED: i32 = 2105;
pub const ALREADY_ROOTED: i32 = 2200;
pub const BUSY: i32 = 2201;
pub const ENGINE_MISSING: i32 = 2202;
pub const ENGINE_LAUNCH_FAILED: i32 = 2203;
pub const STAGE_KSUD_FAILED: i32 = 2204;
#[allow(dead_code)]
pub const EXPLOIT_FAILED: i32 = 2300;
pub const TIMEOUT: i32 = 2301;
pub const BOOTSTRAP_FAILED: i32 = 2302;
pub const MANAGER_MISMATCH: i32 = 2400;

// ---- parallel process exit codes (§5.1) ----------------------------------
pub const EXIT_OK: i32 = 0;
pub const EXIT_ERROR: i32 = 1;
pub const EXIT_BUSY: i32 = 2;
pub const EXIT_UNSUPPORTED: i32 = 3;

/// Whether a client may safely retry the same logical operation.
///
/// Deliberately conservative: a payload integrity failure (2103) and an
/// exploit failure (2300) are not retryable — the spec tells users not to
/// retry those (§5.6 / §9 R2). Busy / timeout / launch failures are retryable.
pub const fn retryable(code: i32) -> bool {
    matches!(
        code,
        IO_ERROR | BUSY | ENGINE_LAUNCH_FAILED | TIMEOUT | BOOTSTRAP_FAILED
    )
}

/// Map a contract error code onto the process exit code a script observes.
pub const fn exit_code(code: i32) -> i32 {
    match code {
        OK => EXIT_OK,
        ALREADY_ROOTED | BUSY => EXIT_BUSY,
        UNSUPPORTED_PLATFORM | UNSUPPORTED_ARCH | KMI_UNKNOWN | KMI_NO_PAYLOAD
        | SELINUX_BLOCKED => EXIT_UNSUPPORTED,
        _ => EXIT_ERROR,
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DError {
    pub code: i32,
    pub message: String,
    pub retryable: bool,
}

impl DError {
    pub fn new(code: i32, message: impl Into<String>) -> Self {
        Self {
            code,
            message: message.into(),
            retryable: retryable(code),
        }
    }

    #[allow(dead_code)]
    pub fn with_retryable(code: i32, message: impl Into<String>, retryable: bool) -> Self {
        Self {
            code,
            message: message.into(),
            retryable,
        }
    }
}

impl fmt::Display for DError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{} (code {})", self.message, self.code)
    }
}

impl std::error::Error for DError {}

impl From<std::io::Error> for DError {
    fn from(err: std::io::Error) -> Self {
        Self::new(IO_ERROR, err.to_string())
    }
}

impl From<anyhow::Error> for DError {
    fn from(err: anyhow::Error) -> Self {
        Self::new(IO_ERROR, format!("{err:#}"))
    }
}

pub type DResult<T> = std::result::Result<T, DError>;

pub fn fail(code: i32, message: impl Into<String>) -> DError {
    DError::new(code, message)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn exit_codes_match_contract() {
        assert_eq!(exit_code(OK), 0);
        assert_eq!(exit_code(BUSY), 2);
        assert_eq!(exit_code(ALREADY_ROOTED), 2);
        assert_eq!(exit_code(UNSUPPORTED_PLATFORM), 3);
        assert_eq!(exit_code(KMI_NO_PAYLOAD), 3);
        assert_eq!(exit_code(PAYLOAD_VERIFY_FAILED), 1);
        assert_eq!(exit_code(EXPLOIT_FAILED), 1);
    }

    #[test]
    fn retryable_flags_match_contract() {
        assert!(retryable(BUSY));
        assert!(retryable(TIMEOUT));
        assert!(!retryable(EXPLOIT_FAILED));
        assert!(!retryable(PAYLOAD_VERIFY_FAILED));
        assert!(!retryable(MANAGER_MISMATCH));
    }
}
