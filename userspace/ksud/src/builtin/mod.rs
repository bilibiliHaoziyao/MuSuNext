//! Built-in components: MuZygisk and MuMount (the built-in metamodule).
//!
//! This module is the only public entry point used by `cli`, `init_event` and `module`.
//! The six implementation files stay private; their interfaces are re-exported here.
//! Design source: `docs/specs/builtin-components.md`.
// A facade intentionally re-exports interfaces not all consumed by this binary.
#![allow(unused_imports)]

mod error;

#[cfg(target_os = "android")]
mod conflict;
#[cfg(target_os = "android")]
mod payload;
#[cfg(target_os = "android")]
mod report;
#[cfg(target_os = "android")]
mod runtime;
#[cfg(target_os = "android")]
mod state;

pub use error::{
    BResult, BuiltinError, BOOT_GUARD_TRIPPED, BUSY, CONFLICT_META_BUILTIN_ON,
    CONFLICT_META_OTHER, CONFLICT_ZYGISK_BUILTIN_ON, CONFLICT_ZYGISK_OTHER, EXIT_BUSY,
    EXIT_CONFLICT, EXIT_ERROR,
    EXIT_OK, EXIT_UNSUPPORTED, IO_ERROR, METAMOUNT_FAILED, MONITOR_SPAWN_FAILED, NOT_FOUND, OK,
    PAYLOAD_MISSING, READ_ERROR, SAFE_MODE, STATE_CORRUPT, UNSUPPORTED_ARCH, UNSUPPORTED_KERNEL,
    UNSUPPORTED_ENV_MAGISK, exit_code, fail,
};

#[cfg(target_os = "android")]
pub use conflict::{
    Conflict, Detection, Warning, ZYGISK_ABI_FILES, detect, metamodule_conflict, metamodule_ids,
    zip_provides_zygisk, zygisk_abi_evidence, zygisk_conflict, zygisk_provider_ids,
};
#[cfg(target_os = "android")]
pub use payload::{
    PayloadSource, available, component_dir, disk_bytes, ensure_payload, seed_hybrid_config,
    source_meta, version_stamp,
};
#[cfg(target_os = "android")]
pub use report::{
    availability, blocked_metamodule_ids, blocked_zygisk_ids, component_json, list_json,
    status_json,
};
#[cfg(target_os = "android")]
pub use runtime::{
    MetaMountState, MONITOR_BIN, apply_feature_overrides as runtime_apply_feature_overrides,
    boot_epoch, kernel_umount_override,
    link_metamodule, load_sepolies, meta_mount_state, metamodule_active, monitor_alive,
    overlay_supported, read_attempts, restore_feature_overrides, start_zygisk,
    symlink_points_to_builtin, unlink_metamodule, write_attempts,
};
#[cfg(target_os = "android")]
pub use state::{ComponentState, ErrorInfo, FeatureOverride, State, SCHEMA};

/// The two components. `as_str` is the wire id; `dir_name` is the on-disk payload
/// directory (kept as `meta-hybrid` for path compatibility).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum ComponentId {
    Zygisk,
    Metamodule,
}

impl ComponentId {
    pub const ALL: [Self; 2] = [Self::Zygisk, Self::Metamodule];

    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Zygisk => "zygisk",
            Self::Metamodule => "metamodule",
        }
    }

    pub const fn dir_name(self) -> &'static str {
        match self {
            Self::Zygisk => "zygisk",
            Self::Metamodule => "meta-hybrid",
        }
    }

    /// Manager-facing display name. The metamodule was renamed to MuMount; its
    /// internal id (`musu_builtin_metamodule`) and payload path are unchanged.
    pub const fn display_name(self) -> &'static str {
        match self {
            Self::Zygisk => "MuZygisk",
            Self::Metamodule => "MuMount",
        }
    }

    /// CLI aliases from spec §4.1: `muz` / `zygisk`, `meta` / `metamodule`.
    pub fn from_arg(raw: &str) -> Option<Self> {
        match raw.trim().to_ascii_lowercase().as_str() {
            "zygisk" | "muz" | "muzygisk" => Some(Self::Zygisk),
            "metamodule" | "meta" | "mumount" => Some(Self::Metamodule),
            _ => None,
        }
    }
}

impl std::fmt::Display for ComponentId {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.display_name())
    }
}

