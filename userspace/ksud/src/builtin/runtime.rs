//! Runtime side of the built-in components: spawn the MuZygisk monitor, forward its
//! sepolicy rules, take over `kernel_umount`, and read the evidence used by `ui_state`.

use crate::defs;
use crate::feature_id::FeatureId;
use crate::{ksucalls, metamodule, restorecon};
use log::{info, warn};
use serde_json::Value;
use std::{
    fs,
    os::unix::{fs::PermissionsExt, process::CommandExt},
    path::Path,
    process::{Command, Stdio},
    time::{SystemTime, UNIX_EPOCH},
};

use super::ComponentId;
use super::error::{BResult, MONITOR_SPAWN_FAILED, PAYLOAD_MISSING, fail};
use super::payload;
use super::state::{FeatureOverride, State};

/// The ptracer doubles as the monitor; it execs the per-ABI daemons itself.
pub const MONITOR_BIN: &str = "muz-zygisk-ptrace64";
/// Upstream names are still matched so an installed third-party build is detected (A3).
pub const KNOWN_ZYGISK_PROCESSES: [&str; 8] = [
    "zygiskd",
    "zygiskd64",
    "zygiskd32",
    "zygisk-ptrace64",
    "zygisk-ptrace32",
    "muz-zygiskd64",
    "muz-zygiskd32",
    "muz-zygisk-ptrace32",
];

/// `comm` is capped at 15 bytes, so the renamed monitor shows up truncated.
const MONITOR_COMM_PREFIX: &str = "muz-zygisk-ptra";

fn process_exe(pid: &str) -> Option<String> {
    fs::read_link(format!("/proc/{pid}/exe"))
        .ok()
        .map(|path| path.to_string_lossy().to_string())
}

fn process_comm(pid: &str) -> Option<String> {
    fs::read_to_string(format!("/proc/{pid}/comm"))
        .ok()
        .map(|raw| raw.trim().to_string())
}

fn pids() -> Vec<String> {
    fs::read_dir("/proc")
        .map(|dir| {
            dir.flatten()
                .filter_map(|entry| {
                    let name = entry.file_name().to_string_lossy().to_string();
                    name.chars().all(|c| c.is_ascii_digit()).then_some(name)
                })
                .filter(|name| !name.is_empty())
                .collect()
        })
        .unwrap_or_default()
}

/// True when the given pid belongs to our own payload tree.
pub fn is_our_process(pid: &str) -> bool {
    process_exe(pid).is_some_and(|exe| exe.starts_with(defs::BUILTIN_DIR))
}

/// Running evidence for `ui_state=active` (§4.3): our monitor is alive.
pub fn monitor_alive() -> bool {
    let ours = payload::component_dir(ComponentId::Zygisk)
        .to_string_lossy()
        .to_string();
    for pid in pids() {
        if let Some(exe) = process_exe(&pid)
            && exe.starts_with(&ours)
            && exe.ends_with(MONITOR_BIN)
        {
            return true;
        }
        if process_comm(&pid).is_some_and(|comm| comm.starts_with(MONITOR_COMM_PREFIX)) {
            return true;
        }
    }
    false
}

/// `/data/adb/musu/run/zygisk` must be 0555 + `system_file` before the monitor starts (§7.1).
fn prepare_run_dir() -> BResult<()> {
    let dir = Path::new(defs::BUILTIN_RUN_ZYGISK_DIR);
    fs::create_dir_all(dir)?;
    // Label first, then drop the write bits: chcon after chmod would need to bypass them.
    if let Err(err) = restorecon::lsetfilecon(dir, restorecon::SYSTEM_CON) {
        warn!("failed to label {}: {err}", dir.display());
    }
    fs::set_permissions(dir, fs::Permissions::from_mode(0o555))?;
    Ok(())
}

