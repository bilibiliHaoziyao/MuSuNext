//! JSON assembly for every `ksud dirtyfrag` subcommand.
//!
//! All builders live here so the field names and the `ok`/`error`/`exposed`
//! semantics (docs/specs/dirtyfrag.md §5.2) have a single source of truth.
//! This file is host-safe: only `serde_json`, no Unix-only API (gate G-4).

use serde_json::{Value, json};

use crate::jsoncmd::JsonOutcome;

use super::error::{DError, exit_code};

pub const SCHEMA: u32 = 1;
pub const LOCKED_MANAGER: &str = "org.musu.next";
pub const ENGINE_PACKAGE: &str = "df.root";
pub const ENGINE_MODE: &str = "locked";
pub const PAYLOAD_VERSION: &str = "1.18";
pub const PAYLOAD_LICENSE: &str = "GPL-2.0-only";
pub const PAYLOAD_SOURCE: &str = "dirtyfrag/SOURCE.txt";

/// Ordered risk list shown by the UI (§5.3); never localised, machine readable.
pub const RISKS: [&str; 3] = [
    "root_cleared_on_reboot",
    "setenforce_permissive",
    "kernel_conflict_on_repeat",
];
pub const RISK_NOTE: &str = "root_cleared_on_reboot";
pub const NEXT_NO_REBOOT: &str = "no_reboot_needed";

// verdict values (§5.3)
pub const V_READY: &str = "ready";
pub const V_UNSUPPORTED: &str = "unsupported";
pub const V_ALREADY_ROOTED: &str = "already_rooted";
pub const V_UNKNOWN: &str = "unknown";

// reason severities (§5.3)
pub const SEV_BLOCKER: &str = "blocker";
pub const SEV_WARNING: &str = "warning";

// run states (§5.4)
#[allow(dead_code)]
pub const S_RUNNING: &str = "running";
pub const S_SUCCEEDED: &str = "succeeded";
pub const S_FAILED: &str = "failed";
#[allow(dead_code)]
pub const S_TIMEOUT: &str = "timeout";
pub const S_IDLE: &str = "idle";

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct DeviceView {
    pub kmi: String,
    pub kernel_release: String,
    pub android_release: String,
    pub abi: String,
    pub selinux: String,
}

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct MatchView {
    pub payload: Option<String>,
    pub matched_by: Option<String>,
    pub payload_sha256: Option<String>,
    pub verified: bool,
}

