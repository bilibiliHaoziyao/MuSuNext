//! `ksud envcheck` — run read-only detection probes as an *unauthorized* UID.
//!
//! Scanning from root would only prove that root exists. The probes run in a
//! forked child after setgroups/setgid/setuid, optionally switching to the
//! untrusted_app SELinux domain. Results go back over a pipe; the child's
//! stdout is /dev/null so the manager always sees exactly one JSON object.

use crate::jsoncmd::JsonOutcome;
#[cfg(unix)]
use crate::{jsoncmd::error, snapshot::data};
#[cfg(unix)]
use anyhow::{Result, bail};
#[cfg(unix)]
use chrono::Local;
#[cfg(unix)]
use serde_json::Value;
use serde_json::json;
#[cfg(unix)]
use std::{fs::File, io::Read, os::fd::FromRawFd};

#[cfg(unix)]
const PROBES: &[(&str, &str)] = &[
    ("su_binary", "su 可执行文件"),
    ("adb_dir", "/data/adb 目录可列出"),
    ("ksu_dir", "/data/adb/ksu 目录可见"),
    ("module_mounts", "模块 overlay 挂载暴露"),
    ("ksud_process", "ksud 进程可见"),
    ("zygisk_process", "zygiskd / muZygisk 进程可见"),
    ("selinux_status", "SELinux 宽容模式可读"),
    ("debuggable", "ro.debuggable / ro.secure 可读"),
    ("magisk_files", "Magisk 常见路径存在"),
];

/// Pick a UID that is definitely not in the root grant list. A high app UID is
/// preferred; shell (2000) is only used when its profile does not allow su.
#[cfg(unix)]
fn select_unauthorized_uid() -> Result<u32> {
    let profiles: Vec<Value> = data::read_profiles()?;
    let allowed = |uid: u32| {
        profiles.iter().any(|profile| {
            profile.get("curr_uid").and_then(Value::as_i64) == Some(i64::from(uid))
                && profile.get("allow_su").and_then(Value::as_bool) == Some(true)
        })
    };
    for uid in 20000..20_100 {
        if !allowed(uid) {
            return Ok(uid);
        }
    }
    if !allowed(2000) {
        return Ok(2000);
    }
    bail!("no unauthorized uid available for the probe")
}

// --- individual probes (read-only) -----------------------------------------

#[cfg(unix)]
mod probes;
#[cfg(unix)]
use probes::{
    probe_adb_dir, probe_debuggable, probe_ksu_dir, probe_magisk_files, probe_module_mounts,
    probe_selinux_status, probe_su_binary, visible_process,
};

// --- child / parent plumbing ------------------------------------------------

#[cfg(unix)]
fn write_line(fd: i32, line: &str) {
    let framed = format!("{line}\n");
    let mut rest = framed.as_bytes();
    while !rest.is_empty() {
        // SAFETY: rest is a valid byte slice with at least one element.
        let written = unsafe { libc::write(fd, rest.as_ptr().cast(), rest.len()) };
        if written > 0 {
            rest = &rest[written as usize..];
        } else if written < 0 && std::io::Error::last_os_error().raw_os_error() == Some(libc::EINTR) {
            // Interrupted before writing anything; retry the same bytes.
        } else {
            return;
        }
    }
}

#[cfg(unix)]
fn try_untrusted_context() -> (bool, String) {
    #[cfg(target_os = "android")]
    {
        let target = "u:r:untrusted_app:s0";
        let written = std::fs::write("/proc/thread-self/attr/current", target.as_bytes());
        let current = std::fs::read_to_string("/proc/self/attr/current")
            .unwrap_or_default()
            .trim()
            .to_string();
        (written.is_ok() && current.contains("untrusted_app"), current)
    }
    #[cfg(not(target_os = "android"))]
    {
        (true, String::from("n/a (non-Android host)"))
    }
}

#[cfg(unix)]
fn child_main(pipe_fd: i32, uid: u32) {
    // Bound the child so a hanging /proc read cannot stall ksud.
    unsafe { libc::alarm(15) };
    let devnull = unsafe { libc::open(c"/dev/null".as_ptr(), libc::O_WRONLY) };
    if devnull >= 0 {
        unsafe {
            libc::dup2(devnull, 1);
            libc::dup2(devnull, 2);
            libc::close(devnull);
        }
    }

    let drop_result = (|| -> Result<()> {
        // Same order as su.rs: clear supplementary groups, then gid, then uid.
        if unsafe { libc::setgroups(0, std::ptr::null()) } != 0 {
            bail!("setgroups failed: {}", std::io::Error::last_os_error());
        }
        if unsafe { libc::setgid(uid) } != 0 {
            bail!("setgid failed: {}", std::io::Error::last_os_error());
        }
        if unsafe { libc::setuid(uid) } != 0 {
            bail!("setuid failed: {}", std::io::Error::last_os_error());
        }
        if unsafe { libc::getuid() } != uid || unsafe { libc::getgid() } != uid {
            bail!("identity verification failed after drop");
        }
        Ok(())
    })();
    if let Err(err) = drop_result {
        write_line(pipe_fd, &json!({"__done": true, "error": err.to_string()}).to_string());
        return;
    }

    let (context_ok, context) = try_untrusted_context();
    for (id, label) in PROBES {
        let (mut exposed, mut detail) = match *id {
            "su_binary" => probe_su_binary(),
            "adb_dir" => probe_adb_dir(),
            "ksu_dir" => probe_ksu_dir(),
            "module_mounts" => probe_module_mounts(),
            "ksud_process" => visible_process(&["ksud"]),
            "zygisk_process" => visible_process(&["zygiskd", "muz-", "musu-zygisk"]),
            "selinux_status" => probe_selinux_status(),
            "debuggable" => probe_debuggable(),
            "magisk_files" => probe_magisk_files(),
            _ => (None, String::from("unknown probe")),
        };
        if !context_ok {
            // Still run the probes, but never present their result as what an
            // untrusted app would see when the domain switch did not happen.
            detail = format!("探针受 SELinux 限制，未验证；原始观测: {detail}");
            exposed = None;
        }
        write_line(
            pipe_fd,
            &json!({"id": id, "label": label, "exposed": exposed, "detail": detail}).to_string(),
        );
    }
    write_line(
        pipe_fd,
        &json!({
            "__done": true,
            "selinux_limited": !context_ok,
            "context": context,
        })
        .to_string(),
    );
}

