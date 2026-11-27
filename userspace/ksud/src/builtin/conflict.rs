//! Mutual exclusion probes: "is another Zygisk implementation / metamodule present?"
//!
//! Criteria are the A1–A7 / B1–B5 rows of `docs/specs/builtin-components.md` §6.1.
//! A6/A7 and B4 are deliberately not implemented (§15.1 V-9): they are low confidence
//! and would block users on a guess.

use crate::defs;
use log::warn;
use regex_lite::Regex;
use serde_json::{Value, json};
use std::collections::HashMap;
use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};

use super::ComponentId;
use super::runtime::{KNOWN_ZYGISK_PROCESSES, is_our_process};

/// The only contract a Zygisk module has: `zygisk/<abi>.so` inside its own directory.
/// Every Zygisk *consumer* (LSPosed etc.) carries this too — it does NOT make a
/// module a Zygisk implementation, so it must never be used alone as a conflict.
pub const ZYGISK_ABI_FILES: [&str; 4] = [
    "arm64-v8a.so",
    "x86_64.so",
    "armeabi-v7a.so",
    "x86.so",
];

/// Module ids of Zygisk implementations that own the Zygisk slot.
const ZYGISK_PROVIDER_IDS: [&str; 2] = ["zygisksu", "rezygisk"];

/// Daemon binaries shipped only by a provider's monitor/ptracer. A consumer
/// module only carries `zygisk/<abi>.so` and relies on a provider to load it.
const ZYGISK_PROVIDER_BINS: [&str; 5] = [
    "zygiskd",
    "zygiskd32",
    "zygiskd64",
    "zygisk-ptrace32",
    "zygisk-ptrace64",
];

#[derive(Debug, Clone)]
pub struct Conflict {
    pub kind: &'static str,
    pub id: String,
    pub name: String,
    pub version: String,
    pub evidence: String,
}

impl Conflict {
    pub fn to_json(&self) -> Value {
        json!({
            "kind": self.kind,
            "id": self.id,
            "name": self.name,
            "version": self.version,
            "evidence": self.evidence,
        })
    }
}

#[derive(Debug, Clone)]
pub struct Warning {
    pub code: &'static str,
    pub message: String,
}

impl Warning {
    fn new(code: &'static str, message: impl Into<String>) -> Self {
        Self {
            code,
            message: message.into(),
        }
    }

    pub fn to_json(&self) -> Value {
        json!({ "code": self.code, "message": self.message })
    }
}

#[derive(Debug, Clone)]
pub struct Detection {
    pub conflict: Option<Conflict>,
    pub warnings: Vec<Warning>,
}

impl Detection {
    pub const fn new() -> Self {
        Self {
            conflict: None,
            warnings: Vec::new(),
        }
    }

    pub fn warnings_json(&self) -> Vec<Value> {
        self.warnings.iter().map(Warning::to_json).collect()
    }
}

fn module_dirs() -> Vec<PathBuf> {
    let Ok(entries) = fs::read_dir(defs::MODULE_DIR) else {
        return Vec::new();
    };
    let mut dirs: Vec<PathBuf> = entries
        .flatten()
        .map(|entry| entry.path())
        .filter(|path| path.is_dir())
        .filter(|path| !path.join(defs::REMOVE_FILE_NAME).exists())
        .collect();
    dirs.sort();
    dirs
}

fn module_id(path: &Path) -> String {
    path.file_name()
        .map(|name| name.to_string_lossy().to_string())
        .unwrap_or_default()
}

fn prop(path: &Path) -> HashMap<String, String> {
    crate::module::read_module_prop(path).unwrap_or_default()
}

/// A module is another Zygisk *implementation* (provider) only when it either
/// uses a known provider id or ships a daemon binary. Merely carrying
/// `zygisk/<abi>.so` makes it a consumer (LSPosed, etc.), which MuZygisk is
/// supposed to host, not to conflict with.
pub fn zygisk_provider_evidence(path: &Path) -> Option<String> {
    let id = module_id(path);
    if ZYGISK_PROVIDER_IDS.contains(&id.as_str()) {
        return Some(id);
    }
    // Provider daemons may live at the module root or under bin/.
    for sub in ["", "bin"] {
        for bin in ZYGISK_PROVIDER_BINS {
            let candidate = path.join(sub).join(bin);
            if candidate.exists() {
                return Some(candidate.to_string_lossy().to_string());
            }
        }
    }
    None
}