impl MatchView {
    #[allow(dead_code)]
    pub fn none() -> Self {
        Self::default()
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ReasonView {
    pub code: String,
    pub severity: String,
    pub message: String,
}

impl ReasonView {
    pub fn new(code: impl Into<String>, severity: impl Into<String>, message: impl Into<String>) -> Self {
        Self {
            code: code.into(),
            severity: severity.into(),
            message: message.into(),
        }
    }

    pub fn to_json(&self) -> Value {
        json!({
            "code": self.code,
            "severity": self.severity,
            "message": self.message,
        })
    }
}

#[derive(Debug, Clone, Default)]
pub struct DetectView {
    pub exposed: bool,
    pub supported: bool,
    pub verdict: &'static str,
    pub device: DeviceView,
    pub mat: MatchView,
    pub reasons: Vec<ReasonView>,
}

#[derive(Debug, Clone)]
pub struct StageView {
    pub name: &'static str,
    pub ok: bool,
}

impl StageView {
    pub const fn new(name: &'static str, ok: bool) -> Self {
        Self { name, ok }
    }
}

#[derive(Debug, Clone)]
pub struct RunView {
    pub exposed: bool,
    pub state: &'static str,
    pub request_id: String,
    pub elapsed_ms: u64,
    pub kmi: String,
    pub ko: String,
    pub libksud_sha256: String,
    pub stages: Vec<StageView>,
    pub next: &'static str,
}

impl Default for RunView {
    fn default() -> Self {
        Self {
            exposed: false,
            state: S_IDLE,
            request_id: String::new(),
            elapsed_ms: 0,
            kmi: String::new(),
            ko: String::new(),
            libksud_sha256: String::new(),
            stages: Vec::new(),
            next: NEXT_NO_REBOOT,
        }
    }
}

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct AttemptView {
    pub request_id: String,
    pub result: &'static str,
    pub code: i32,
    pub started_ms: u64,
    pub finished_ms: u64,
}

#[derive(Debug, Clone)]
pub struct StatusView {
    pub exposed: bool,
    pub state: &'static str,
    pub boot_id: String,
    pub active_boot: String,
    pub last_attempt: Option<AttemptView>,
    pub next: &'static str,
    pub risk_note: &'static str,
}

impl Default for StatusView {
    fn default() -> Self {
        Self {
            exposed: false,
            state: S_IDLE,
            boot_id: String::new(),
            active_boot: String::new(),
            last_attempt: None,
            next: NEXT_NO_REBOOT,
            risk_note: RISK_NOTE,
        }
    }
}

fn envelope(ok: bool, error: Option<&DError>) -> Value {
    let error_value = error.map_or(Value::Null, |err| {
        json!({
            "code": err.code,
            "message": err.message,
            "retryable": err.retryable,
        })
    });
    json!({
        "ok": ok,
        "schema": SCHEMA,
        "error": error_value,
    })
}

const fn outcome(code: i32, body: Value) -> JsonOutcome {
    let code = exit_code(code);
    let json = body;
    // JsonOutcome.fail sets code 1; we need the parallel exit code, so build
    // the struct directly (its fields are public).
    JsonOutcome { json, code }
}

fn device_json(d: &DeviceView) -> Value {
    json!({
        "kmi": d.kmi,
        "kernel_release": d.kernel_release,
        "android_release": d.android_release,
        "abi": d.abi,
        "selinux": d.selinux,
    })
}

fn match_json(m: &MatchView) -> Value {
    json!({
        "payload": m.payload,
        "matched_by": m.matched_by,
        "payload_sha256": m.payload_sha256,
        "verified": m.verified,
    })
}

fn payload_json() -> Value {
    json!({
        "version": PAYLOAD_VERSION,
        "license": PAYLOAD_LICENSE,
        "source": PAYLOAD_SOURCE,
    })
}

fn engine_json() -> Value {
    json!({
        "package": ENGINE_PACKAGE,
        "mode": ENGINE_MODE,
        "manager": LOCKED_MANAGER,
    })
}

fn finish(mut body: Value, exposed: bool, code: i32) -> JsonOutcome {
    body["exposed"] = json!(exposed);
    outcome(code, body)
}

/// `detect` output (§5.3). `error` is None for a credible probe — including an
/// unsupported device (compatibility goes into `supported`/`reasons`, not `error`).
pub fn detect_json(v: &DetectView, error: Option<&DError>) -> JsonOutcome {
    let mut body = envelope(error.is_none(), error);
    body["supported"] = json!(v.supported);
    body["verdict"] = json!(v.verdict);
    body["device"] = device_json(&v.device);
    body["match"] = match_json(&v.mat);
    body["reasons"] = json!(v.reasons.iter().map(ReasonView::to_json).collect::<Vec<_>>());
    body["risks"] = json!(RISKS);
    body["payload"] = payload_json();
    body["engine"] = engine_json();
    let code = error.map_or(0, |e| e.code);
    finish(body, v.exposed, code)
}

/// `run` output (§5.4). On BUSY the caller passes `state=S_RUNNING` plus the
/// error, matching the "second click returns running" contract.
pub fn run_json(v: &RunView, error: Option<&DError>) -> JsonOutcome {
    let mut body = envelope(error.is_none(), error);
    body["state"] = json!(v.state);
    body["request_id"] = json!(v.request_id);
    body["elapsed_ms"] = json!(v.elapsed_ms);
    body["used"] = json!({
        "kmi": v.kmi,
        "ko": v.ko,
        "manager": LOCKED_MANAGER,
        "libksud_sha256": v.libksud_sha256,
    });
    body["stages"] = json!(
        v.stages
            .iter()
            .map(|s| json!({ "name": s.name, "ok": s.ok }))
            .collect::<Vec<_>>()
    );
    body["next"] = json!(v.next);
    let code = error.map_or(0, |e| e.code);
    finish(body, v.exposed, code)
}

/// `status` output (§5.5).
pub fn status_json(v: &StatusView, error: Option<&DError>) -> JsonOutcome {
    let mut body = envelope(error.is_none(), error);
    body["state"] = json!(v.state);
    body["boot_id"] = json!(v.boot_id);
    body["active_boot"] = json!(v.active_boot);
    body["last_attempt"] = v.last_attempt.as_ref().map_or(Value::Null, |a| {
        json!({
            "request_id": a.request_id,
            "result": a.result,
            "code": a.code,
            "started_ms": a.started_ms,
            "finished_ms": a.finished_ms,
        })
    });
    body["next"] = json!(v.next);
    body["risk_note"] = json!(v.risk_note);
    let code = error.map_or(0, |e| e.code);
    finish(body, v.exposed, code)
}

/// `stage` output (internal, §5.1). The manager never shows it directly.
pub fn stage_json(ko: &str, libksud_sha256: &str, error: Option<&DError>) -> JsonOutcome {
    let mut body = envelope(error.is_none(), error);
    body["state"] = json!("staged");
    body["ko"] = json!(ko);
    body["libksud_sha256"] = json!(libksud_sha256);
    let code = error.map_or(0, |e| e.code);
    finish(body, false, code)
}

/// A minimal but contract-shaped answer for an error that has no view data
/// (e.g. BAD_REQUEST on stdin, or a non-Android stub).
pub fn error_json(err: &DError, exposed: bool) -> JsonOutcome {
    let mut body = envelope(false, Some(err));
    body["state"] = json!(S_FAILED);
    finish(body, exposed, err.code)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::dirtyfrag::error::{BAD_REQUEST, BUSY};

    #[test]
    fn detect_shape_and_semantics() {
        let view = DetectView {
            exposed: false,
            supported: false,
            verdict: V_UNSUPPORTED,
            device: DeviceView {
                kmi: String::from("android14-6.1"),
                kernel_release: String::from("6.1.157-android14-11-o-xxxx"),
                android_release: String::from("14"),
                abi: String::from("arm64-v8a"),
                selinux: String::from("enforcing"),
            },
            mat: MatchView::none(),
            reasons: vec![ReasonView::new(
                "KMI_NO_PAYLOAD",
                SEV_BLOCKER,
                "no ko",
            )],
        };
        let out = detect_json(&view, None);
        assert_eq!(out.code, 0);
        assert_eq!(out.json["ok"], true);
        assert_eq!(out.json["error"], Value::Null);
        assert_eq!(out.json["schema"], 1);
        assert_eq!(out.json["exposed"], false);
        assert_eq!(out.json["supported"], false);
        assert_eq!(out.json["verdict"], "unsupported");
        assert_eq!(out.json["engine"]["manager"], "org.musu.next");
        assert_eq!(out.json["engine"]["package"], "df.root");
        assert_eq!(out.json["engine"]["mode"], "locked");
        assert_eq!(out.json["payload"]["version"], "1.18");
        assert_eq!(out.json["payload"]["license"], "GPL-2.0-only");
        assert_eq!(out.json["reasons"][0]["code"], "KMI_NO_PAYLOAD");
        assert_eq!(out.json["reasons"][0]["severity"], "blocker");
        assert_eq!(out.json["risks"].as_array().map_or(0, Vec::len), 3);
        assert_eq!(out.json["match"]["verified"], false);
        assert_eq!(out.json["match"]["payload"], Value::Null);
    }

    #[test]
    fn error_object_has_three_fields() {
        let err = DError::new(BAD_REQUEST, "bad");
        let out = error_json(&err, false);
        assert_eq!(out.code, 1);
        assert_eq!(out.json["ok"], false);
        assert_eq!(out.json["error"]["code"], 2002);
        assert_eq!(out.json["error"]["retryable"], false);
        assert_eq!(out.json["error"].as_object().map_or(0, |m| m.len()), 3);
    }

    #[test]
    fn busy_run_is_running_state_with_error() {
        let mut view = RunView::default();
        view.state = S_RUNNING;
        view.request_id = String::from("t1");
        let err = DError::new(BUSY, "dedup");
        let out = run_json(&view, Some(&err));
        assert_eq!(out.code, 2);
        assert_eq!(out.json["state"], "running");
        assert_eq!(out.json["error"]["code"], 2201);
        assert_eq!(out.json["used"]["manager"], "org.musu.next");
    }

    #[test]
    fn status_shape() {
        let view = StatusView {
            exposed: true,
            state: S_SUCCEEDED,
            boot_id: String::from("b1"),
            active_boot: String::from("b1"),
            last_attempt: Some(AttemptView {
                request_id: String::from("t1"),
                result: S_SUCCEEDED,
                code: 0,
                started_ms: 1000,
                finished_ms: 2000,
            }),
            ..StatusView::default()
        };
        let out = status_json(&view, None);
        assert_eq!(out.code, 0);
        assert_eq!(out.json["exposed"], true);
        assert_eq!(out.json["active_boot"], "b1");
        assert_eq!(out.json["last_attempt"]["code"], 0);
        assert_eq!(out.json["risk_note"], "root_cleared_on_reboot");
    }
}
