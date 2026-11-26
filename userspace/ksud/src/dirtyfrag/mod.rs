//! `ksud dirtyfrag` — orchestration facade (docs/specs/dirtyfrag.md §3–§5).
//!
//! MuSu Next only stages, detects, launches the locked engine as a separate
//! process and parses results. No exploit code lives in this tree (C1/C5).
//!
//! Layer layout (§5.7): `error`/`report`/`state`/`detect` are host-safe and
//! always compiled (their syscall edges are cfg-gated inside). The process /
//! payload layer is Android-only; every other platform gets `stub`, which
//! returns a structured 2003 answer while keeping the CLI enum complete.

#![allow(unused_imports)]

mod detect;
mod error;
mod report;
mod state;

#[cfg(target_os = "android")]
mod payload;
#[cfg(target_os = "android")]
mod run;

#[cfg(not(target_os = "android"))]
mod stub;

pub use error::{
    ALREADY_ROOTED, BAD_REQUEST, BOOTSTRAP_FAILED, BUSY, DResult, DError, ENGINE_LAUNCH_FAILED,
    ENGINE_MISSING, EXIT_BUSY, EXIT_ERROR, EXIT_OK, EXIT_UNSUPPORTED, EXPLOIT_FAILED, IO_ERROR,
    KMI_NO_PAYLOAD, KMI_UNKNOWN, MANAGER_MISMATCH, OK, PAYLOAD_MISSING, PAYLOAD_VERIFY_FAILED,
    SELINUX_BLOCKED, STAGE_KSUD_FAILED, STATE_CORRUPT, TIMEOUT, UNSUPPORTED_ARCH,
    UNSUPPORTED_PLATFORM, exit_code, fail,
};
pub use report::{
    AttemptView, DetectView, DeviceView, MatchView, ReasonView, RunView, SCHEMA, StageView,
    StatusView,
};

/// The four `ksud dirtyfrag` subcommands (§5.1).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Command {
    Detect,
    Run,
    Status,
    Stage,
}

/// Parse the single stdin JSON object. Empty input is `{}`; unknown fields are
/// ignored; invalid JSON is BAD_REQUEST. This is a boundary input (§5.1).
fn parse_stdin(raw: &str) -> DResult<serde_json::Value> {
    if raw.trim().is_empty() {
        return Ok(serde_json::json!({}));
    }
    serde_json::from_str::<serde_json::Value>(raw)
        .map_err(|e| fail(BAD_REQUEST, format!("stdin JSON 非法: {e}")))
}

fn require_true(input: &serde_json::Value, key: &str) -> DResult<()> {
    if input.get(key).and_then(serde_json::Value::as_bool) != Some(true) {
        return Err(fail(BAD_REQUEST, format!("缺少 {key}=true（边界校验）")));
    }
    Ok(())
}

fn require_id(input: &serde_json::Value) -> DResult<String> {
    let id = input
        .get("client_request_id")
        .and_then(serde_json::Value::as_str)
        .unwrap_or_default()
        .trim();
    if id.is_empty() {
        return Err(fail(
            BAD_REQUEST,
            "缺少 client_request_id（幂等键）",
        ));
    }
    Ok(id.to_string())
}

/// Execute a subcommand: read one stdin JSON, validate the boundary, dispatch.
/// Returns exactly one `JsonOutcome` for `jsoncmd::emit`.
pub fn execute(command: Command) -> crate::jsoncmd::JsonOutcome {
    let mut raw = String::new();
    if std::io::Read::read_to_string(&mut std::io::stdin(), &mut raw).is_err() {
        return report::error_json(&fail(IO_ERROR, "读取 stdin 失败"), false);
    }
    let input = match parse_stdin(&raw) {
        Ok(value) => value,
        Err(err) => return report::error_json(&err, false),
    };

    match command {
        Command::Detect => platform_detect(&input),
        Command::Status => platform_status(&input),
        Command::Stage => platform_stage(&input),
        Command::Run => {
            if let Err(err) = require_true(&input, "confirm") {
                return report::error_json(&err, false);
            }
            let request_id = match require_id(&input) {
                Ok(id) => id,
                Err(err) => return report::error_json(&err, false),
            };
            platform_run(&input, &request_id)
        }
    }
}

#[cfg(target_os = "android")]
fn platform_detect(input: &serde_json::Value) -> crate::jsoncmd::JsonOutcome {
    run::cmd_detect(input)
}
#[cfg(target_os = "android")]
fn platform_status(input: &serde_json::Value) -> crate::jsoncmd::JsonOutcome {
    run::cmd_status(input)
}
#[cfg(target_os = "android")]
fn platform_stage(input: &serde_json::Value) -> crate::jsoncmd::JsonOutcome {
    run::cmd_stage(input)
}
#[cfg(target_os = "android")]
fn platform_run(input: &serde_json::Value, request_id: &str) -> crate::jsoncmd::JsonOutcome {
    run::cmd_run(input, request_id)
}

#[cfg(not(target_os = "android"))]
fn platform_detect(input: &serde_json::Value) -> crate::jsoncmd::JsonOutcome {
    stub::detect(input)
}
#[cfg(not(target_os = "android"))]
fn platform_status(input: &serde_json::Value) -> crate::jsoncmd::JsonOutcome {
    stub::status(input)
}
#[cfg(not(target_os = "android"))]
fn platform_stage(input: &serde_json::Value) -> crate::jsoncmd::JsonOutcome {
    stub::stage(input)
}
#[cfg(not(target_os = "android"))]
fn platform_run(input: &serde_json::Value, request_id: &str) -> crate::jsoncmd::JsonOutcome {
    stub::run(input, request_id)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_stdin_is_empty_object() {
        let value = parse_stdin("   \n").unwrap();
        assert_eq!(value, serde_json::json!({}));
    }

    #[test]
    fn unknown_fields_are_ignored() {
        let value = parse_stdin(r#"{"confirm":true,"junk":42}"#).unwrap();
        assert_eq!(value["confirm"], true);
        assert_eq!(value["junk"], 42); // parsed but ignored by require_*
    }

    #[test]
    fn invalid_json_is_bad_request() {
        let err = parse_stdin("{oops").unwrap_err();
        assert_eq!(err.code, BAD_REQUEST);
    }

    #[test]
    fn run_requires_confirm_and_request_id() {
        let none = serde_json::json!({});
        assert_eq!(require_true(&none, "confirm").unwrap_err().code, BAD_REQUEST);
        let confirmed = serde_json::json!({"confirm": true});
        assert_eq!(
            require_id(&confirmed).unwrap_err().code,
            BAD_REQUEST
        );
        let full = serde_json::json!({"confirm": true, "client_request_id": "t1"});
        assert!(require_true(&full, "confirm").is_ok());
        assert_eq!(require_id(&full).unwrap(), "t1");
    }
}
