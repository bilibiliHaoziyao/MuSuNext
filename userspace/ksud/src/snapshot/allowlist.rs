//! Kernel-persisted root grant allowlist (`/data/adb/ksu/.allowlist`).
//!
//! Binary layout mirrors `uapi/app_profile.h` v4 exactly. Profiles are stored
//! with both structured fields (auditable) and raw bytes (byte-exact restore).

use anyhow::{Context, Result, bail};
use serde_json::Value;
use std::{
    fs::{self, OpenOptions},
    io::Write,
    os::unix::fs::{PermissionsExt, chown},
    path::{Path, PathBuf},
};

pub const ALLOWLIST_PATH: &str = "/data/adb/ksu/.allowlist";

const ALLOWLIST_MAGIC: u32 = 0x7f4b_5355;
const ALLOWLIST_VERSION: u32 = 4;
const RECORD_SIZE_V4: usize = 784;
const RECORD_SIZE_PRE_V4: usize = 776;
const MAX_PROFILES: usize = u16::MAX as usize;

#[repr(C)]
#[derive(Clone, Copy, Default)]
struct Capabilities {
    effective: u64,
    permitted: u64,
    inheritable: u64,
}

#[repr(C)]
#[derive(Clone, Copy)]
struct RootProfile {
    uid: i32,
    gid: i32,
    groups_count: u32,
    groups: [i32; 32],
    capabilities: Capabilities,
    selinux_domain: [u8; 64],
    namespaces: i32,
    flags: u64,
}

