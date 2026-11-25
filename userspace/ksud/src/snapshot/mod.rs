//! `ksud snapshot ...` — backup and restore of module states, root grants and
//! built-in component switches. One JSON object on stdout, nothing else.

pub mod allowlist;
pub mod data;

use crate::jsoncmd::{JsonOutcome, error};
use anyhow::{Context, Result, bail};
use chrono::Local;
use flate2::{Compression, read::GzDecoder, write::GzEncoder};
use serde_json::{Value, json};
use std::{
    collections::HashMap,
    fs::{self, File, OpenOptions},
    io::Read,
    os::unix::fs::PermissionsExt,
    path::{Component, Path, PathBuf},
};

const SCHEMA: u32 = 1;
const ARCHIVE_VERSION: &str = "0.0.7";

struct Manifest {
    created: String,
    stamp: String,
    unix: i64,
    modules: usize,
    profiles: usize,
    builtins: usize,
}

impl Manifest {
    fn to_json(&self) -> Value {
        json!({
            "schema": SCHEMA,
            "version": ARCHIVE_VERSION,
            "created": self.created,
            "created_unix": self.unix,
            "counts": {
                "modules": self.modules,
                "profiles": self.profiles,
                "builtins": self.builtins,
            },
        })
    }
}

struct SnapshotData {
    manifest: Manifest,
    modules: Vec<Value>,
    profiles: Vec<Value>,
    builtins: Vec<Value>,
}

fn clock() -> (String, String, i64) {
    let now = Local::now();
    (
        now.format("%Y-%m-%d %H:%M:%S").to_string(),
        now.format("%Y%m%d-%H%M%S").to_string(),
        now.timestamp(),
    )
}

fn append_part(builder: &mut tar::Builder<GzEncoder<File>>, name: &str, body: &[u8], mtime: u64) -> Result<()> {
    let mut header = tar::Header::new_gnu();
    header.set_size(body.len() as u64);
    header.set_mode(0o600);
    header.set_mtime(mtime);
    header.set_uid(0);
    header.set_gid(0);
    header.set_cksum();
    builder
        .append_data(&mut header, name, &mut &body[..])
        .with_context(|| format!("append archive member {name}"))?;
    Ok(())
}

/// Create a snapshot. `pre_restore` selects the `.pre-restore` suffix used for
/// the automatic safety copy taken immediately before a destructive restore.
fn build_snapshot(pre_restore: bool) -> Result<(PathBuf, Manifest, u64)> {
    let modules = data::read_modules()?;
    let profiles = data::read_profiles()?;
    let builtins = data::read_builtins();
    let (created, stamp, unix) = clock();
    let manifest = Manifest {
        created,
        stamp,
        unix,
        modules: modules.len(),
        profiles: profiles.len(),
        builtins: builtins.len(),
    };

    let dir = Path::new(data::SNAPSHOT_DIR);
    fs::create_dir_all(dir)?;
    fs::set_permissions(dir, fs::Permissions::from_mode(0o700))?;

    let suffix = if pre_restore { ".pre-restore" } else { "" };
    let path = dir.join(format!("snapshot-{}{suffix}.tar.gz", manifest.stamp));
    let file = OpenOptions::new().write(true).create_new(true).open(&path)?;
    let encoder = GzEncoder::new(file, Compression::default());
    let mut builder = tar::Builder::new(encoder);
    append_part(&mut builder, "manifest.json", &serde_json::to_vec(&manifest.to_json())?, unix as u64)?;
    append_part(&mut builder, "modules.json", &serde_json::to_vec(&modules)?, unix as u64)?;
    append_part(&mut builder, "profiles.json", &serde_json::to_vec(&profiles)?, unix as u64)?;
    append_part(&mut builder, "builtins.json", &serde_json::to_vec(&builtins)?, unix as u64)?;
    builder.finish()?;
    let file = builder.into_inner()?.finish()?;
    file.sync_all()?;
    fs::set_permissions(&path, fs::Permissions::from_mode(0o600))?;
    let bytes = fs::metadata(&path)?.len();
    Ok((path, manifest, bytes))
}

fn parse_snapshot(path: &Path) -> Result<SnapshotData> {
    let file = File::open(path)?;
    let mut archive = tar::Archive::new(GzDecoder::new(file));
    let mut parts: HashMap<String, Vec<u8>> = HashMap::new();
    for entry in archive.entries()? {
        let mut entry = entry?;
        if !entry.header().entry_type().is_file() {
            continue;
        }
        let name = entry.path()?.to_string_lossy().trim_start_matches("./").to_string();
        let mut body = Vec::new();
        entry.read_to_end(&mut body)?;
        parts.insert(name, body);
    }

    let take = |name: &str| -> Result<&Vec<u8>> {
        parts.get(name).with_context(|| format!("archive is missing {name}"))
    };
    let manifest: Value = serde_json::from_slice(take("manifest.json")?)?;
    let schema = manifest.get("schema").and_then(Value::as_u64).unwrap_or(0);
    if schema != u64::from(SCHEMA) {
        bail!("unsupported snapshot schema: {schema}");
    }
    let counts = manifest.get("counts");
    let created = manifest
        .get("created")
        .and_then(Value::as_str)
        .unwrap_or("")
        .to_string();
    let modules: Vec<Value> = serde_json::from_slice(take("modules.json")?)?;
    let profiles: Vec<Value> = serde_json::from_slice(take("profiles.json")?)?;
    let builtins: Vec<Value> = serde_json::from_slice(take("builtins.json")?)?;
    let count = |key: &str| -> usize {
        counts
            .and_then(|c| c.get(key))
            .and_then(Value::as_u64)
            .and_then(|n| usize::try_from(n).ok())
            .unwrap_or(0)
    };
    Ok(SnapshotData {
        manifest: Manifest {
            created,
            stamp: String::new(),
            unix: manifest
                .get("created_unix")
                .and_then(Value::as_i64)
                .unwrap_or(0),
            modules: count("modules"),
            profiles: count("profiles"),
            builtins: count("builtins"),
        },
        modules,
        profiles,
        builtins,
    })
}

