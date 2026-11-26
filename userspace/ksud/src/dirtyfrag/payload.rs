//! Independent DirtyFrag payload: embedded binaries, fail-closed authenticity
//! verification and private staging (docs/specs/dirtyfrag.md §4).
//!
//! This is a *separate* `RustEmbed` from `assets::Asset` on purpose: the main
//! `ensure_binaries()` skips `*.ko` and would flatten the APK into
//! `/data/adb/ksu/bin/`, breaking the payload boundary (F13 / §4.2).
//!
//! Android-only (cfg-gated in mod.rs). Syscall edges use cfg(unix)/cfg(not(unix))
//! pairs per §5.7 so the pattern stays explicit.

use std::collections::HashMap;
use std::path::{Path, PathBuf};

use rust_embed::RustEmbed;

use super::detect::{
    COMPLIANCE_FILES, is_elf_aarch64, is_zip, parse_sha256sums, sha256_hex,
};
use super::error::{DResult, IO_ERROR, PAYLOAD_MISSING, PAYLOAD_VERIFY_FAILED, STAGE_KSUD_FAILED, fail};
use crate::defs;

/// The orchestration payload tree lives at `bin/dirtyfrag`, never in the main
/// Asset embed. Only arm64 is shipped in 0.0.8 (§10).
#[derive(RustEmbed)]
#[folder = "bin/dirtyfrag"]
struct DirtyfragPayload;

const ARCH: &str = "arm64";
const APK_NAME: &str = "dirtyfrag-locked.apk";
const EXPECTED_VERSION: &str = "1.18-musu";
const DIR_MODE: u32 = 0o700;
const FILE_MODE: u32 = 0o600;

fn key(rel: &str) -> String {
    format!("{ARCH}/{rel}")
}

fn get(rel: &str) -> Option<Vec<u8>> {
    DirtyfragPayload::get(&key(rel)).map(|f| f.data.as_ref().to_vec())
}

#[derive(Debug, Clone)]
pub struct VerifiedPayload {
    #[allow(dead_code)]
    pub version: String,
    pub ko_sha256: HashMap<String, String>,
    #[allow(dead_code)]
    pub apk_sha256: String,
}

/// Whether this build embeds a DirtyFrag payload at all.
#[allow(dead_code)]
pub fn available() -> bool {
    get("VERSION")
        .is_some_and(|b| String::from_utf8_lossy(&b).trim() == EXPECTED_VERSION)
}

/// Fail-closed authenticity check (§4.4): version → compliance → sha256 →
/// ELF/machine → APK container. Any missing/invalid file is an error, never a
/// silent skip (runtime-defensive §二).
pub fn verify() -> DResult<VerifiedPayload> {
    let version = get("VERSION")
        .map(|b| String::from_utf8_lossy(&b).trim().to_string())
        .ok_or_else(|| fail(PAYLOAD_MISSING, "载荷缺少 VERSION"))?;
    if version != EXPECTED_VERSION {
        return Err(fail(
            PAYLOAD_MISSING,
            format!("VERSION 期望 {EXPECTED_VERSION}，实际 {version}"),
        ));
    }

    for name in COMPLIANCE_FILES {
        if get(name).is_none() {
            return Err(fail(
                PAYLOAD_MISSING,
                format!("载荷缺少合规文件 {name}（stage 必须拒绝）"),
            ));
        }
    }

    let sums_raw = get("SHA256SUMS")
        .ok_or_else(|| fail(PAYLOAD_MISSING, "载荷缺少 SHA256SUMS"))?;
    let entries = parse_sha256sums(&String::from_utf8_lossy(&sums_raw));
    if entries.is_empty() {
        return Err(fail(PAYLOAD_VERIFY_FAILED, "SHA256SUMS 无有效条目"));
    }
    for (expected, rel) in &entries {
        let bytes = get(rel).ok_or_else(|| {
            fail(PAYLOAD_VERIFY_FAILED, format!("SHA256SUMS 列出的文件缺失: {rel}"))
        })?;
        let actual = sha256_hex(&bytes);
        if actual != *expected {
            return Err(fail(
                PAYLOAD_VERIFY_FAILED,
                format!("sha256 不符: {rel}（期望 {expected}，实际 {actual}）"),
            ));
        }
    }

    let mut ko_sha256 = HashMap::new();
    for key_name in DirtyfragPayload::iter() {
        let path = key_name.as_ref();
        let Some(rel) = path.strip_prefix(&format!("{ARCH}/ko/")) else {
            continue;
        };
        if !std::path::Path::new(rel)
            .extension()
            .is_some_and(|ext| ext.eq_ignore_ascii_case("ko"))
        {
            continue;
        }
        let bytes = get(&format!("ko/{rel}"))
            .ok_or_else(|| fail(PAYLOAD_MISSING, format!("无法读取 ko: {rel}")))?;
        if !is_elf_aarch64(&bytes) {
            return Err(fail(
                PAYLOAD_VERIFY_FAILED,
                format!("ko 不是 AArch64 ELF(e_machine=183): {rel}"),
            ));
        }
        ko_sha256.insert(rel.to_string(), sha256_hex(&bytes));
    }
    if ko_sha256.is_empty() {
        return Err(fail(PAYLOAD_MISSING, "载荷中没有任何 ko"));
    }

    let apk = get(APK_NAME)
        .ok_or_else(|| fail(PAYLOAD_MISSING, format!("载荷缺少 {APK_NAME}")))?;
    if !is_zip(&apk) {
        return Err(fail(
            PAYLOAD_VERIFY_FAILED,
            "引擎 APK 不是有效 ZIP 容器",
        ));
    }
    // The certificate fingerprint is pinned by the integrator against the
    // fixed-commit APK (§4.1/§4.2); the signed artifact is rebuilt with the fork.
    let apk_sha256 = sha256_hex(&apk);

    Ok(VerifiedPayload {
        version,
        ko_sha256,
        apk_sha256,
    })
}

