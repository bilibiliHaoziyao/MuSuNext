//! JSON the manager reads: `builtin list` / `builtin status`.
//!
//! `ui_state` and `pending_reboot` are computed here and nowhere else (§4.3): the
//! manager must not re-derive them from `state.json`.

use crate::defs;
use crate::utils;
use serde_json::{Map, Value, json};
use std::collections::HashMap;
use std::fs;

use super::ComponentId;
use super::conflict::{self, Detection};
use super::error::{
    CONFLICT_META_BUILTIN_ON, CONFLICT_ZYGISK_BUILTIN_ON, READ_ERROR, UNSUPPORTED_ARCH,
    UNSUPPORTED_ENV_MAGISK, UNSUPPORTED_KERNEL,
};
use super::payload;
use super::runtime;
use super::state::{ErrorInfo, State};

/// Whether the component can run in this environment, and why not (§4.5, §10).
pub fn availability(id: ComponentId) -> (bool, Option<ErrorInfo>) {
    if utils::has_magisk() {
        return (
            false,
            Some(ErrorInfo::new(
                UNSUPPORTED_ENV_MAGISK,
                "检测到 Magisk，MuSu Next 不与 Magisk 共存",
            )),
        );
    }
    if !payload::available(id) {
        return (
            false,
            Some(ErrorInfo::new(
                UNSUPPORTED_ARCH,
                format!("当前架构没有 {} 载荷", id.display_name()),
            )),
        );
    }
    if id == ComponentId::Metamodule && !runtime::overlay_supported() {
        return (
            false,
            Some(ErrorInfo::new(
                UNSUPPORTED_KERNEL,
                "内核未提供 overlayfs，MuMount 不可用",
            )),
        );
    }
    (true, None)
}

fn flatten(value: &Value, prefix: &str, out: &mut HashMap<String, bool>) {
    let Some(map) = value.as_object() else {
        return;
    };
    for (key, item) in map {
        let path = if prefix.is_empty() {
            key.clone()
        } else {
            format!("{prefix}.{key}")
        };
        if let Some(flag) = item.as_bool() {
            out.insert(path, flag);
        } else if item.is_object() {
            flatten(item, &path, out);
        }
    }
}

fn monitor_flags() -> HashMap<String, bool> {
    let mut flags = HashMap::new();
    if let Ok(raw) = fs::read_to_string(defs::BUILTIN_RUN_ZYGISK_STATE)
        && let Ok(value) = serde_json::from_str::<Value>(&raw)
    {
        flatten(&value, "", &mut flags);
    }
    flags
}

fn injected(flags: &HashMap<String, bool>, abi: &str) -> bool {
    flags.iter().any(|(path, value)| {
        let leaf = path.rsplit('.').next().unwrap_or(path);
        (leaf == "zygote_injected" || leaf == "injected")
            && path.contains(abi)
            && *value
    })
}

fn find_u64(value: &Value, key: &str) -> Option<u64> {
    match value {
        Value::Object(map) => map
            .get(key)
            .and_then(Value::as_u64)
            .or_else(|| map.values().find_map(|item| find_u64(item, key))),
        Value::Array(items) => items.iter().find_map(|item| find_u64(item, key)),
        _ => None,
    }
}

fn zygisk_metrics() -> Value {
    let flags = monitor_flags();
    let raw = fs::read_to_string(defs::BUILTIN_RUN_ZYGISK_STATE)
        .ok()
        .and_then(|raw| serde_json::from_str::<Value>(&raw).ok());
    let loaded = raw.as_ref().and_then(|v| find_u64(v, "modules_loaded")).unwrap_or(0);
    json!({
        "zygote64_injected": injected(&flags, "64"),
        "zygote32_injected": injected(&flags, "32"),
        "zygote32_covered": zygote32_covered(),
        "modules_loaded": loaded,
        "modules_total": conflict::zygisk_provider_ids().len(),
    })
}

/// A 32-bit daemon is only shipped for some architectures (§10.1).
fn zygote32_covered() -> bool {
    payload::component_dir(ComponentId::Zygisk)
        .join("bin/muz-zygiskd32")
        .exists()
        && std::path::Path::new("/system/bin/app_process32").exists()
}

fn metamodule_metrics() -> Value {
    let state = runtime::meta_mount_state();
    json!({
        "mounted_modules": state.mounted,
        "failed_modules": state.failed,
        "scope": state.scope,
        "degraded": state.degraded,
        "mount_confirmed": state.confirmed,
    })
}