impl Default for RootProfile {
    fn default() -> Self {
        Self {
            uid: 0,
            gid: 0,
            groups_count: 0,
            groups: [0; 32],
            capabilities: Capabilities::default(),
            selinux_domain: [0; 64],
            namespaces: 0,
            flags: 0,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
struct NonRootProfile {
    umount_modules: bool,
}

#[repr(C)]
#[derive(Clone, Copy)]
struct RpConfig {
    use_default: bool,
    template_name: [u8; 256],
    profile: RootProfile,
}

impl Default for RpConfig {
    fn default() -> Self {
        Self {
            use_default: false,
            template_name: [0; 256],
            profile: RootProfile::default(),
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
struct NrpConfig {
    use_default: bool,
    profile: NonRootProfile,
}

#[repr(C)]
#[derive(Clone, Copy)]
union ProfileConfig {
    rp: RpConfig,
    nrp: NrpConfig,
}

#[repr(C)]
#[derive(Clone, Copy)]
pub(super) struct AppProfile {
    version: u32,
    key: [u8; 256],
    curr_uid: i32,
    allow_su: bool,
    config: ProfileConfig,
}

// A layout change would corrupt the kernel's allowlist on restore. Fail to
// build instead of writing a malformed record.
const _: () = assert!(std::mem::size_of::<AppProfile>() == RECORD_SIZE_V4);
const _: () = assert!(std::mem::offset_of!(AppProfile, key) == 4);
const _: () = assert!(std::mem::offset_of!(AppProfile, curr_uid) == 260);
const _: () = assert!(std::mem::offset_of!(AppProfile, allow_su) == 264);
const _: () = assert!(std::mem::offset_of!(AppProfile, config) == 272);

pub(super) fn from_bytes(bytes: &[u8]) -> AppProfile {
    let mut raw = [0u8; RECORD_SIZE_V4];
    raw[..bytes.len()].copy_from_slice(bytes);
    // SAFETY: AppProfile is repr(C), 784 bytes, align 8; `raw` is a 784-byte
    // zero-initialised array whose alignment is at least 1. Copy into an
    // aligned value instead of transmuting the unaligned buffer.
    let mut profile = AppProfile {
        version: 0,
        key: [0; 256],
        curr_uid: 0,
        allow_su: false,
        config: ProfileConfig {
            rp: RpConfig::default(),
        },
    };
    let dst = std::ptr::from_mut(&mut profile).cast::<u8>();
    // SAFETY: dst points to a live, correctly sized and aligned AppProfile.
    unsafe { std::ptr::copy_nonoverlapping(raw.as_ptr(), dst, RECORD_SIZE_V4) };
    profile
}

pub(super) fn as_bytes(profile: &AppProfile) -> Vec<u8> {
    let src = std::ptr::from_ref(profile).cast::<u8>();
    // SAFETY: src points to a live, correctly sized and aligned AppProfile.
    unsafe { std::slice::from_raw_parts(src, RECORD_SIZE_V4).to_vec() }
}

/// Mirror kernel `migrate_profile()` so old allowlist records stored as v4
/// keep the flags/domain the kernel would have applied when loading them.
fn migrate_pre_v4(profile: &mut AppProfile, source_version: u32) {
    if source_version == 4 || !profile.allow_su {
        return;
    }
    // SAFETY: allow_su means the kernel uses the rp_config union side.
    let rp = unsafe { &mut profile.config.rp };
    if source_version <= 2 && c_string(&rp.profile.selinux_domain) == "u:r:su:s0" {
        put_string(&mut rp.profile.selinux_domain, "u:r:ksu:s0");
    }
    rp.profile.flags |= 1; // FLAG_KSU_NO_NEW_PRIVS
}

#[path = "allowlist_json.rs"]
mod json;
use json::{c_string, profile_from_json, profile_to_json, put_string};

/// Read all persisted app profiles. A missing file means "no grants yet".
pub fn read_profiles() -> Result<Vec<Value>> {
    let bytes = match fs::read(ALLOWLIST_PATH) {
        Ok(bytes) => bytes,
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => return Ok(Vec::new()),
        Err(err) => return Err(err).context("read kernel allowlist"),
    };
    if bytes.len() < 8 {
        bail!("allowlist is shorter than its header");
    }
    let magic = u32::from_le_bytes(bytes[0..4].try_into().context("allowlist magic")?);
    let version = u32::from_le_bytes(bytes[4..8].try_into().context("allowlist version")?);
    if magic != ALLOWLIST_MAGIC {
        bail!("allowlist magic mismatch: {magic:#x}");
    }
    if !(2..=ALLOWLIST_VERSION).contains(&version) {
        bail!("unsupported allowlist version: {version}");
    }
    let record_size = if version == ALLOWLIST_VERSION {
        RECORD_SIZE_V4
    } else {
        RECORD_SIZE_PRE_V4
    };
    let mut profiles = Vec::new();
    for chunk in bytes[8..].chunks_exact(record_size) {
        if version == ALLOWLIST_VERSION {
            let profile = from_bytes(chunk);
            profiles.push(profile_to_json(&profile));
        } else {
            // Pre-v4 has no `version` field and only 3 bytes of padding before
            // the union. Both offsets differ, so copy field by field.
            let mut raw = [0u8; RECORD_SIZE_V4];
            raw[0..4].copy_from_slice(&ALLOWLIST_VERSION.to_le_bytes());
            raw[4..260].copy_from_slice(&chunk[0..256]);
            raw[260..264].copy_from_slice(&chunk[256..260]);
            raw[264] = chunk[260];
            raw[272..784].copy_from_slice(&chunk[264..776]);
            let mut profile = from_bytes(&raw);
            migrate_pre_v4(&mut profile, version);
            profiles.push(profile_to_json(&profile));
        }
    }
    Ok(profiles)
}

/// Atomically replace the kernel's persisted allowlist. Takes effect on reboot.
pub fn write_profiles(profiles: &[Value]) -> Result<usize> {
    if profiles.len() > MAX_PROFILES {
        bail!("too many profiles: {}", profiles.len());
    }
    let mut body = Vec::with_capacity(8 + profiles.len() * RECORD_SIZE_V4);
    body.extend_from_slice(&ALLOWLIST_MAGIC.to_le_bytes());
    body.extend_from_slice(&ALLOWLIST_VERSION.to_le_bytes());
    for value in profiles {
        let profile = profile_from_json(value)?;
        if c_string(&profile.key).is_empty() {
            bail!("profile key is empty");
        }
        body.extend_from_slice(&as_bytes(&profile));
    }

    let path = Path::new(ALLOWLIST_PATH);
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let tmp = PathBuf::from(format!("{ALLOWLIST_PATH}.snapshot.tmp"));
    {
        let mut file = OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .open(&tmp)?;
        file.write_all(&body)?;
        file.sync_all()?;
    }
    fs::set_permissions(&tmp, fs::Permissions::from_mode(0o644))?;
    let _ = chown(&tmp, Some(0), Some(0));
    fs::rename(&tmp, path)?;
    Ok(profiles.len())
}