/// Locate MuSu Next's own libksud.so (F15 / §6.2). Never dlopen'd here — it is
/// copied into the private stage dir for the engine to pick up on its side.
fn find_libksud() -> DResult<PathBuf> {
    let mut candidates = vec![
        PathBuf::from(defs::LIBRARY_DIR).join("libksud.so"),
        PathBuf::from(defs::BINARY_DIR).join("libksud.so"),
    ];
    // The manager ships libksud.so in its nativeLibraryDir; resolve it through
    // the package manager (a process boundary), never by loading it ourselves.
    if let Ok(output) = std::process::Command::new("pm")
        .args(["path", defs::DEFAULT_PACKAGE_NAME])
        .output()
        && output.status.success()
    {
        let apk = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if let Some(base) = apk.strip_prefix("package:")
            && let Some(slash) = base.rfind('/') {
                candidates.push(
                    Path::new(&base[..slash])
                        .join("lib/arm64/libksud.so"),
                );
            }
    }
    candidates
        .into_iter()
        .find(|p| p.is_file())
        .ok_or_else(|| fail(STAGE_KSUD_FAILED, "找不到 MuSu Next 自带的 libksud.so"))
}

#[cfg(unix)]
fn make_private_dir(path: &Path) -> DResult<()> {
    use std::os::unix::fs::PermissionsExt;
    // create_dir is atomic and fails if the random name already exists (O_EXCL).
    std::fs::create_dir(path)?;
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(DIR_MODE))?;
    Ok(())
}

#[cfg(not(unix))]
fn make_private_dir(_path: &Path) -> DResult<()> {
    Err(fail(
        super::error::UNSUPPORTED_PLATFORM,
        "payload staging is Unix-only",
    ))
}

#[cfg(unix)]
fn set_file_mode(path: &Path) -> DResult<()> {
    use std::os::unix::fs::PermissionsExt;
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(FILE_MODE))?;
    Ok(())
}

#[cfg(not(unix))]
fn set_file_mode(_path: &Path) -> DResult<()> {
    Ok(())
}

/// Stage the verified payload + libksud.so into a private 0700 directory, then
/// atomically rename it into place. Returns the staged ko name/PATH info used
/// by `used` in the run result and the libksud sha256.
pub fn stage(ko_name: &str) -> DResult<(PathBuf, String, String)> {
    let verified = verify()?;
    let ko_sha = verified
        .ko_sha256
        .get(ko_name)
        .ok_or_else(|| fail(PAYLOAD_MISSING, format!("载荷缺少所选 ko: {ko_name}")))?
        .clone();

    std::fs::create_dir_all(defs::DIRTYFRAG_DIR)?;
    let stamp = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_or(0, |d| d.as_millis());
    let staging = Path::new(defs::DIRTYFRAG_STAGE_DIR).join(format!(".staging-{stamp}"));
    make_private_dir(&staging)?;

    let prefix = format!("{ARCH}/");
    let mut written = 0usize;
    for key_name in DirtyfragPayload::iter() {
        let Some(rel) = key_name.as_ref().strip_prefix(&prefix) else {
            continue;
        };
        let Some(asset) = DirtyfragPayload::get(&key_name) else {
            continue;
        };
        let dest = staging.join(rel);
        if let Some(parent) = dest.parent() {
            std::fs::create_dir_all(parent)?;
        }
        std::fs::write(&dest, asset.data.as_ref())?;
        set_file_mode(&dest)?;
        written += 1;
    }
    if written == 0 {
        let _ = std::fs::remove_dir_all(&staging);
        return Err(fail(PAYLOAD_MISSING, "载荷为空"));
    }

    let lib = find_libksud()?;
    let lib_bytes = std::fs::read(&lib)?;
    let lib_sha = sha256_hex(&lib_bytes);
    let lib_dest = staging.join("libksud.so");
    std::fs::write(&lib_dest, &lib_bytes)?;
    set_file_mode(&lib_dest)?;

    let target = Path::new(defs::DIRTYFRAG_STAGE_DIR);
    if target.exists() {
        std::fs::remove_dir_all(target)?;
    }
    std::fs::rename(&staging, target)?;
    log::info!(
        "dirtyfrag: staged {written} payload files + libksud.so to {}",
        target.display()
    );
    Ok((target.to_path_buf(), ko_sha, lib_sha))
}

/// Remove a path only after confirming it is not a symlink (runtime-defensive §6).
#[allow(dead_code)]
pub fn safe_remove(path: &Path) -> DResult<()> {
    match std::fs::symlink_metadata(path) {
        Ok(meta) if meta.file_type().is_symlink() => {
            std::fs::remove_file(path)?;
        }
        Ok(meta) if meta.is_dir() => {
            std::fs::remove_dir_all(path)?;
        }
        Ok(_) => std::fs::remove_file(path)?,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => {}
        Err(e) => return Err(fail(IO_ERROR, e.to_string())),
    }
    Ok(())
}