/// One CLI answer: `json` is the single object written to stdout, `code` is the
/// in-JSON error code (also mapped to a process exit code by `error::exit_code`).
#[derive(Debug, Clone)]
pub struct CommandOutput {
    pub json: String,
    pub code: i32,
}

#[cfg(target_os = "android")]
impl CommandOutput {
    #[allow(clippy::needless_pass_by_value)]
    fn from_value(code: i32, value: serde_json::Value) -> Self {
        Self {
            json: value.to_string(),
            code,
        }
    }
}

#[cfg(target_os = "android")]
#[allow(clippy::wildcard_imports)]
mod platform {
    use super::*;
    use crate::defs;
    use crate::utils;
    use log::warn;
    use serde_json::{Value, json};
    use std::collections::HashMap;
    use std::fs;
    use std::io::ErrorKind;
    use std::os::unix::io::AsRawFd;
    use std::path::Path;
    use std::time::Duration;

    fn err_output(code: i32, message: impl Into<String>) -> CommandOutput {
        CommandOutput::from_value(
            code,
            json!({ "ok": false, "code": code, "message": message.into() }),
        )
    }

    /// `flock(LOCK_EX|LOCK_NB)` on `/data/adb/musu/.lock` (§4.6).
    fn with_lock<T>(op: impl FnOnce() -> BResult<T>) -> BResult<T> {
        State::ensure_dir()?;
        let file = fs::OpenOptions::new()
            .create(true)
            .truncate(false) // a lock file must never be resized by a second opener
            .write(true)
            .open(defs::BUILTIN_LOCK_PATH)?;
        let fd = file.as_raw_fd();
        if unsafe { libc::flock(fd, libc::LOCK_EX | libc::LOCK_NB) } != 0 {
            return Err(fail(BUSY, "builtin 状态正在被其它进程修改，请稍后再试"));
        }
        let result = op();
        unsafe {
            libc::flock(fd, libc::LOCK_UN);
        }
        result
    }

    fn state_stub(id: ComponentId, enabled: bool, state: &State) -> Value {
        let applied = state.component(id.as_str()).applied_boot_id.as_deref()
            == Some(state.boot_id.as_str());
        json!({
            "id": id.as_str(),
            "enabled": enabled,
            "pending_reboot": if enabled == applied { "none" } else { "full" },
        })
    }

    fn denied(
        id: ComponentId,
        state: &State,
        code: i32,
        message: &str,
        conflict: Option<&Conflict>,
    ) -> CommandOutput {
        let mut body = json!({
            "ok": false,
            "code": code,
            "message": message,
            "state": state_stub(id, state.component(id.as_str()).enabled, state),
        });
        if let Some(found) = conflict {
            body["conflict"] = found.to_json();
        }
        CommandOutput::from_value(code, body)
    }

    /// `builtin list` — the manager's only read entry (§4.2).
    // Uniform `BResult` signature across all commands is deliberate for the CLI.
    #[allow(clippy::unnecessary_wraps)]
    pub fn cmd_list() -> BResult<CommandOutput> {
        let state = State::load();
        Ok(CommandOutput::from_value(OK, list_json(&state)))
    }

    /// `builtin status <id>` (§4.1).
    #[allow(clippy::unnecessary_wraps)]
    pub fn cmd_status(raw_id: &str) -> BResult<CommandOutput> {
        let Some(id) = ComponentId::from_arg(raw_id) else {
            return Ok(err_output(
                NOT_FOUND,
                format!("未知的组件 id: {raw_id}"),
            ));
        };
        let state = State::load();
        Ok(CommandOutput::from_value(
            OK,
            status_json(&state, id),
        ))
    }

