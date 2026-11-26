//! Compatibility detection: uname KMI token, vendor Android release, ABI,
//! SELinux, and the fixed KMI→ko table (docs/specs/dirtyfrag.md §5.3).
//!
//! The pure classification logic (table match, reason severities, verdict
//! ordering) is host-safe and unit-tested. Only the OS probes are cfg-gated.

use super::report::{
    DetectView, DeviceView, MatchView, ReasonView, SEV_BLOCKER, SEV_WARNING, V_ALREADY_ROOTED,
    V_READY, V_UNSUPPORTED, V_UNKNOWN,
};

/// The eight upstream ko targets (spec §1.2 U6). No entry means "no payload"
/// and must be refused, never guessed.
pub const PAYLOAD_KMIS: [&str; 8] = [
    "android12-5.10",
    "android13-5.10",
    "android13-5.15",
    "android14-5.15",
    "android14-6.1",
    "android15-6.6",
    "android16-6.12",
    "android17-6.18",
];

/// Reason codes are part of the wire contract; severities are fixed (§5.3).
pub const R_KMI_UNKNOWN: &str = "KMI_UNKNOWN";
pub const R_KMI_NO_PAYLOAD: &str = "KMI_NO_PAYLOAD";
pub const R_ABI_UNSUPPORTED: &str = "ABI_UNSUPPORTED";
pub const R_PAYLOAD_VERIFY_FAILED: &str = "PAYLOAD_VERIFY_FAILED";
pub const R_VENDOR_PATCH_SUSPECTED: &str = "VENDOR_PATCH_SUSPECTED";
pub const R_SELINUX_ENFORCING: &str = "SELINUX_ENFORCING";

pub fn reason_severity(code: &str) -> &'static str {
    match code {
        R_KMI_UNKNOWN | R_KMI_NO_PAYLOAD | R_ABI_UNSUPPORTED | R_PAYLOAD_VERIFY_FAILED => {
            SEV_BLOCKER
        }
        _ => SEV_WARNING,
    }
}

/// Extract the `androidNN` token and the kernel `major.minor` from `uname -r`
/// (e.g. `6.1.157-android14-11-o-xxxx` → `android14-6.1`). Returns None when
/// the android token is absent — the caller then emits KMI_UNKNOWN.
pub fn parse_kmi(release: &str) -> Option<String> {
    let mut android_tag: Option<String> = None;
    for token in release.split(|c: char| c.is_whitespace() || c == '-' || c == '_') {
        if token.len() > 7
            && token.starts_with("android")
            && token[7..].bytes().all(|b| b.is_ascii_digit())
        {
            android_tag = Some(token.to_string());
            break;
        }
    }
    let android_tag = android_tag?;
    let kernel = parse_kernel_version(release)?;
    Some(format!("{android_tag}-{kernel}"))
}

fn parse_kernel_version(release: &str) -> Option<String> {
    let bytes = release.as_bytes();
    let mut idx = 0;
    while idx < bytes.len() {
        if bytes[idx].is_ascii_digit() {
            let start = idx;
            while idx < bytes.len() && bytes[idx].is_ascii_digit() {
                idx += 1;
            }
            if idx < bytes.len() && bytes[idx] == b'.' {
                idx += 1;
                let minor_start = idx;
                while idx < bytes.len() && bytes[idx].is_ascii_digit() {
                    idx += 1;
                }
                if idx > minor_start {
                    // `start..idx` spans "major.minor"; the dot is included.
                    return Some(release[start..idx].to_string());
                }
            }
        } else {
            idx += 1;
        }
    }
    None
}

/// Return the ko filename for a KMI, or None when the device KMI has no row.
pub fn match_payload(kmi: &str) -> Option<String> {
    if PAYLOAD_KMIS.contains(&kmi) {
        Some(format!("dfroot-{kmi}.ko"))
    } else {
        None
    }
}

// ---- payload authenticity helpers (§4.4, fail-closed) --------------------
// Kept here — not in the Android-only payload module — so the checks are
// unit-tested on the host.