/// Reject traversal and symlinks before handing a manager-supplied path to fs.
fn checked_path(raw: &str) -> Result<PathBuf> {
    let base = Path::new(data::SNAPSHOT_DIR).canonicalize()?;
    let candidate = Path::new(raw);
    let path = if candidate.is_absolute() {
        candidate.to_path_buf()
    } else {
        Path::new(data::SNAPSHOT_DIR).join(candidate)
    };
    if path.components().any(|c| matches!(c, Component::ParentDir)) {
        bail!("snapshot path must stay inside {}", data::SNAPSHOT_DIR);
    }
    let canonical = path.canonicalize()?;
    if !canonical.starts_with(&base) {
        bail!("snapshot path must stay inside {}", data::SNAPSHOT_DIR);
    }
    if !canonical.is_file() {
        bail!("snapshot path is not a regular file");
    }
    Ok(canonical)
}

pub fn cmd_create() -> JsonOutcome {
    match build_snapshot(false) {
        Ok((path, manifest, bytes)) => JsonOutcome::ok(json!({
            "ok": true,
            "path": path.to_string_lossy(),
            "modules": manifest.modules,
            "profiles": manifest.profiles,
            "builtins": manifest.builtins,
            "bytes": bytes,
        })),
        Err(err) => {
            log::warn!("snapshot create failed: {err:#}");
            error(format!("快照创建失败: {err:#}"))
        }
    }
}

pub fn cmd_list() -> JsonOutcome {
    let entries = match fs::read_dir(data::SNAPSHOT_DIR) {
        Ok(entries) => entries,
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => {
            return JsonOutcome::ok(json!({"ok": true, "snapshots": []}));
        }
        Err(err) => return error(format!("读取快照目录失败: {err}")),
    };
    let mut snapshots = Vec::new();
    for entry in entries.flatten() {
        let path = entry.path();
        if !path.is_file() || path.extension().and_then(|e| e.to_str()) != Some("gz") {
            continue;
        }
        let size = match entry.metadata() {
            Ok(meta) => meta.len(),
            Err(_) => continue,
        };
        match parse_snapshot(&path) {
            Ok(data) => snapshots.push(json!({
                "path": path.to_string_lossy(),
                "created": data.manifest.created,
                "modules": data.manifest.modules,
                "profiles": data.manifest.profiles,
                "size": size,
            })),
            Err(err) => log::warn!("skip unreadable snapshot {}: {err:#}", path.display()),
        }
    }
    snapshots.sort_by(|a, b| b["path"].as_str().cmp(&a["path"].as_str()));
    JsonOutcome::ok(json!({"ok": true, "snapshots": snapshots}))
}

pub fn cmd_restore(raw_path: &str) -> JsonOutcome {
    let outcome = (|| -> Result<Value> {
        let path = checked_path(raw_path)?;
        let snapshot = parse_snapshot(&path)?;

        // Destructive operation: the safety copy must succeed before anything
        // is mutated. If it fails, abort with no changes applied.
        let (pre_path, _, _) = build_snapshot(true).context("create pre-restore snapshot")?;
        log::info!("pre-restore snapshot saved at {}", pre_path.display());

        let (modules, missing) = data::apply_modules(&snapshot.modules)?;
        let profiles = data::write_profiles(&snapshot.profiles)?;
        let builtins = data::apply_builtins(&snapshot.builtins)?;
        if missing > 0 {
            log::warn!("snapshot restore skipped {missing} modules that are not installed");
        }
        Ok(json!({
            "ok": true,
            "restored": {
                "modules": modules,
                "profiles": profiles,
                "builtins": builtins,
            },
            "need_reboot": true,
        }))
    })();
    match outcome {
        Ok(value) => JsonOutcome::ok(value),
        Err(err) => {
            log::warn!("snapshot restore failed: {err:#}");
            error(format!("快照恢复失败: {err:#}"))
        }
    }
}

pub fn cmd_delete(raw_path: &str) -> JsonOutcome {
    match checked_path(raw_path) {
        Ok(path) => match fs::remove_file(&path) {
            Ok(()) => JsonOutcome::ok(json!({"ok": true})),
            Err(err) => error(format!("删除快照失败: {err}")),
        },
        Err(err) => error(format!("删除快照失败: {err:#}")),
    }
}

#[cfg(test)]
mod tests;
