//! Embedded payloads for the built-in components and the idempotent extractor.
//!
//! The payload lives in its own `rust_embed` structs on purpose: reusing `assets::Asset`
//! would let `ensure_binaries()` flatten `bin/builtin/**` into `/data/adb/ksu/bin/`
//! (`docs/specs/builtin-components.md` §2.3).

use crate::defs;
use log::{info, warn};
use rust_embed::RustEmbed;
use std::{
    fs,
    os::unix::fs::PermissionsExt,
    path::{Path, PathBuf},
};

use super::ComponentId;
use super::error::{BResult, EXTRACT_FAILED, PAYLOAD_MISSING, UNSUPPORTED_ARCH, fail};

#[cfg(target_arch = "aarch64")]
#[derive(RustEmbed)]
#[folder = "bin/builtin/aarch64"]
struct BuiltinPayload;

#[cfg(target_arch = "x86_64")]
#[derive(RustEmbed)]
#[folder = "bin/builtin/x86_64"]
struct BuiltinPayload;

#[cfg(target_arch = "riscv64")]
#[derive(RustEmbed)]
#[folder = "bin/builtin/riscv64"]
struct BuiltinPayload;

// Unknown architectures embed the placeholder tree only, so `available()` is false.
#[cfg(not(any(
    target_arch = "aarch64",
    target_arch = "x86_64",
    target_arch = "riscv64"
)))]
#[derive(RustEmbed)]
#[folder = "bin/builtin"]
struct BuiltinPayload;

const STAMP_FILE: &str = ".payload_version";
const FILE_MODE: u32 = 0o755;
const DIR_MODE: u32 = 0o755;

pub fn component_dir(id: ComponentId) -> PathBuf {
    Path::new(defs::BUILTIN_DIR).join(id.dir_name())
}

/// False when this build has no payload for the component (riscv64, §10.2).
pub fn available(id: ComponentId) -> bool {
    BuiltinPayload::get(&format!("{}/VERSION", id.dir_name())).is_some()
}

fn key_files(id: ComponentId) -> &'static [&'static str] {
    match id {
        ComponentId::Zygisk => &[
            "bin/muz-zygiskd64",
            "bin/muz-zygisk-ptrace64",
            "lib64/libzygisk.so",
            "sepolicy.rule",
        ],
        ComponentId::Metamodule => &["hybrid-mount", "metamount.sh", "module.prop"],
    }
}

/// Version stamp written on extraction; compared against `defs::VERSION_CODE`.
pub fn version_stamp(id: ComponentId) -> String {
    fs::read_to_string(component_dir(id).join(STAMP_FILE))
        .map_or_else(|_| String::from("0"), |raw| raw.trim().to_string())
}

fn stamp_matches(id: ComponentId) -> bool {
    if version_stamp(id) != defs::VERSION_CODE {
        return false;
    }
    let dir = component_dir(id);
    key_files(id).iter().all(|rel| dir.join(rel).exists())
}

fn mkdir_mode(path: &Path, mode: u32) -> BResult<()> {
    fs::create_dir_all(path)?;
    fs::set_permissions(path, fs::Permissions::from_mode(mode))?;
    Ok(())
}

fn reset_dir(path: &Path) -> BResult<()> {
    if path.exists() {
        fs::remove_dir_all(path)?;
    }
    mkdir_mode(path, DIR_MODE)
}