/// Ids of installed modules that are other Zygisk implementations (used for grey-out).
pub fn zygisk_provider_ids() -> Vec<String> {
    module_dirs()
        .into_iter()
        .filter(|path| zygisk_provider_evidence(path).is_some())
        .map(|path| module_id(&path))
        .filter(|id| !id.is_empty())
        .collect()
}

/// Ids of installed modules carrying a Zygisk ABI library, including consumers.
/// Used only for the "modules_total" metric, not for conflict / grey-out.
pub fn zygisk_module_ids() -> Vec<String> {
    module_dirs()
        .into_iter()
        .filter(|path| {
            ZYGISK_ABI_FILES
                .iter()
                .any(|abi| path.join("zygisk").join(abi).exists())
        })
        .map(|path| module_id(&path))
        .filter(|id| !id.is_empty())
        .collect()
}

/// Ids of installed modules that declare `metamodule=1`.
pub fn metamodule_ids() -> Vec<String> {
    module_dirs()
        .into_iter()
        .filter(|path| crate::metamodule::is_metamodule(&prop(path)))
        .map(|path| module_id(&path))
        .filter(|id| !id.is_empty())
        .collect()
}

fn zygisk_name_hint(props: &HashMap<String, String>) -> bool {
    let Ok(name_re) = Regex::new("(?i)zygisk") else {
        return false;
    };
    ["name", "description"]
        .iter()
        .filter_map(|key| props.get(*key))
        .any(|value| name_re.is_match(value))
}

/// A1/A2/A3: another Zygisk implementation is present and would fight ours.
pub fn zygisk_conflict(detection: &mut Detection) -> Option<Conflict> {
    // A1: only another *implementation* blocks us. A module merely carrying
    // zygisk/<abi>.so is a consumer that MuZygisk hosts, not a rival provider.
    let mut weak: Option<Conflict> = None;
    for dir in module_dirs() {
        let Some(evidence) = zygisk_provider_evidence(&dir) else {
            continue;
        };
        let props = prop(&dir);
        let conflict = Conflict {
            kind: "zygisk",
            id: module_id(&dir),
            name: props
                .get("name")
                .cloned()
                .unwrap_or_else(|| module_id(&dir)),
            version: props.get("version").cloned().unwrap_or_default(),
            evidence,
        };
        // A known provider id / daemon binary is already a hard signal; a name
        // hint on top makes the reason more readable but is not required.
        if zygisk_name_hint(&props) {
            return Some(conflict);
        }
        weak.get_or_insert(conflict);
    }

    // A3: something is already running, even if its module directory is gone.
    for pid in process_ids() {
        if is_our_process(&pid) {
            continue;
        }
        let exe = fs::read_link(format!("/proc/{pid}/exe"))
            .ok()
            .map(|p| p.to_string_lossy().to_string());
        let comm = fs::read_to_string(format!("/proc/{pid}/comm"))
            .ok()
            .map(|raw| raw.trim().to_string());
        let name = exe
            .as_ref()
            .and_then(|path| Path::new(path).file_name())
            .map(|name| name.to_string_lossy().to_string())
            .or_else(|| comm.clone());
        let Some(name) = name else { continue };
        if KNOWN_ZYGISK_PROCESSES.contains(&name.as_str()) {
            return Some(Conflict {
                kind: "zygisk",
                id: String::from("running"),
                name: comm.unwrap_or(name),
                version: String::new(),
                evidence: format!("/proc/{pid}/comm"),
            });
        }
    }

    // A4: leftovers only warn, they must not block.
    for leftover in ["/data/adb/rezygisk", "/data/adb/zygisksu"] {
        if Path::new(leftover).exists() {
            detection.warnings.push(Warning::new(
                "zygisk_leftover",
                format!("发现残留目录 {leftover}，可直接删除"),
            ));
        }
    }

    weak
}

