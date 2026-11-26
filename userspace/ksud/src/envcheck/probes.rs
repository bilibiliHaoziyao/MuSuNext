//! Read-only probes executed in the dropped-privilege child.

use std::{ffi::CString, fs};

#[cfg(target_os = "android")]
unsafe extern "C" {
    fn __system_property_get(name: *const libc::c_char, value: *mut libc::c_char) -> libc::c_int;
}

fn errno_text(err: &std::io::Error) -> String {
    match err.raw_os_error() {
        Some(libc::ENOENT) => String::from("ENOENT"),
        Some(libc::EACCES) => String::from("EACCES"),
        Some(libc::ENOTDIR) => String::from("ENOTDIR"),
        Some(libc::EPERM) => String::from("EPERM"),
        Some(code) => format!("errno {code}"),
        None => err.to_string(),
    }
}

pub(super) fn check_x(path: &str) -> (bool, String) {
    let Ok(c_path) = CString::new(path) else {
        return (false, String::from("invalid path"));
    };
    // SAFETY: c_path is a valid NUL-terminated string for the duration of the call.
    if unsafe { libc::access(c_path.as_ptr(), libc::X_OK) } == 0 {
        (true, String::from("X_OK 可执行"))
    } else {
        let err = std::io::Error::last_os_error();
        (false, format!("access X_OK 返回 {}", errno_text(&err)))
    }
}

pub(super) fn probe_su_binary() -> (Option<bool>, String) {
    let paths = ["/system/bin/su", "/system/xbin/su", "/data/adb/ksu/bin/su"];
    let mut exposed = false;
    let mut detail = Vec::new();
    for path in paths {
        let (ok, reason) = check_x(path);
        exposed |= ok;
        detail.push(format!("{path}: {reason}"));
    }
    (Some(exposed), detail.join("; "))
}

pub(super) fn probe_adb_dir() -> (Option<bool>, String) {
    match fs::read_dir("/data/adb") {
        Ok(_) => (Some(true), String::from("目录可列出")),
        Err(err) => (Some(false), format!("read_dir 返回 {}", errno_text(&err))),
    }
}

pub(super) fn probe_ksu_dir() -> (Option<bool>, String) {
    match fs::metadata("/data/adb/ksu") {
        Ok(_) => (Some(true), String::from("路径可见")),
        Err(err) if err.raw_os_error() == Some(libc::EACCES) => (
            None,
            String::from("路径存在但访问被拒绝（权限差异可被探测，未确认内容）"),
        ),
        Err(err) => (Some(false), format!("metadata 返回 {}", errno_text(&err))),
    }
}

pub(super) fn probe_module_mounts() -> (Option<bool>, String) {
    let text = match fs::read_to_string("/proc/self/mounts") {
        Ok(text) => text,
        Err(err) => return (None, format!("读取 /proc/self/mounts 失败: {}", errno_text(&err))),
    };
    let hit = text
        .lines()
        .find(|line| line.contains("/data/adb/modules") || line.contains("/data/adb/musu"));
    hit.map_or_else(
        || (Some(false), String::from("mounts 中无模块路径")),
        |line| {
            (
                Some(true),
                format!("mounts 暴露: {}", line.chars().take(180).collect::<String>()),
            )
        },
    )
}

pub(super) fn visible_process(names: &[&str]) -> (Option<bool>, String) {
    let Ok(entries) = fs::read_dir("/proc") else {
        return (None, String::from("无法枚举 /proc"));
    };
    let self_pid = std::process::id().to_string();
    let mut found = Vec::new();
    for entry in entries.flatten() {
        let pid = entry.file_name().to_string_lossy().into_owned();
        if !pid.chars().all(|c| c.is_ascii_digit()) || pid == self_pid {
            continue;
        }
        let comm = fs::read_to_string(entry.path().join("comm")).unwrap_or_default();
        let cmdline = fs::read_to_string(entry.path().join("cmdline"))
            .unwrap_or_default()
            .replace('\0', " ");
        let haystack = format!("{} {}", comm.trim(), cmdline);
        if names.iter().any(|name| haystack.contains(name)) {
            found.push(format!("pid {pid}: {}", comm.trim()));
        }
    }
    if found.is_empty() {
        (Some(false), String::from("无匹配进程"))
    } else {
        (Some(true), found.join("; "))
    }
}

pub(super) fn probe_selinux_status() -> (Option<bool>, String) {
    match fs::read_to_string("/sys/fs/selinux/enforce") {
        Ok(value) => {
            let value = value.trim();
            (
                Some(value == "0"),
                format!("enforce={value}（0 表示宽容模式，可被检测方读取）"),
            )
        }
        Err(err) => (None, format!("读取 enforce 失败: {}", errno_text(&err))),
    }
}

#[cfg(target_os = "android")]
pub(super) fn read_property(key: &str) -> Option<String> {
    let name = CString::new(key).ok()?;
    let mut buffer = [0u8; 92];
    // SAFETY: name is NUL-terminated; buffer has room for PROP_VALUE_MAX (92).
    let length = unsafe { __system_property_get(name.as_ptr(), buffer.as_mut_ptr().cast()) };
    if length <= 0 {
        return None;
    }
    let end = buffer.iter().position(|b| *b == 0).unwrap_or(buffer.len());
    String::from_utf8(buffer[..end].to_vec()).ok()
}

#[cfg(not(target_os = "android"))]
pub(super) fn read_property(_key: &str) -> Option<String> {
    None
}

pub(super) fn probe_debuggable() -> (Option<bool>, String) {
    let debuggable = read_property("ro.debuggable");
    let secure = read_property("ro.secure");
    if debuggable.is_none() && secure.is_none() {
        return (None, String::from("属性读取不可用（未验证）"));
    }
    let d = debuggable.as_deref().unwrap_or("?");
    let s = secure.as_deref().unwrap_or("?");
    (
        Some(d == "1" || s == "0"),
        format!("ro.debuggable={d}, ro.secure={s}"),
    )
}

pub(super) fn probe_magisk_files() -> (Option<bool>, String) {
    let paths = ["/sbin/magisk", "/.magisk", "/data/adb/magisk", "/dev/.magisk"];
    let found: Vec<&str> = paths.iter().copied().filter(|p| fs::metadata(p).is_ok()).collect();
    if found.is_empty() {
        (Some(false), String::from("常见 Magisk 路径均不存在"))
    } else {
        (Some(true), format!("存在: {}", found.join(", ")))
    }
}