#[cfg(unix)]
pub fn run() -> JsonOutcome {
    let uid = match select_unauthorized_uid() {
        Ok(uid) => uid,
        Err(err) => return error(format!("无法确定未授权探针 UID: {err:#}")),
    };

    let mut fds = [0i32; 2];
    // SAFETY: fds is a two-element array owned by this call.
    if unsafe { libc::pipe(fds.as_mut_ptr()) } != 0 {
        return error(format!("创建探针管道失败: {}", std::io::Error::last_os_error()));
    }
    // SAFETY: fork has no Rust-side borrows crossing into the child that are
    // used after the call; the child only touches raw fds and its own copy.
    let child = unsafe { libc::fork() };
    if child < 0 {
        unsafe {
            libc::close(fds[0]);
            libc::close(fds[1]);
        }
        return error(format!("fork 探针失败: {}", std::io::Error::last_os_error()));
    }
    if child == 0 {
        unsafe { libc::close(fds[0]) };
        child_main(fds[1], uid);
        unsafe { libc::_exit(0) };
    }

    unsafe { libc::close(fds[1]) };
    let mut reader = unsafe { File::from_raw_fd(fds[0]) };
    let mut output = String::new();
    let read_error = reader.read_to_string(&mut output).err();
    let mut status = 0i32;
    loop {
        // SAFETY: status is a valid i32 out-parameter.
        if unsafe { libc::waitpid(child, &raw mut status, 0) } >= 0 {
            break;
        }
        if std::io::Error::last_os_error().raw_os_error() != Some(libc::EINTR) {
            unsafe { libc::_exit(1) };
        }
    }

    let mut items = Vec::new();
    let mut done: Option<Value> = None;
    let mut drop_error: Option<String> = None;
    for line in output.lines() {
        let Ok(value) = serde_json::from_str::<Value>(line) else {
            continue;
        };
        if value.get("__done").and_then(Value::as_bool) == Some(true) {
            if let Some(message) = value.get("error").and_then(Value::as_str) {
                drop_error = Some(message.to_string());
            }
            done = Some(value);
        } else if value.get("id").is_some() {
            items.push(value);
        }
    }

    // SAFETY: status was filled by waitpid above.
    let status_error = if !libc::WIFEXITED(status) || libc::WEXITSTATUS(status) != 0 {
        Some(format!("探针子进程异常退出 (status {status})"))
    } else {
        drop_error
    };
    let failure = read_error
        .map(|read_error| format!("读取探针结果失败: {read_error}"))
        .or_else(|| {
            if done.is_none() {
                Some(String::from("探针子进程未返回完成标记"))
            } else {
                status_error
            }
        });
    if let Some(message) = failure {
        return JsonOutcome::fail(json!({
            "ok": false,
            "error": message,
            "partial_items": items,
        }));
    }

    let done = done.unwrap_or_default();
    let limited = done.get("selinux_limited").and_then(Value::as_bool).unwrap_or(false);
    let context = done
        .get("context")
        .and_then(Value::as_str)
        .unwrap_or("unknown")
        .to_string();
    let exposed_count = items
        .iter()
        .filter(|item| item.get("exposed").and_then(Value::as_bool) == Some(true))
        .count();
    JsonOutcome::ok(json!({
        "ok": true,
        "view": "unauthorized_uid",
        "uid": uid,
        "items": items,
        "exposed_count": exposed_count,
        "selinux_limited": limited,
        "context": context,
        "checked_at": Local::now().format("%Y-%m-%d %H:%M:%S").to_string(),
    }))
}

/// Non-Unix targets (notably Windows) have no fork/setuid/procfs semantics.
/// Keep the subcommand in the CLI and keep the JSON shape, but make the
/// unsupported platform an explicit, machine-readable error.
#[cfg(not(unix))]
pub fn run() -> JsonOutcome {
    JsonOutcome::fail(json!({
        "ok": false,
        "error": "envcheck is not supported on this platform",
    }))
}

#[cfg(all(test, unix))]
mod tests {
    use super::*;

    #[test]
    fn probe_runs_as_unauthorized_uid() {
        let outcome = run();
        assert_eq!(outcome.code, 0, "{:?}", outcome.json);
        assert_eq!(outcome.json["ok"], true);
        assert_eq!(outcome.json["view"], "unauthorized_uid");
        assert_eq!(outcome.json["uid"], 20000);
        let items = outcome.json["items"].as_array().unwrap();
        assert_eq!(items.len(), PROBES.len());
        assert!(
            items
                .iter()
                .all(|item| item.get("id").is_some() && item.get("detail").is_some())
        );
        println!("envcheck sample: {}", serde_json::to_string(&outcome.json).unwrap());
    }
}