fn detail(id: ComponentId, state: &State) -> Value {
    let source = payload::source_meta(id);
    let mut map = Map::new();
    map.insert(String::from("version"), Value::String(source.version));
    map.insert(String::from("upstream"), Value::String(source.upstream));
    map.insert(String::from("upstream_url"), Value::String(source.upstream_url));
    map.insert(
        String::from("upstream_commit"),
        Value::String(source.upstream_commit),
    );
    map.insert(String::from("license"), Value::String(source.license));
    map.insert(
        String::from("disk_bytes"),
        json!(payload::disk_bytes(id)),
    );
    map.insert(
        String::from("payload_version"),
        Value::String(payload::version_stamp(id)),
    );
    if id == ComponentId::Zygisk {
        map.insert(
            String::from("kernel_umount_saved"),
            json!(runtime::kernel_umount_override(state)),
        );
    }
    Value::Object(map)
}

#[allow(clippy::fn_params_excessive_bools)]
fn ui_state(
    state: &State,
    id: ComponentId,
    available: bool,
    blocked: bool,
    applied_now: bool,
    running: bool,
) -> &'static str {
    let component = state.component(id.as_str());
    if state
        .load_error
        .as_ref()
        .is_some_and(|err| err.code == READ_ERROR)
    {
        "read_error"
    } else if !available {
        "unsupported"
    } else if blocked {
        "blocked"
    } else if component.enabled && component.last_error.is_some() {
        "failed"
    } else if component.enabled && applied_now && running {
        "active"
    } else if !component.enabled && applied_now {
        "pending_disable"
    } else if component.enabled && !applied_now {
        "pending_enable"
    } else {
        "off"
    }
}

pub fn component_json(state: &State, id: ComponentId, detection: &Detection) -> Value {
    let (available, unsupported) = availability(id);
    let component = state.component(id.as_str());
    let applied_now = component.applied_boot_id.as_deref() == Some(state.boot_id.as_str());
    let running = match id {
        ComponentId::Zygisk => runtime::monitor_alive(),
        ComponentId::Metamodule => runtime::metamodule_active(state),
    };
    let blocked = detection.conflict.is_some();
    let ui = ui_state(state, id, available, blocked, applied_now, running);
    let error = component.last_error.clone().or(unsupported);
    // Both directions need a reboot: enabling needs post-fs-data to run again,
    // disabling cannot roll back an in-memory injection / an applied overlay (§8.1).
    let pending = if component.enabled == applied_now {
        "none"
    } else {
        "full"
    };

    let metrics = match id {
        ComponentId::Zygisk => zygisk_metrics(),
        ComponentId::Metamodule => metamodule_metrics(),
    };

    json!({
        "id": id.as_str(),
        "name": id.display_name(),
        "ui_state": ui,
        "enabled": component.enabled,
        "pending_reboot": pending,
        "available": available,
        "blocking_others": component.enabled,
        "applied_boot_id": component.applied_boot_id,
        "conflict": detection.conflict.as_ref().map(conflict::Conflict::to_json),
        "error": error.as_ref().map(ErrorInfo::to_json),
        "warnings": detection.warnings_json(),
        "metrics": metrics,
        "detail": detail(id, state),
    })
}

/// Ids the module list must grey out while we occupy the Zygisk slot (§6.4, Q-1 strict).
pub fn blocked_zygisk_ids(state: &State) -> Vec<String> {
    if state.component(ComponentId::Zygisk.as_str()).enabled {
        conflict::zygisk_provider_ids()
    } else {
        Vec::new()
    }
}

pub fn blocked_metamodule_ids(state: &State) -> Vec<String> {
    if state.component(ComponentId::Metamodule.as_str()).enabled {
        conflict::metamodule_ids()
    } else {
        Vec::new()
    }
}

pub fn list_json(state: &State) -> Value {
    let mut components = Vec::new();
    for id in ComponentId::ALL {
        let detection = conflict::detect(id);
        components.push(component_json(state, id, &detection));
    }

    let blocked = blocked_zygisk_ids(state);
    let blocked_meta = blocked_metamodule_ids(state);
    let both_active = components
        .iter()
        .filter(|item| item["ui_state"] == "active")
        .count()
        == 2;
    let mut warnings = Vec::new();
    if both_active {
        warnings.push(json!({
            "code": "both_active",
            "message": "MuZygisk 与 MuMount 同时运行中，若模块加载异常请先单独排查",
        }));
    }

    json!({
        "schema": state.schema,
        "boot_id": state.boot_id,
        "components": components,
        "blocked_module_ids": blocked,
        "blocked_reason_code": if blocked.is_empty() { Value::Null } else { json!(CONFLICT_ZYGISK_BUILTIN_ON) },
        "blocked_metamodule_ids": blocked_meta,
        "blocked_metamodule_reason_code": if blocked_meta.is_empty() { Value::Null } else { json!(CONFLICT_META_BUILTIN_ON) },
        "warnings": warnings,
    })
}

pub fn status_json(state: &State, id: ComponentId) -> Value {
    let detection = conflict::detect(id);
    json!({
        "schema": state.schema,
        "boot_id": state.boot_id,
        "component": component_json(state, id, &detection),
    })
}
