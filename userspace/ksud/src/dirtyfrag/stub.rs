//! Non-Android stub — every CLI enum stays complete, but actions are refused
//! with a structured `UNSUPPORTED_PLATFORM` (2003) answer (spec §5.7).

use serde_json::Value;

use super::detect;
use super::error::{UNSUPPORTED_PLATFORM, fail};
use super::report::{
    self, DetectView, ReasonView, RunView, StatusView, V_UNSUPPORTED, S_FAILED,
};

fn unsupported(context: &str) -> crate::jsoncmd::JsonOutcome {
    let err = fail(
        UNSUPPORTED_PLATFORM,
        format!("dirtyfrag {context} 在当前平台不受支持（仅 Android/arm64）"),
    );
    report::error_json(&err, false)
}

/// Same-shape detect JSON (§5.7) with code 2003: the CLI enum and field set
/// stay complete even though the host cannot probe an Android device.
pub fn detect(_input: &Value) -> crate::jsoncmd::JsonOutcome {
    let err = fail(UNSUPPORTED_PLATFORM, "dirtyfrag detect 在当前平台不受支持（仅 Android/arm64）");
    let mut view = DetectView {
        verdict: V_UNSUPPORTED,
        ..DetectView::default()
    };
    view.reasons.push(ReasonView::new(
        detect::R_KMI_UNKNOWN,
        detect::reason_severity(detect::R_KMI_UNKNOWN),
        String::from("当前平台不受支持，无法探测 androidNN token"),
    ));
    report::detect_json(&view, Some(&err))
}

pub fn status(_input: &Value) -> crate::jsoncmd::JsonOutcome {
    unsupported("status")
}

pub fn stage(_input: &Value) -> crate::jsoncmd::JsonOutcome {
    unsupported("stage")
}

pub fn run(_input: &Value, _request_id: &str) -> crate::jsoncmd::JsonOutcome {
    // Keep the run-shaped fields present even on the stub, so a script sees a
    // stable envelope (ok=false, error.code=2003, state=failed).
    let err = fail(UNSUPPORTED_PLATFORM, "dirtyfrag run 在当前平台不受支持");
    let view = RunView {
        state: S_FAILED,
        ..RunView::default()
    };
    report::run_json(&view, Some(&err))
}

#[allow(dead_code)]
fn status_shape_reference() -> StatusView {
    StatusView::default()
}