/// B1/B2/B3: another metamodule already owns `/data/adb/metamodule`.
pub fn metamodule_conflict(detection: &mut Detection) -> Option<Conflict> {
    let link = Path::new(defs::METAMODULE_DIR.trim_end_matches('/'));
    if link.is_symlink()
        && let Ok(target) = fs::read_link(link)
    {
        let resolved = if target.is_absolute() {
            Some(target.clone())
        } else {
            link.parent().map(|parent| parent.join(&target))
        };
        let builtin = super::payload::component_dir(ComponentId::Metamodule);
        if resolved.as_deref() != Some(builtin.as_path()) {
            let props = resolved
                .as_deref()
                .map(prop)
                .unwrap_or_default();
            return Some(Conflict {
                kind: "metamodule",
                id: resolved
                    .as_deref()
                    .map_or_else(|| String::from("unknown"), module_id),
                name: props
                    .get("name")
                    .cloned()
                    .unwrap_or_else(|| String::from("其他元模块")),
                version: props.get("version").cloned().unwrap_or_default(),
                evidence: format!("{} -> {}", link.display(), target.display()),
            });
        }
    }

    if let Some(id) = metamodule_ids().into_iter().next() {
        let dir = Path::new(defs::MODULE_DIR).join(&id);
        let props = prop(&dir);
        // B3: a mount script makes the claim believable, it is not required to block.
        let extra = if dir.join(defs::METAMODULE_MOUNT_SCRIPT).exists() {
            " + metamount.sh"
        } else {
            ""
        };
        return Some(Conflict {
            kind: "metamodule",
            id: id.clone(),
            name: props.get("name").cloned().unwrap_or_else(|| id.clone()),
            version: props.get("version").cloned().unwrap_or_default(),
            evidence: format!(
                "{}/{id}/module.prop: metamodule=1{extra}",
                defs::MODULE_DIR
            ),
        });
    }

    // B5: runtime directories of other mount providers.
    for leftover in [
        defs::HYBRID_MOUNT_DIR,
        "/data/adb/mountify/",
        "/data/adb/modules/hybrid_mount/",
    ] {
        if Path::new(leftover).exists() {
            detection.warnings.push(Warning::new(
                "metamodule_leftover",
                format!("发现 {leftover}，启用 MuMount 后会复用或忽略它"),
            ));
        }
    }

    None
}

/// Full detection run for one component id (order matters: A5/Magisk is not a conflict).
pub fn detect(id: ComponentId) -> Detection {
    let mut detection = Detection::new();
    detection.conflict = match id {
        ComponentId::Zygisk => zygisk_conflict(&mut detection),
        ComponentId::Metamodule => metamodule_conflict(&mut detection),
    };
    detection
}

fn process_ids() -> Vec<String> {
    let Ok(entries) = fs::read_dir("/proc") else {
        return Vec::new();
    };
    entries
        .flatten()
        .filter_map(|entry| {
            let name = entry.file_name().to_string_lossy().to_string();
            (!name.is_empty() && name.bytes().all(|b| b.is_ascii_digit())).then_some(name)
        })
        .collect()
}

/// Install-time check: only a rival Zygisk *implementation* should be blocked.
/// A consumer module ships `zygisk/<abi>.so` but relies on our provider and is
/// allowed; a provider ships its own daemon binary (or a known provider id).
pub fn zip_provides_zygisk(zip: &Path) -> bool {
    let Ok(file) = fs::File::open(zip) else {
        warn!("cannot open {} to inspect zygisk payload", zip.display());
        return false;
    };
    let Ok(mut archive) = zip::ZipArchive::new(file) else {
        return false;
    };
    for index in 0..archive.len() {
        let Ok(mut entry) = archive.by_index(index) else {
            continue;
        };
        let name = entry.name().trim_start_matches("./").replace('\\', "/");
        let file_name = name.rsplit('/').next().unwrap_or("");
        // Known provider module ids are declared in module.prop; a fast signal is
        // the provider's daemon binary, which no consumer module ships.
        if ZYGISK_PROVIDER_BINS.contains(&file_name) {
            return true;
        }
        if file_name == "module.prop" {
            let mut raw = Vec::new();
            if entry.read_to_end(&mut raw).is_ok() {
                let text = String::from_utf8_lossy(&raw);
                let is_provider = text.lines().any(|line| {
                    let mut parts = line.splitn(2, '=');
                    parts.next().is_some_and(|k| k.trim() == "id")
                        && parts
                            .next()
                            .is_some_and(|v| ZYGISK_PROVIDER_IDS.contains(&v.trim()))
                });
                if is_provider {
                    return true;
                }
            }
        }
    }
    false
}