/// Compliance files that must be present or stage is refused with 2104 (§4.2).
pub const COMPLIANCE_FILES: [&str; 4] = ["VERSION", "LICENSE", "NOTICE", "SOURCE.txt"];

pub fn sha256_hex(bytes: &[u8]) -> String {
    sha256::digest(bytes)
}

/// `\x7fELF` magic and `e_machine == 183` (AArch64) — §4.4 step 3.
pub fn is_elf_aarch64(bytes: &[u8]) -> bool {
    bytes.len() >= 20
        && bytes.starts_with(b"\x7fELF")
        && u16::from_le_bytes([bytes[18], bytes[19]]) == 183
}

/// ZIP local-header / empty-archive magic — §4.4 step 4 (APK container).
pub fn is_zip(bytes: &[u8]) -> bool {
    bytes.len() >= 4
        && (bytes.starts_with(b"PK\x03\x04")
            || bytes.starts_with(b"PK\x05\x06")
            || bytes.starts_with(b"PK\x07\x08"))
}

/// Parse `SHA256SUMS` lines of the form `<hex>  <relative path>`.
pub fn parse_sha256sums(raw: &str) -> Vec<(String, String)> {
    raw.lines()
        .filter_map(|line| {
            let line = line.trim();
            if line.is_empty() || line.starts_with('#') {
                return None;
            }
            let mut parts = line.split_whitespace();
            let hash = parts.next()?.to_ascii_lowercase();
            let path = parts.next()?.to_string();
            if hash.len() != 64 || !hash.bytes().all(|b| b.is_ascii_hexdigit()) {
                return None;
            }
            Some((hash, path))
        })
        .collect()
}

/// Verdict order is significant (§5.3): exposed beats everything; then an
/// unsupported device; then ready; otherwise unknown.
pub fn verdict(exposed: bool, supported: bool, verified: bool) -> &'static str {
    if exposed {
        V_ALREADY_ROOTED
    } else if !supported {
        V_UNSUPPORTED
    } else if verified {
        V_READY
    } else {
        V_UNKNOWN
    }
}

/// Build the full detection view from probe inputs. Pure: no filesystem, no
/// syscall, so it is fully unit-testable on the host.
pub fn classify(
    device: DeviceView,
    payload_verified: bool,
    vendor_patch_suspected: bool,
) -> DetectView {
    let mut reasons: Vec<ReasonView> = Vec::new();

    let kmi = device.kmi.as_str();
    let payload = if kmi.is_empty() {
        reasons.push(ReasonView::new(
            R_KMI_UNKNOWN,
            reason_severity(R_KMI_UNKNOWN),
            format!("uname 未找到 androidNN token（回移: {}）", device.kernel_release),
        ));
        None
    } else {
        let found = match_payload(kmi);
        if found.is_none() {
            reasons.push(ReasonView::new(
                R_KMI_NO_PAYLOAD,
                reason_severity(R_KMI_NO_PAYLOAD),
                format!("内核 {kmi} 无对应 ko，拒绝猜测匹配"),
            ));
        }
        found
    };

    if device.abi != "arm64-v8a" {
        reasons.push(ReasonView::new(
            R_ABI_UNSUPPORTED,
            reason_severity(R_ABI_UNSUPPORTED),
            format!("当前 ABI {} 无对应载荷", device.abi),
        ));
    }

    if payload.is_some() && !payload_verified {
        reasons.push(ReasonView::new(
            R_PAYLOAD_VERIFY_FAILED,
            reason_severity(R_PAYLOAD_VERIFY_FAILED),
            String::from("载荷 sha256 / ELF / 签名校验未通过，拒绝执行"),
        ));
    }

    if vendor_patch_suspected {
        reasons.push(ReasonView::new(
            R_VENDOR_PATCH_SUSPECTED,
            reason_severity(R_VENDOR_PATCH_SUSPECTED),
            String::from("设备可能回移了官方修复，即使 KMI 匹配也可能失败"),
        ));
    }

    if device.selinux == "enforcing" {
        reasons.push(ReasonView::new(
            R_SELINUX_ENFORCING,
            reason_severity(R_SELINUX_ENFORCING),
            String::from("SELinux 处于 enforcing，引擎成功后会临时切换为 permissive"),
        ));
    }

    let supported = payload.is_some() && device.abi == "arm64-v8a" && !kmi.is_empty();
    let verified = supported && payload_verified;
    let mat = MatchView {
        payload,
        matched_by: if supported {
            Some(String::from("kmi"))
        } else {
            None
        },
        payload_sha256: None,
        verified,
    };
    DetectView {
        exposed: false,
        supported,
        verdict: verdict(false, supported, verified),
        device,
        mat,
        reasons,
    }
}

