//! Shared stdout discipline for the JSON-only subcommands.
//!
//! Each command returns a single `Value`; logs go to logd through `log::*`.
//! Nothing else in these code paths is allowed to write to stdout.

use serde_json::Value;

/// A command result: one JSON object plus the parallel process exit code.
pub struct JsonOutcome {
    pub json: Value,
    pub code: i32,
}

impl JsonOutcome {
    pub fn ok(json: Value) -> Self {
        Self { json, code: 0 }
    }

    pub fn fail(json: Value) -> Self {
        Self { json, code: 1 }
    }
}

pub fn error(message: impl Into<String>) -> JsonOutcome {
    JsonOutcome::fail(serde_json::json!({
        "ok": false,
        "error": message.into(),
    }))
}

/// Print the single JSON object and terminate. Never returns.
pub fn emit(outcome: &JsonOutcome) -> ! {
    use std::io::Write;
    println!("{}", outcome.json);
    let _ = std::io::stdout().flush();
    std::process::exit(outcome.code);
}