    /// `builtin enable <id>`: environment → conflict → payload → state (§8.2).
    pub fn cmd_enable(raw_id: &str) -> BResult<CommandOutput> {
        let Some(id) = ComponentId::from_arg(raw_id) else {
            return Ok(err_output(
                NOT_FOUND,
                format!("未知的组件 id: {raw_id}"),
            ));
        };
        with_lock(|| {
            let mut state = State::load();
            if utils::is_safe_mode() {
                return Ok(denied(
                    id,
                    &state,
                    SAFE_MODE,
                    "safe mode 下不允许启用内建组件",
                    None,
                ));
            }
            let (supported, why) = report::availability(id);
            if !supported {
                let info = why.unwrap_or_else(|| {
                    ErrorInfo::new(UNSUPPORTED_ARCH, "当前环境不支持该组件")
                });
                return Ok(denied(id, &state, info.code, &info.message, None));
            }
            let detection = conflict::detect(id);
            if let Some(found) = &detection.conflict {
                let code = match id {
                    ComponentId::Zygisk => CONFLICT_ZYGISK_OTHER,
                    ComponentId::Metamodule => CONFLICT_META_OTHER,
                };
                return Ok(denied(
                    id,
                    &state,
                    code,
                    &format!("检测到 {}（{}）正在占用，请先停用", found.name, found.id),
                    Some(found),
                ));
            }

            payload::ensure_payload(id)?;
            if id == ComponentId::Metamodule {
                payload::seed_hybrid_config()?;
                // Reuse metamodule.rs's symlink path, never a bespoke implementation.
                runtime::link_metamodule()?;
            }

            let mut component = state.component(id.as_str());
            component.enabled = true;
            component.enabled_boot_id = Some(state.boot_id.clone());
            component.last_error = None;
            state.set_component(id.as_str(), &component);
            state.save()?;

            Ok(CommandOutput::from_value(
                OK,
                json!({
                    "ok": true,
                    "code": OK,
                    "message": "已启用，完整重启后生效",
                    "state": state_stub(id, true, &state),
                }),
            ))
        })
    }

    /// `builtin disable <id>`: state is written first, then runtime cleanup (§8.2).
    pub fn cmd_disable(raw_id: &str) -> BResult<CommandOutput> {
        let Some(id) = ComponentId::from_arg(raw_id) else {
            return Ok(err_output(
                NOT_FOUND,
                format!("未知的组件 id: {raw_id}"),
            ));
        };
        with_lock(|| {
            let mut state = State::load();
            let mut component = state.component(id.as_str());
            component.enabled = false;
            component.last_error = None;
            state.set_component(id.as_str(), &component);
            state.save()?;

            match id {
                ComponentId::Zygisk => {
                    runtime::restore_feature_overrides(&mut state)?;
                    stop_our_monitor();
                }
                ComponentId::Metamodule => runtime::unlink_metamodule()?,
            }

            Ok(CommandOutput::from_value(
                OK,
                json!({
                    "ok": true,
                    "code": OK,
                    "message": "已禁用，完整重启后生效",
                    "state": state_stub(id, false, &state),
                }),
            ))
        })
    }

    /// `builtin detect <id>` — read only, never mutates state (§4.1).
    #[allow(clippy::unnecessary_wraps)]
    pub fn cmd_detect(raw_id: &str) -> BResult<CommandOutput> {
        let Some(id) = ComponentId::from_arg(raw_id) else {
            return Ok(err_output(
                NOT_FOUND,
                format!("未知的组件 id: {raw_id}"),
            ));
        };
        let detection = conflict::detect(id);
        Ok(CommandOutput::from_value(
            OK,
            json!({
                "id": id.as_str(),
                "conflict": detection.conflict.as_ref().map(Conflict::to_json),
                "warnings": detection.warnings_json(),
            }),
        ))
    }

    /// `builtin reset`: clear payload + state, keep `/data/adb/hybrid-mount` config.
    pub fn cmd_reset() -> BResult<CommandOutput> {
        with_lock(|| {
            stop_our_monitor();
            if let Err(err) = runtime::unlink_metamodule() {
                warn!("reset: failed to remove metamodule symlink: {err}");
            }
            for id in ComponentId::ALL {
                if let Err(err) = fs::remove_dir_all(payload::component_dir(id))
                    && err.kind() != ErrorKind::NotFound
                {
                    warn!("reset: failed to remove {} payload: {err}", id.as_str());
                }
            }
            if let Err(err) = fs::remove_dir_all(defs::BUILTIN_RUN_DIR)
                && err.kind() != ErrorKind::NotFound
            {
                warn!("reset: failed to remove run dir: {err}");
            }
            if let Err(err) = fs::remove_file(defs::BUILTIN_STATE_PATH)
                && err.kind() != ErrorKind::NotFound
            {
                return Err(err.into());
            }
            let _ = State::load(); // recreate "both off" defaults
            Ok(CommandOutput::from_value(
                OK,
                json!({
                    "ok": true,
                    "code": OK,
                    "message": "已清除载荷与状态，用户配置保持不变",
                }),
            ))
        })
    }

