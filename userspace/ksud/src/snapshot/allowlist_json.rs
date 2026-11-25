//! JSON representation of a kernel app profile. Structured fields make the
//! snapshot auditable; `raw_hex` guarantees byte-exact restore.

use anyhow::{Context, Result, bail};
use serde_json::{Value, json};

use super::{
    ALLOWLIST_VERSION, AppProfile, NrpConfig, ProfileConfig, RECORD_SIZE_V4, RpConfig, as_bytes,
    from_bytes,
};

pub(super) fn c_string(bytes: &[u8]) -> String {
    let end = bytes.iter().position(|b| *b == 0).unwrap_or(bytes.len());
    String::from_utf8_lossy(&bytes[..end]).into_owned()
}

pub(super) fn put_string(dst: &mut [u8], value: &str) {
    let bytes = value.as_bytes();
    let limit = dst.len().saturating_sub(1).min(bytes.len());
    dst[..limit].copy_from_slice(&bytes[..limit]);
    dst[limit] = 0;
}

pub(super) fn profile_to_json(profile: &AppProfile) -> Value {
    // SAFETY: both union variants are plain-old-data and reading the rp side
    // is valid for any byte pattern; `allow_su` only selects which side the
    // kernel uses.
    let rp = unsafe { profile.config.rp };
    let nrp = unsafe { profile.config.nrp };
    json!({
        "version": profile.version,
        "key": c_string(&profile.key),
        "curr_uid": profile.curr_uid,
        "allow_su": profile.allow_su,
        "root": {
            "use_default": rp.use_default,
            "template": c_string(&rp.template_name),
            "uid": rp.profile.uid,
            "gid": rp.profile.gid,
            "groups": rp.profile.groups[..(rp.profile.groups_count as usize).min(32)],
            "capabilities": {
                "effective": rp.profile.capabilities.effective,
                "permitted": rp.profile.capabilities.permitted,
                "inheritable": rp.profile.capabilities.inheritable,
            },
            "context": c_string(&rp.profile.selinux_domain),
            "namespaces": rp.profile.namespaces,
            "flags": rp.profile.flags,
        },
        "non_root": {
            "use_default": nrp.use_default,
            "umount_modules": nrp.profile.umount_modules,
        },
        // Byte-exact copy wins on restore; structured fields above are for
        // humans auditing the snapshot.
        "raw_hex": hex_encode(&as_bytes(profile)),
    })
}

fn hex_encode(bytes: &[u8]) -> String {
    const HEX: &[u8; 16] = b"0123456789abcdef";
    let mut out = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        out.push(char::from(HEX[(byte >> 4) as usize]));
        out.push(char::from(HEX[(byte & 0x0f) as usize]));
    }
    out
}

fn hex_decode(text: &str) -> Result<Vec<u8>> {
    if !text.len().is_multiple_of(2) {
        bail!("raw_hex has odd length");
    }
    let mut out = Vec::with_capacity(text.len() / 2);
    let bytes = text.as_bytes();
    for pair in bytes.chunks_exact(2) {
        let hi = (pair[0] as char).to_digit(16).context("invalid raw_hex")?;
        let lo = (pair[1] as char).to_digit(16).context("invalid raw_hex")?;
        out.push(((hi << 4) | lo) as u8);
    }
    Ok(out)
}

pub(super) fn profile_from_json(value: &Value) -> Result<AppProfile> {
    if let Some(hex) = value.get("raw_hex").and_then(Value::as_str) {
        let raw = hex_decode(hex)?;
        if raw.len() != RECORD_SIZE_V4 {
            bail!("raw_hex must be {RECORD_SIZE_V4} bytes, got {}", raw.len());
        }
        return Ok(from_bytes(&raw));
    }

    let mut profile = AppProfile {
        version: ALLOWLIST_VERSION,
        key: [0; 256],
        curr_uid: value.get("curr_uid").and_then(Value::as_i64).unwrap_or(9999) as i32,
        allow_su: value.get("allow_su").and_then(Value::as_bool).unwrap_or(false),
        config: ProfileConfig {
            rp: RpConfig::default(),
        },
    };
    put_string(
        &mut profile.key,
        value.get("key").and_then(Value::as_str).unwrap_or(""),
    );
    let root = value.get("root");
    let mut rp = RpConfig::default();
    if let Some(root) = root {
        rp.use_default = root.get("use_default").and_then(Value::as_bool).unwrap_or(false);
        put_string(
            &mut rp.template_name,
            root.get("template").and_then(Value::as_str).unwrap_or(""),
        );
        rp.profile.uid = root.get("uid").and_then(Value::as_i64).unwrap_or(0) as i32;
        rp.profile.gid = root.get("gid").and_then(Value::as_i64).unwrap_or(0) as i32;
        let groups = root.get("groups").and_then(Value::as_array);
        if let Some(groups) = groups {
            let count = groups.len().min(32);
            rp.profile.groups_count = count as u32;
            for (slot, group) in rp.profile.groups[..count].iter_mut().zip(groups) {
                *slot = group.as_i64().unwrap_or(0) as i32;
            }
        }
        if let Some(caps) = root.get("capabilities") {
            rp.profile.capabilities.effective = caps.get("effective").and_then(Value::as_u64).unwrap_or(0);
            rp.profile.capabilities.permitted = caps.get("permitted").and_then(Value::as_u64).unwrap_or(0);
            rp.profile.capabilities.inheritable = caps.get("inheritable").and_then(Value::as_u64).unwrap_or(0);
        }
        put_string(
            &mut rp.profile.selinux_domain,
            root.get("context").and_then(Value::as_str).unwrap_or("u:r:su:s0"),
        );
        rp.profile.namespaces = root.get("namespaces").and_then(Value::as_i64).unwrap_or(0) as i32;
        rp.profile.flags = root.get("flags").and_then(Value::as_u64).unwrap_or(0);
    }
    let non_root = value.get("non_root");
    let mut nrp = NrpConfig::default();
    if let Some(non_root) = non_root {
        nrp.use_default = non_root.get("use_default").and_then(Value::as_bool).unwrap_or(false);
        nrp.profile.umount_modules = non_root.get("umount_modules").and_then(Value::as_bool).unwrap_or(false);
    }
    if profile.allow_su {
        profile.config.rp = rp;
    } else {
        profile.config.nrp = nrp;
    }
    Ok(profile)
}

