//! Android orchestration: detect / run / status / stage command handlers.
//!
//! The engine is a *separate* GPL-2.0-only process launched through the Android
//! activity boundary (Intent / `am start`). There is no dlopen, FFI, or shared
//! address space with the exploit chain (spec C1/C5, §6.2).

use serde_json::Value;

use super::detect::{self, parse_kmi};
use super::error::{
    ALREADY_ROOTED, BAD_REQUEST, DResult, ENGINE_LAUNCH_FAILED, ENGINE_MISSING, EXPLOIT_FAILED,
    KMI_NO_PAYLOAD, KMI_UNKNOWN, MANAGER_MISMATCH, PAYLOAD_MISSING, TIMEOUT, UNSUPPORTED_ARCH,
    fail,
};
use super::payload;
use super::report::{self, AttemptView, DetectView, RunView, StageView, StatusView};
use super::state::{self, Dedup};
use crate::defs;

/// Build-time constant: the locked engine must target MuSu Next, verified at
/// run time so a misbuilt APK cannot silently reach users (§6.2 step 5, 2400).
const LOCKED_MANAGER: &str = "org.musu.next";
const ENGINE_PACKAGE: &str = "df.root";
const LOCKED_EXTRA: &str = "org.musu.next.dirtyfrag.LOCKED_MANAGER";
const ENGINE_ACTIVITY: &str = "df.root/.LockedEntry";
/// Engine window ~70s plus margin (§5.4 / §9 R1).
const ENGINE_WAIT_MS: u64 = 78_000;
const POLL_INTERVAL_MS: u64 = 1_000;

fn check_manager() -> DResult<()> {
    if LOCKED_MANAGER != "org.musu.next" {
        return Err(fail(
            MANAGER_MISMATCH,
            format!("构建错误: LOCKED_MANAGER={LOCKED_MANAGER}，期望 org.musu.next"),
        ));
    }
    Ok(())
}

/// Independent probe: is dfroot.ko loaded AND is `su` reachable? Only valid in
/// this boot (§5.2). Uses the module list plus a real su invocation.
fn verify_exposed() -> bool {
    let ko_loaded = std::fs::read_to_string("/proc/modules")
        .map(|raw| raw.lines().any(|line| line.starts_with("dfroot")))
        .unwrap_or(false);
    if !ko_loaded {
        return false;
    }
    std::process::Command::new("/data/adb/ksu/bin/su")
        .args(["-c", "id -u"])
        .output()
        .map(|o| o.status.success() && o.stdout == b"0\n")
        .unwrap_or(false)
}

/// `detect` — read-only compatibility probe (§5.3). Incompatibility is never
/// an `error`; it is `supported:false` + `reasons`.
pub fn cmd_detect(_input: &Value) -> crate::jsoncmd::JsonOutcome {
    let device = detect::probe_device();
    let payload_state = payload::verify();
    let (verified, payload_sha) = payload_state.as_ref().map_or((false, None), |info| {
        let sha = info.ko_sha256.values().next().cloned();
        (true, sha)
    });
    let mut view: DetectView = detect::classify(device, verified, false);
    view.mat.payload_sha256 = payload_sha;
    view.exposed = state::is_exposed(&state::load());
    view.verdict = detect::verdict(view.exposed, view.supported, view.mat.verified);
    report::detect_json(&view, None)
}

/// `stage` (internal) — stage payload + libksud.so only (§5.1).
pub fn cmd_stage(_input: &Value) -> crate::jsoncmd::JsonOutcome {
    if let Err(err) = check_manager() {
        return report::stage_json("", "", Some(&err));
    }
    let device = detect::probe_device();
    let Some(kmi) = parse_kmi(&device.kernel_release) else {
        let err = fail(KMI_UNKNOWN, "uname 无 androidNN token");
        return report::stage_json("", "", Some(&err));
    };
    let Some(ko) = detect::match_payload(&kmi) else {
        let err = fail(KMI_NO_PAYLOAD, format!("内核 {kmi} 无对应 ko"));
        return report::stage_json("", "", Some(&err));
    };
    match payload::stage(&ko) {
        Ok((_, _, lib_sha)) => report::stage_json(&ko, &lib_sha, None),
        Err(err) => report::stage_json(&ko, "", Some(&err)),
    }
}

/// `status` — current boot exposure + last result (§5.5). Corruption was
/// already backed up + reset by `state::load` (code 2001 is silent).
pub fn cmd_status(_input: &Value) -> crate::jsoncmd::JsonOutcome {
    let state = state::load();
    let exposed = state::is_exposed(&state);
    let view = StatusView {
        exposed,
        state: state.state,
        boot_id: state.boot_id.clone(),
        active_boot: state.active_boot,
        last_attempt: state.last_attempt,
        next: report::NEXT_NO_REBOOT,
        risk_note: report::RISK_NOTE,
    };
    report::status_json(&view, None)
}

/// Launch the locked engine APK through the Android process boundary. The
/// engine itself stages libksud.so from the locked manager (§6.2 step 2).
fn launch_engine() -> DResult<()> {
    let installed = std::process::Command::new("pm")
        .args(["path", ENGINE_PACKAGE])
        .output()
        .map(|o| o.status.success() && !o.stdout.is_empty())
        .unwrap_or(false);
    if !installed {
        return Err(fail(
            ENGINE_MISSING,
            format!("引擎 APK {ENGINE_PACKAGE} 未安装"),
        ));
    }
    let status = std::process::Command::new("am")
        .args([
            "start",
            "-n",
            ENGINE_ACTIVITY,
            "--es",
            LOCKED_EXTRA,
            LOCKED_MANAGER,
        ])
        .status()
        .map_err(|e| fail(ENGINE_LAUNCH_FAILED, e.to_string()))?;
    if !status.success() {
        return Err(fail(
            ENGINE_LAUNCH_FAILED,
            format!("am start 退出码 {status}"),
        ));
    }
    Ok(())
}