    /// `builtin apply` (hidden): boot orchestration called from post-fs-data.
    pub fn cmd_apply() -> BResult<CommandOutput> {
        apply()?;
        Ok(CommandOutput::from_value(
            OK,
            json!({ "ok": true, "code": OK, "message": "builtin apply 完成" }),
        ))
    }

    /// Post-fs-data row 4: extract both payloads, idempotent (§5.1, §2.3).
    pub fn ensure_payloads() -> BResult<()> {
        let mut state = State::load();
        for id in ComponentId::ALL {
            if !report::availability(id).0 {
                continue;
            }
            if let Err(err) = payload::ensure_payload(id) {
                warn!("extract {} payload failed: {err}", id.as_str());
                if state.component(id.as_str()).enabled {
                    let mut component = state.component(id.as_str());
                    component.last_error = Some(ErrorInfo::new(err.code, err.message.clone()));
                    state.set_component(id.as_str(), &component);
                }
            }
        }
        state.save()
    }

    /// Post-fs-data row 10, right after `load_sepolicy_rule()` (§7.2).
    pub fn load_sepolicy() -> BResult<()> {
        runtime::load_sepolies(&State::load())
    }

    /// Post-fs-data row 12, after feature init (§8.4).
    pub fn apply_feature_overrides() -> BResult<()> {
        let mut state = State::load();
        runtime::apply_feature_overrides(&mut state)
    }

    /// Safe mode (§5.1 row 5): detach our symlink, keep user config and state.
    pub fn safe_mode_detach() -> BResult<()> {
        if runtime::symlink_points_to_builtin().is_some() {
            runtime::unlink_metamodule()?;
        }
        Ok(())
    }

    /// Post-fs-data row 15: consistency check, metamodule boot guard, start monitor.
    pub fn apply() -> BResult<()> {
        let mut state = State::load();
        consistency_check(&mut state)?;

        if state
            .component(ComponentId::Metamodule.as_str())
            .enabled
        {
            let path = defs::BUILTIN_META_ATTEMPTS_PATH;
            let attempts = runtime::read_attempts(path).saturating_add(1);
            runtime::write_attempts(path, attempts);
            if attempts > 3 {
                let mut meta = state.component(ComponentId::Metamodule.as_str());
                meta.enabled = false;
                meta.last_error = Some(ErrorInfo::new(
                    BOOT_GUARD_TRIPPED,
                    String::from("连续 3 次挂载未确认成功，MuMount 已自动关闭"),
                ));
                state.set_component(ComponentId::Metamodule.as_str(), &meta);
                state.save()?;
                runtime::unlink_metamodule()?;
                warn!("MuMount boot guard tripped, skipping metamount this boot");
                return Ok(());
            }
        }

        if let Err(err) = runtime::start_zygisk(&mut state) {
            let mut zygisk = state.component(ComponentId::Zygisk.as_str());
            zygisk.last_error = Some(ErrorInfo::new(err.code, err.message.clone()));
            zygisk.applied_boot_id = None;
            state.set_component(ComponentId::Zygisk.as_str(), &zygisk);
            state.save()?;
            return Err(err);
        }
        Ok(())
    }

    /// Boot completion (§5.1 row 19): confirm evidence, clear / bump counters.
    pub fn on_boot_completed() -> BResult<()> {
        let mut state = State::load();
        let boot = state.boot_id.clone();

        let mut zygisk = state.component(ComponentId::Zygisk.as_str());
        if zygisk.enabled {
            let attempts_path = defs::BUILTIN_ZYGISK_ATTEMPTS_PATH;
            if runtime::monitor_alive() {
                runtime::write_attempts(attempts_path, 0);
                zygisk.applied_boot_id = Some(boot.clone());
                zygisk.last_error = None;
                zygisk.consecutive_boot_failures = 0;
            } else {
                let attempts = runtime::read_attempts(attempts_path).saturating_add(1);
                runtime::write_attempts(attempts_path, attempts);
                zygisk.last_error = Some(ErrorInfo::new(
                    MONITOR_SPAWN_FAILED,
                    String::from("MuZygisk monitor 未存活"),
                ));
                zygisk.applied_boot_id = None;
                zygisk.consecutive_boot_failures = attempts;
                if attempts >= 3 {
                    zygisk.enabled = false;
                    warn!("MuZygisk failed three boots in a row, auto-disabled");
                }
            }
            state.set_component(ComponentId::Zygisk.as_str(), &zygisk);
        }

        let mut meta = state.component(ComponentId::Metamodule.as_str());
        if meta.enabled {
            if runtime::meta_mount_state().confirmed {
                runtime::write_attempts(defs::BUILTIN_META_ATTEMPTS_PATH, 0);
                meta.consecutive_boot_failures = 0;
                meta.applied_boot_id = Some(boot);
                meta.last_error = None;
            } else {
                meta.last_error = Some(ErrorInfo::new(
                    METAMOUNT_FAILED,
                    String::from("MuMount 挂载未确认成功"),
                ));
                meta.consecutive_boot_failures =
                    meta.consecutive_boot_failures.saturating_add(1);
            }
            state.set_component(ComponentId::Metamodule.as_str(), &meta);
        }
        state.save()
    }