/// Fork+exec the monitor detached, log to `log/zygisk.log`, never wait (§5.1 row 15).
pub fn start_zygisk(state: &mut State) -> BResult<()> {
    let mut zygisk = state.component(ComponentId::Zygisk.as_str());
    if !zygisk.enabled {
        return Ok(());
    }

    prepare_run_dir()?;
    crate::utils::ensure_dir_exists(defs::LOG_DIR)?;
    let dir = payload::component_dir(ComponentId::Zygisk);
    let binary = format!("./bin/{MONITOR_BIN}");
    if !dir.join(format!("bin/{MONITOR_BIN}")).exists() {
        return Err(fail(
            PAYLOAD_MISSING,
            format!("monitor 缺少可执行文件 {binary}"),
        ));
    }

    let log = fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(defs::BUILTIN_ZYGISK_LOG_PATH)?;
    let mut command = Command::new(&binary);
    command
        .arg("monitor")
        // The monitor execs its daemons by relative path (U2), so the cwd is the payload.
        .current_dir(&dir)
        .stdin(Stdio::null())
        .stdout(Stdio::from(log.try_clone()?))
        .stderr(Stdio::from(log));
    unsafe {
        command.pre_exec(|| {
            // New session: the monitor must survive ksud exiting and init reaping us.
            if libc::setsid() < 0 {
                return Err(std::io::Error::last_os_error());
            }
            Ok(())
        });
    }

    let child = command
        .spawn()
        .map_err(|err| fail(MONITOR_SPAWN_FAILED, format!("启动 monitor 失败: {err}")))?;
    info!("MuZygisk monitor spawned, pid {}", child.id());
    // Dropped without wait on purpose: it is reparented to init and must outlive us.
    drop(child);

    zygisk.applied_boot_id = Some(state.boot_id.clone());
    zygisk.last_error = None;
    zygisk.consecutive_boot_failures = 0;
    state.set_component(ComponentId::Zygisk.as_str(), &zygisk);
    state.save()?;
    Ok(())
}

/// Forward the payload's `sepolicy.rule` through the existing patch path (§7.2).
pub fn load_sepolies(state: &State) -> BResult<()> {
    if !state.component(ComponentId::Zygisk.as_str()).enabled {
        return Ok(());
    }
    let rule = payload::component_dir(ComponentId::Zygisk).join("sepolicy.rule");
    if !rule.exists() {
        return Err(fail(PAYLOAD_MISSING, "MuZygisk 载荷缺少 sepolicy.rule"));
    }
    crate::sepolicy::apply_file(&rule)?;
    info!("applied builtin sepolicy: {}", rule.display());
    Ok(())
}

fn kernel_umount_id() -> u32 {
    FeatureId::KernelUmount as u32
}

/// MuZygisk replaces the module-declared `managedFeatures=kernel_umount` (§8.4).
pub fn apply_feature_overrides(state: &mut State) -> BResult<()> {
    let mut zygisk = state.component(ComponentId::Zygisk.as_str());
    if !zygisk.enabled {
        return Ok(());
    }
    let id = kernel_umount_id();
    let Ok(current) = ksucalls::get_feature(id) else {
        warn!("cannot read kernel_umount, skip feature override");
        return Ok(());
    };
    if current.forced {
        warn!("kernel_umount is forced by boot config, skip feature override");
        return Ok(());
    }
    if zygisk.feature_override.kernel_umount_saved.is_none() {
        zygisk.feature_override.kernel_umount_saved = Some(current.value);
    }
    if current.value != 0 {
        ksucalls::set_feature(id, 0)?;
        info!(
            "MuZygisk enabled: kernel_umount set to 0 (was {})",
            current.value
        );
    }
    zygisk.feature_override.applied = true;
    state.set_component(ComponentId::Zygisk.as_str(), &zygisk);
    state.save()?;
    Ok(())
}

/// Put the user's original `kernel_umount` value back on disable (§8.2 step 5).
pub fn restore_feature_overrides(state: &mut State) -> BResult<()> {
    let mut zygisk = state.component(ComponentId::Zygisk.as_str());
    let Some(saved) = zygisk.feature_override.kernel_umount_saved else {
        return Ok(());
    };
    let id = kernel_umount_id();
    match ksucalls::get_feature(id) {
        Ok(state_now) if !state_now.forced && state_now.value != saved => {
            ksucalls::set_feature(id, saved)?;
            info!("restored kernel_umount to {saved}");
        }
        Ok(_) => {}
        Err(err) => warn!("cannot restore kernel_umount: {err:#}"),
    }
    zygisk.feature_override = FeatureOverride::default();
    state.set_component(ComponentId::Zygisk.as_str(), &zygisk);
    state.save()?;
    Ok(())
}

/// `kernel_umount` override currently in effect, for the detail block.
pub fn kernel_umount_override(state: &State) -> Option<u64> {
    let zygisk = state.component(ComponentId::Zygisk.as_str());
    if zygisk.feature_override.applied {
        zygisk.feature_override.kernel_umount_saved
    } else {
        None
    }
}

pub fn read_attempts(path: &str) -> u32 {
    fs::read_to_string(path)
        .ok()
        .and_then(|raw| raw.trim().parse().ok())
        .unwrap_or(0)
}

pub fn write_attempts(path: &str, value: u32) {
    let written = (|| -> std::io::Result<()> {
        fs::create_dir_all(defs::BUILTIN_RUN_DIR)?;
        fs::write(path, value.to_string())?;
        Ok(())
    })();
    if let Err(err) = written {
        warn!("failed to write boot attempts {path}: {err}");
    }
}