/// Idempotent extraction (§2.3): staging dir + atomic directory rename, so a payload
/// that is currently being executed is never overwritten in place.
/// Returns `true` when the payload was (re)extracted.
pub fn ensure_payload(id: ComponentId) -> BResult<bool> {
    if !available(id) {
        return Err(fail(
            UNSUPPORTED_ARCH,
            format!("当前架构没有 {} 载荷", id.display_name()),
        ));
    }
    if stamp_matches(id) {
        info!(
            "builtin {} payload is current (version {})",
            id.as_str(),
            defs::VERSION_CODE
        );
        return Ok(false);
    }

    let dir = component_dir(id);
    let staging = Path::new(defs::BUILTIN_DIR).join(format!(".staging-{}", id.dir_name()));
    let old_dir = Path::new(defs::BUILTIN_DIR).join(format!(".old-{}", id.dir_name()));

    let _ = fs::remove_dir_all(&old_dir);
    reset_dir(&staging)?;

    let prefix = format!("{}/", id.dir_name());
    let mut written = 0usize;
    for key in BuiltinPayload::iter() {
        let Some(rel) = key.strip_prefix(&prefix) else {
            continue;
        };
        let Some(asset) = BuiltinPayload::get(&key) else {
            continue;
        };
        let dest = staging.join(rel);
        if let Some(parent) = dest.parent() {
            mkdir_mode(parent, DIR_MODE)?;
        }
        fs::write(&dest, asset.data.as_ref()).map_err(|err| {
            fail(
                EXTRACT_FAILED,
                format!("写入 {} 失败: {err}", dest.display()),
            )
        })?;
        fs::set_permissions(&dest, fs::Permissions::from_mode(FILE_MODE))?;
        written += 1;
    }
    if written == 0 {
        return Err(fail(
            PAYLOAD_MISSING,
            format!("{} 载荷为空", id.display_name()),
        ));
    }
    fs::write(staging.join(STAMP_FILE), defs::VERSION_CODE)?;

    let had_old = dir.exists();
    if had_old {
        fs::rename(&dir, &old_dir).map_err(|err| {
            fail(
                EXTRACT_FAILED,
                format!("移走旧载荷 {} 失败: {err}", dir.display()),
            )
        })?;
    }
    if let Err(err) = fs::rename(&staging, &dir) {
        // Keep the previous payload usable rather than leaving a half-written tree.
        if had_old {
            let _ = fs::rename(&old_dir, &dir);
        }
        let _ = fs::remove_dir_all(&staging);
        return Err(fail(
            EXTRACT_FAILED,
            format!("启用新载荷 {} 失败: {err}", dir.display()),
        ));
    }
    if had_old {
        // Unlinking is safe while the old binaries are running: the inodes stay alive.
        if let Err(err) = fs::remove_dir_all(&old_dir) {
            warn!("failed to remove old payload {}: {err}", old_dir.display());
        }
    }

    let missing: Vec<&str> = key_files(id)
        .iter()
        .copied()
        .filter(|rel| !dir.join(rel).exists())
        .collect();
    if !missing.is_empty() {
        return Err(fail(
            PAYLOAD_MISSING,
            format!("载荷缺少关键文件: {}", missing.join(", ")),
        ));
    }

    info!(
        "extracted builtin {} payload to {} ({} files)",
        id.as_str(),
        dir.display(),
        written
    );
    Ok(true)
}

#[derive(Debug, Default, Clone)]
pub struct PayloadSource {
    pub version: String,
    pub upstream: String,
    pub upstream_url: String,
    pub upstream_commit: String,
    pub license: String,
}

/// License/upstream metadata for the UI detail block (`SOURCE.txt` in the payload).
pub fn source_meta(id: ComponentId) -> PayloadSource {
    let mut meta = PayloadSource {
        version: read_trimmed(component_dir(id).join("VERSION"))
            .unwrap_or_else(|| String::from("unknown")),
        upstream: String::from(match id {
            ComponentId::Zygisk => "ReZygisk",
            ComponentId::Metamodule => "Hybrid Mount",
        }),
        upstream_url: String::from(match id {
            ComponentId::Zygisk => "https://github.com/PerformanC/ReZygisk",
            ComponentId::Metamodule => "https://github.com/Hybrid-Mount/meta-hybrid_mount",
        }),
        upstream_commit: String::new(),
        license: String::from(match id {
            ComponentId::Zygisk => "AGPL-3.0-or-later",
            ComponentId::Metamodule => "GPL-3.0-only",
        }),
    };

    let Ok(raw) = fs::read_to_string(component_dir(id).join("SOURCE.txt")) else {
        return meta;
    };
    for line in raw.lines() {
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        let value = value.trim().to_string();
        match key.trim() {
            "upstream" if !value.is_empty() => meta.upstream = value,
            "upstream_url" if !value.is_empty() => meta.upstream_url = value,
            "upstream_commit" if !value.is_empty() => meta.upstream_commit = value,
            "license" if !value.is_empty() => meta.license = value,
            _ => {}
        }
    }
    meta
}

fn read_trimmed(path: PathBuf) -> Option<String> {
    fs::read_to_string(path)
        .ok()
        .map(|raw| raw.trim().to_string())
        .filter(|raw| !raw.is_empty())
}

/// Bytes occupied by the payload tree, for the detail block.
pub fn disk_bytes(id: ComponentId) -> u64 {
    fn walk(dir: &Path, total: &mut u64) {
        let Ok(entries) = fs::read_dir(dir) else {
            return;
        };
        for entry in entries.flatten() {
            let Ok(meta) = entry.metadata() else {
                continue;
            };
            if meta.is_dir() {
                walk(&entry.path(), total);
            } else {
                *total = total.saturating_add(meta.len());
            }
        }
    }
    let mut total = 0u64;
    walk(&component_dir(id), &mut total);
    total
}

/// First enable seeds the user-visible Hybrid Mount config; later upgrades must not
/// overwrite what the user edited (§9.1, D-18).
pub fn seed_hybrid_config() -> BResult<bool> {
    let target = Path::new(defs::HYBRID_MOUNT_CONFIG_PATH);
    if target.exists() {
        return Ok(false);
    }
    mkdir_mode(Path::new(defs::HYBRID_MOUNT_DIR), DIR_MODE)?;
    let template = component_dir(ComponentId::Metamodule).join("config.toml");
    if !template.exists() {
        warn!(
            "hybrid mount config template missing at {}, skip seeding",
            template.display()
        );
        return Ok(false);
    }
    fs::copy(&template, target)?;
    info!("seeded {} from payload", target.display());
    Ok(true)
}