// ---- OS probes ------------------------------------------------------------

#[cfg(target_os = "android")]
unsafe extern "C" {
    fn __system_property_get(name: *const libc::c_char, value: *mut libc::c_char) -> libc::c_int;
}

#[cfg(target_os = "android")]
fn property(name: &str) -> Option<String> {
    use std::ffi::CString;
    let key = CString::new(name).ok()?;
    let mut buf = [0u8; 128];
    // SAFETY: key is NUL-terminated and buf has room for the value.
    let len = unsafe {
        __system_property_get(key.as_ptr(), buf.as_mut_ptr().cast::<libc::c_char>())
    };
    if len <= 0 {
        return None;
    }
    Some(
        String::from_utf8_lossy(&buf[..len as usize])
            .trim()
            .to_string(),
    )
}

/// Read the device identity. Android uses real sources; other platforms get a
/// deterministic fixture so the module still compiles and tests on host.
pub fn probe_device() -> DeviceView {
    #[cfg(target_os = "android")]
    {
        let kernel_release = std::fs::read_to_string("/proc/sys/kernel/osrelease")
            .unwrap_or_default()
            .trim()
            .to_string();
        let kmi = parse_kmi(&kernel_release).unwrap_or_default();
        let android_release = property("ro.vendor.build.version.release").unwrap_or_default();
        let abi = property("ro.product.cpu.abi").unwrap_or_else(|| String::from("arm64-v8a"));
        let selinux = match std::fs::read_to_string("/sys/fs/selinux/enforce") {
            Ok(raw) if raw.trim() == "0" => String::from("permissive"),
            Ok(_) => String::from("enforcing"),
            Err(_) => String::from("unknown"),
        };
        DeviceView {
            kmi,
            kernel_release,
            android_release,
            abi,
            selinux,
        }
    }
    #[cfg(not(target_os = "android"))]
    {
        DeviceView::default()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_kmi_from_uname() {
        assert_eq!(
            parse_kmi("6.1.157-android14-11-o-xxxx"),
            Some(String::from("android14-6.1"))
        );
        assert_eq!(
            parse_kmi("5.15.100-android13-5-kbuild"),
            Some(String::from("android13-5.15"))
        );
        assert_eq!(parse_kmi("4.19.0-generic"), None);
    }

    #[test]
    fn kmi_table_has_eight_rows_and_maps_to_ko() {
        assert_eq!(PAYLOAD_KMIS.len(), 8);
        for kmi in PAYLOAD_KMIS {
            assert_eq!(match_payload(kmi), Some(format!("dfroot-{kmi}.ko")));
        }
        assert_eq!(match_payload("android99-9.9"), None);
        assert_eq!(match_payload(""), None);
    }

    #[test]
    fn verdict_order_exposed_then_unsupported_then_ready_then_unknown() {
        // exposed wins even if supported/verified are false
        assert_eq!(verdict(true, false, false), V_ALREADY_ROOTED);
        assert_eq!(verdict(true, true, true), V_ALREADY_ROOTED);
        // unsupported wins over ready
        assert_eq!(verdict(false, false, true), V_UNSUPPORTED);
        // supported+verified → ready
        assert_eq!(verdict(false, true, true), V_READY);
        // supported but unverified → unknown
        assert_eq!(verdict(false, true, false), V_UNKNOWN);
    }

    #[test]
    fn severity_mapping_matches_contract() {
        for code in [
            R_KMI_UNKNOWN,
            R_KMI_NO_PAYLOAD,
            R_ABI_UNSUPPORTED,
            R_PAYLOAD_VERIFY_FAILED,
        ] {
            assert_eq!(reason_severity(code), SEV_BLOCKER);
        }
        assert_eq!(reason_severity(R_VENDOR_PATCH_SUSPECTED), SEV_WARNING);
        assert_eq!(reason_severity(R_SELINUX_ENFORCING), SEV_WARNING);
    }

    fn supported_device() -> DeviceView {
        DeviceView {
            kmi: String::from("android14-6.1"),
            kernel_release: String::from("6.1.157-android14-11-o-xxxx"),
            android_release: String::from("14"),
            abi: String::from("arm64-v8a"),
            selinux: String::from("enforcing"),
        }
    }

    #[test]
    fn classify_supported_verified_is_ready() {
        let view = classify(supported_device(), true, false);
        assert!(view.supported);
        assert!(view.mat.verified);
        assert_eq!(view.verdict, V_READY);
        assert_eq!(view.mat.payload.as_deref(), Some("dfroot-android14-6.1.ko"));
        assert_eq!(view.mat.matched_by.as_deref(), Some("kmi"));
        // SELinux enforcing is a warning, not a blocker.
        assert!(view.reasons.iter().any(|r| r.code == R_SELINUX_ENFORCING));
        assert!(!view
            .reasons
            .iter()
            .any(|r| r.severity == SEV_BLOCKER));
    }

    #[test]
    fn classify_no_payload_is_blocked_not_guessed() {
        let mut device = supported_device();
        device.kmi = String::from("android99-9.9");
        let view = classify(device, true, false);
        assert!(!view.supported);
        assert_eq!(view.verdict, V_UNSUPPORTED);
        assert_eq!(view.mat.payload, None);
        assert!(view
            .reasons
            .iter()
            .any(|r| r.code == R_KMI_NO_PAYLOAD && r.severity == SEV_BLOCKER));
    }

    #[test]
    fn elf_and_zip_magic_detection() {
        let mut elf = vec![0u8; 20];
        elf[..4].copy_from_slice(b"\x7fELF");
        elf[18..20].copy_from_slice(&183u16.to_le_bytes());
        assert!(is_elf_aarch64(&elf));
        elf[18..20].copy_from_slice(&3u16.to_le_bytes()); // EM_386
        assert!(!is_elf_aarch64(&elf));
        assert!(!is_elf_aarch64(b"not an elf"));
        assert!(is_zip(b"PK\x03\x04rest"));
        assert!(!is_zip(b"nope"));
    }

    #[test]
    fn sha256sums_parsing_is_strict() {
        let a = "a".repeat(64);
        let b = "b".repeat(64);
        let raw = format!(
            "{a}  ko/dfroot-android14-6.1.ko\n# comment\nbad  ignored.txt\n{b}  dirtyfrag-locked.apk\n"
        );
        let entries = parse_sha256sums(&raw);
        assert_eq!(entries.len(), 2);
        assert_eq!(entries[0].1, "ko/dfroot-android14-6.1.ko");
        assert_eq!(entries[1].1, "dirtyfrag-locked.apk");
        assert_eq!(sha256_hex(b"abc").len(), 64);
    }

    #[test]
    fn compliance_file_list_has_four_entries() {
        assert_eq!(COMPLIANCE_FILES.len(), 4);
        assert!(COMPLIANCE_FILES.contains(&"LICENSE"));
        assert!(COMPLIANCE_FILES.contains(&"SOURCE.txt"));
    }

    #[test]
    fn classify_bad_abi_and_unverified_payload_are_blockers() {
        let mut device = supported_device();
        device.abi = String::from("armeabi-v7a");
        let view = classify(device, false, false);
        assert!(!view.supported);
        assert!(view
            .reasons
            .iter()
            .any(|r| r.code == R_ABI_UNSUPPORTED && r.severity == SEV_BLOCKER));
        assert!(!view.mat.verified);
        assert_eq!(view.verdict, V_UNSUPPORTED);
    }
}