    /// §11.4: repair the deterministic cases, never silently rebuild a link the
    /// user may have removed on purpose (D-22).
    fn consistency_check(state: &mut State) -> BResult<()> {
        let link = Path::new(defs::METAMODULE_DIR.trim_end_matches('/'));
        if runtime::symlink_points_to_builtin().is_some() {
            if !state.component(ComponentId::Metamodule.as_str()).enabled {
                runtime::unlink_metamodule()?;
                warn!("removed builtin metamodule symlink while intent is off");
            }
        } else if link.is_symlink() && fs::read_link(link).is_ok() {
            if !link.exists() {
                runtime::unlink_metamodule()?;
                warn!("removed dangling metamodule symlink: {}", link.display());
            } else if state.component(ComponentId::Metamodule.as_str()).enabled {
                let mut meta = state.component(ComponentId::Metamodule.as_str());
                meta.last_error = Some(ErrorInfo::new(
                    CONFLICT_META_OTHER,
                    String::from("MuMount 已启用，但 /data/adb/metamodule 未指向内建目录"),
                ));
                state.set_component(ComponentId::Metamodule.as_str(), &meta);
            }
        }

        let mut zygisk = state.component(ComponentId::Zygisk.as_str());
        if zygisk.enabled && payload::version_stamp(ComponentId::Zygisk) != defs::VERSION_CODE {
            if let Err(err) = payload::ensure_payload(ComponentId::Zygisk) {
                warn!("retry payload extraction failed: {err}");
                zygisk.last_error = Some(ErrorInfo::new(err.code, err.message.clone()));
            }
            state.set_component(ComponentId::Zygisk.as_str(), &zygisk);
        }
        Ok(())
    }

    /// Kill only monitors whose executable lives in our payload tree (D-15).
    fn stop_our_monitor() {
        const OUR_COMM_PREFIX: &str = "muz-zygisk-ptra";
        let Ok(entries) = fs::read_dir("/proc") else {
            return;
        };
        let mut targets = Vec::new();
        for entry in entries.flatten() {
            let pid = entry.file_name().to_string_lossy().to_string();
            if pid.is_empty() || !pid.bytes().all(|b| b.is_ascii_digit()) {
                continue;
            }
            if !runtime::is_our_process(&pid) {
                continue;
            }
            let comm = fs::read_to_string(format!("/proc/{pid}/comm"))
                .unwrap_or_default()
                .trim()
                .to_string();
            if (comm.starts_with(OUR_COMM_PREFIX)
                || runtime::KNOWN_ZYGISK_PROCESSES.contains(&comm.as_str()))
                && let Ok(number) = pid.parse::<i32>()
            {
                targets.push(number);
            }
        }
        for pid in &targets {
            unsafe {
                libc::kill(*pid, libc::SIGTERM);
            }
        }
        if targets.is_empty() {
            return;
        }
        std::thread::sleep(Duration::from_secs(2));
        for pid in targets {
            if runtime::is_our_process(&pid.to_string()) {
                unsafe {
                    libc::kill(pid, libc::SIGKILL);
                }
            }
        }
        let _ = fs::remove_dir_all(defs::BUILTIN_RUN_ZYGISK_DIR);
    }