pub fn boot_epoch() -> Option<u64> {
    let uptime = fs::read_to_string("/proc/uptime").ok()?;
    let secs: f64 = uptime.split_whitespace().next()?.parse().ok()?;
    let now = SystemTime::now().duration_since(UNIX_EPOCH).ok()?.as_secs();
    Some(now.saturating_sub(secs as u64))
}

#[derive(Debug, Default, Clone)]
pub struct MetaMountState {
    pub confirmed: bool,
    pub degraded: bool,
    pub mounted: u64,
    pub failed: u64,
    pub scope: Vec<String>,
}

fn timestamp_of(value: &Value) -> Option<u64> {
    match value.get("timestamp") {
        Some(Value::Number(number)) => number.as_u64(),
        Some(Value::String(text)) => text.trim().parse().ok(),
        _ => None,
    }
}

fn arr_len(value: &Value, key: &str) -> u64 {
    value
        .get(key)
        .and_then(Value::as_array)
        .map_or(0, |items| items.len() as u64)
}

fn top_level_of(item: &Value) -> Option<String> {
    let raw = match item {
        Value::String(text) => text.clone(),
        other => ["target", "mount_point", "path", "mountpoint"]
            .iter()
            .find_map(|key| other.get(key).and_then(Value::as_str))
            .map(str::to_string)?,
    };
    let trimmed = raw.trim_start_matches('/');
    let first = trimmed.split('/').next()?.to_string();
    (!first.is_empty()).then(|| format!("/{first}"))
}

/// Read `/data/adb/hybrid-mount/run/state.json` (U9) as the metamodule's evidence.
pub fn meta_mount_state() -> MetaMountState {
    let mut state = MetaMountState::default();
    let Ok(raw) = fs::read_to_string(defs::HYBRID_MOUNT_STATE_PATH) else {
        return state;
    };
    let Ok(value) = serde_json::from_str::<Value>(&raw) else {
        warn!("cannot parse {}", defs::HYBRID_MOUNT_STATE_PATH);
        return state;
    };

    state.mounted = arr_len(&value, "active_mounts");
    state.failed = arr_len(&value, "mount_error_modules");
    state.degraded = value
        .get("degraded")
        .and_then(Value::as_bool)
        .unwrap_or(false);
    if let Some(items) = value.get("active_mounts").and_then(Value::as_array) {
        for item in items {
            if let Some(scope) = top_level_of(item)
                && !state.scope.contains(&scope)
            {
                state.scope.push(scope);
            }
        }
    }
    state.scope.sort();

    state.confirmed = match (timestamp_of(&value), boot_epoch()) {
        (Some(stamp), Some(epoch)) => stamp.saturating_add(5) >= epoch,
        // Without an uptime reference we cannot disprove it; trust the file.
        (Some(_), None) => true,
        _ => false,
    };
    state
}

/// Metamodule running evidence: the symlink points at our payload and the mount ran.
pub fn metamodule_active(state: &State) -> bool {
    if !state.component(ComponentId::Metamodule.as_str()).enabled {
        return false;
    }
    let linked = crate::metamodule::get_metamodule_path().is_some_and(|path| {
        path == payload::component_dir(ComponentId::Metamodule)
            || path == Path::new(defs::BUILTIN_DIR).join(ComponentId::Metamodule.dir_name())
    });
    linked && meta_mount_state().confirmed
}

/// Symlink helpers, so the "does it point at us" question lives in one place.
pub fn symlink_points_to_builtin() -> Option<std::path::PathBuf> {
    let link = Path::new(defs::METAMODULE_DIR.trim_end_matches('/'));
    if !link.is_symlink() {
        return None;
    }
    let target = fs::read_link(link).ok()?;
    let resolved = if target.is_absolute() {
        target
    } else {
        link.parent()?.join(target)
    };
    (resolved == payload::component_dir(ComponentId::Metamodule)).then_some(resolved)
}

pub fn link_metamodule() -> BResult<()> {
    metamodule::ensure_symlink(&payload::component_dir(ComponentId::Metamodule))?;
    Ok(())
}

pub fn unlink_metamodule() -> BResult<()> {
    metamodule::remove_symlink()?;
    Ok(())
}

/// `/proc/filesystems` is the cheapest honest answer for "can this kernel overlay".
pub fn overlay_supported() -> bool {
    fs::read_to_string("/proc/filesystems").is_ok_and(|raw| {
        raw.lines()
            .any(|line| line.split_whitespace().last() == Some("overlay"))
    })
}