fn run_failure(
    request_id: &str,
    code: i32,
    message: &str,
    stages: Vec<StageView>,
    elapsed_ms: u64,
) -> crate::jsoncmd::JsonOutcome {
    let mut state = state::load();
    state.state = if code == TIMEOUT { "timeout" } else { "failed" };
    state.last_attempt = Some(AttemptView {
        request_id: request_id.to_string(),
        result: state.state,
        code,
        started_ms: state::now_ms().saturating_sub(elapsed_ms),
        finished_ms: state::now_ms(),
    });
    let _ = state::save(&state);
    let view = RunView {
        request_id: request_id.to_string(),
        state: state.state,
        elapsed_ms,
        stages,
        ..Default::default()
    };
    report::run_json(&view, Some(&fail(code, message)))
}

fn orchestrate(request_id: &str) -> crate::jsoncmd::JsonOutcome {
    let started = state::now_ms();
    let elapsed = || state::now_ms().saturating_sub(started);
    let mut stages = vec![
        StageView::new("detect", false),
        StageView::new("stage_ksud", false),
        StageView::new("engine_exec", false),
        StageView::new("verify_exposed", false),
    ];

    // 1. detect
    let device = detect::probe_device();
    let verified = payload::verify();
    let ok = verified.is_ok();
    let view = detect::classify(device.clone(), ok, false);
    if !view.supported {
        let (code, msg) = if device.kmi.is_empty() {
            (KMI_UNKNOWN, "uname 无 androidNN token")
        } else if detect::match_payload(&device.kmi).is_none() {
            (KMI_NO_PAYLOAD, "内核无对应 ko，拒绝猜测")
        } else {
            (UNSUPPORTED_ARCH, "ABI 或载荷不匹配")
        };
        return run_failure(request_id, code, msg, stages, elapsed());
    }
    stages[0].ok = true;
    let kmi = device.kmi;
    let ko = view.mat.payload.unwrap_or_default();

    // 2. stage_ksud
    if let Err(err) = check_manager() {
        return run_failure(request_id, err.code, &err.message, stages, elapsed());
    }
    let stage_info = match payload::stage(&ko) {
        Ok(info) => info,
        Err(err) => return run_failure(request_id, err.code, &err.message, stages, elapsed()),
    };
    let lib_sha = stage_info.2;
    stages[1].ok = true;

    // 3. engine_exec (separate process / Intent boundary)
    if let Err(err) = launch_engine() {
        return run_failure(request_id, err.code, &err.message, stages, elapsed());
    }
    stages[2].ok = true;

    // 4. verify_exposed — poll within the bounded window
    let deadline = started.saturating_add(ENGINE_WAIT_MS);
    while state::now_ms() < deadline {
        if verify_exposed() {
            stages[3].ok = true;
            break;
        }
        std::thread::sleep(std::time::Duration::from_millis(POLL_INTERVAL_MS));
    }
    if !stages[3].ok {
        return run_failure(
            request_id,
            TIMEOUT,
            "引擎未在窗口内暴露 root；请勿重启或连点",
            stages,
            elapsed(),
        );
    }

    // success: bind exposure to this boot only (§5.4)
    let boot = state::boot_id();
    let mut persist = state::load();
    persist.state = "succeeded";
    persist.active_boot = boot;
    persist.last_attempt = Some(AttemptView {
        request_id: request_id.to_string(),
        result: "succeeded",
        code: 0,
        started_ms: started,
        finished_ms: state::now_ms(),
    });
    let _ = state::save(&persist);

    let view = RunView {
        exposed: true,
        state: "succeeded",
        request_id: request_id.to_string(),
        elapsed_ms: elapsed(),
        kmi,
        ko,
        libksud_sha256: lib_sha,
        stages,
        next: report::NEXT_NO_REBOOT,
    };
    report::run_json(&view, None)
}

/// `run` — boundary-checked in facade; here we dedup with an atomic lock file
/// and never run the engine twice in the 70s window (§5.4).
pub fn cmd_run(input: &Value, request_id: &str) -> crate::jsoncmd::JsonOutcome {
    if !input.get("confirm").and_then(Value::as_bool).unwrap_or(false) {
        return report::error_json(&fail(BAD_REQUEST, "confirm 必须为 true"), false);
    }
    let persisted = state::load();
    let exposed = state::is_exposed(&persisted);
    // create_new is atomic: `lock` is Some only for the process that owns the
    // lock file. Never unlink a lock held by somebody else.
    let lock = state::try_lock();
    let we_own = lock.is_some();
    let decision = state::dedup_decision(
        state::now_ms(),
        persisted.last_attempt.as_ref(),
        !we_own,
        exposed,
    );
    let outcome = match decision {
        Dedup::AlreadyRooted => report::error_json(
            &fail(ALREADY_ROOTED, "本 boot 已暴露 root，拒绝重跑"),
            true,
        ),
        Dedup::Busy => {
            let err = fail(super::error::BUSY, "70s 窗口内重复调用，引擎不重复执行");
            let view = RunView {
                state: "running",
                request_id: request_id.to_string(),
                ..Default::default()
            };
            report::run_json(&view, Some(&err))
        }
        Dedup::Proceed => orchestrate(request_id),
    };
    if we_own {
        state::release_lock();
    }
    outcome
}