    /// §6.2 ③: installing a Zygisk or metamodule module while ours is on (1201/1301).
    pub fn check_module_install(zip: &Path, is_metamodule: bool) -> Option<BuiltinError> {
        let state = State::load();
        if state.component(ComponentId::Zygisk.as_str()).enabled
            && conflict::zip_provides_zygisk(zip)
        {
            return Some(fail(
                CONFLICT_ZYGISK_BUILTIN_ON,
                "MuZygisk 已启用，无法安装其它 Zygisk 模块",
            ));
        }
        if state.component(ComponentId::Metamodule.as_str()).enabled && is_metamodule {
            return Some(fail(
                CONFLICT_META_BUILTIN_ON,
                "MuMount 已启用，无法安装其它元模块",
            ));
        }
        None
    }

    /// §6.2 ④: enabling a module that occupies a slot we own.
    pub fn check_module_enable(
        module_path: &Path,
        props: &HashMap<String, String>,
    ) -> Option<BuiltinError> {
        let state = State::load();
        if state.component(ComponentId::Zygisk.as_str()).enabled
            && conflict::zygisk_abi_evidence(module_path).is_some()
        {
            return Some(fail(
                CONFLICT_ZYGISK_BUILTIN_ON,
                "MuZygisk 已启用，无法启用其它 Zygisk 模块",
            ));
        }
        if state.component(ComponentId::Metamodule.as_str()).enabled
            && crate::metamodule::is_metamodule(props)
        {
            return Some(fail(
                CONFLICT_META_BUILTIN_ON,
                "MuMount 已启用，无法启用其它元模块",
            ));
        }
        None
    }

}

#[cfg(target_os = "android")]
pub use platform::{
    apply, apply_feature_overrides, check_module_enable,
    check_module_install, cmd_apply, cmd_detect, cmd_disable, cmd_enable, cmd_list, cmd_reset,
    cmd_status, ensure_payloads, load_sepolicy, on_boot_completed, safe_mode_detach,
};

// The host build only compiles this entry point (the six implementation modules
// depend on Android-only crates). Keeping the same API here means `mod builtin;`
// is unconditional and is type-checked by a plain `cargo check` as well.
#[cfg(not(target_os = "android"))]
#[allow(dead_code)]
mod host {
    use super::{BResult, CommandOutput};
    use std::collections::HashMap;
    use std::path::Path;

    fn unavailable() -> CommandOutput {
        CommandOutput {
            json: String::from(
                "{\"ok\":false,\"code\":1600,\"message\":\"builtin components are Android-only\"}",
            ),
            code: super::NOT_FOUND,
        }
    }

    pub fn cmd_list() -> BResult<CommandOutput> {
        Ok(unavailable())
    }
    pub fn cmd_status(_id: &str) -> BResult<CommandOutput> {
        Ok(unavailable())
    }
    pub fn cmd_enable(_id: &str) -> BResult<CommandOutput> {
        Ok(unavailable())
    }
    pub fn cmd_disable(_id: &str) -> BResult<CommandOutput> {
        Ok(unavailable())
    }
    pub fn cmd_detect(_id: &str) -> BResult<CommandOutput> {
        Ok(unavailable())
    }
    pub fn cmd_reset() -> BResult<CommandOutput> {
        Ok(unavailable())
    }
    pub fn cmd_apply() -> BResult<CommandOutput> {
        Ok(unavailable())
    }
    pub fn apply() -> BResult<()> {
        Ok(())
    }
    pub fn ensure_payloads() -> BResult<()> {
        Ok(())
    }
    pub fn load_sepolicy() -> BResult<()> {
        Ok(())
    }
    pub fn apply_feature_overrides() -> BResult<()> {
        Ok(())
    }
    pub fn safe_mode_detach() -> BResult<()> {
        Ok(())
    }
    pub fn on_boot_completed() -> BResult<()> {
        Ok(())
    }
    pub fn check_module_install(
        _zip: &Path,
        _is_metamodule: bool,
    ) -> Option<super::BuiltinError> {
        None
    }
    pub fn check_module_enable(
        _path: &Path,
        _props: &HashMap<String, String>,
    ) -> Option<super::BuiltinError> {
        None
    }
}

#[cfg(not(target_os = "android"))]
pub use host::{
    apply, apply_feature_overrides, check_module_enable,
    check_module_install, cmd_apply, cmd_detect, cmd_disable, cmd_enable, cmd_list, cmd_reset,
    cmd_status, ensure_payloads, load_sepolicy, on_boot_completed, safe_mode_detach,
};
