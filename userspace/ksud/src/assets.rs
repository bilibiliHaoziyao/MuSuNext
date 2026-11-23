use anyhow::Result;
use rust_embed::RustEmbed;

#[cfg(target_os = "android")]
mod android {
    use crate::assets::Asset;
    use crate::defs::BINARY_DIR;
    use crate::utils::ensure_binary;
    use const_format::concatcp;

    pub const RESETPROP_PATH: &str = concatcp!(BINARY_DIR, "resetprop");
    pub const BUSYBOX_PATH: &str = concatcp!(BINARY_DIR, "busybox");
    pub const BOOTCTL_PATH: &str = concatcp!(BINARY_DIR, "bootctl");

    pub fn ensure_binaries(ignore_if_exist: bool) -> anyhow::Result<()> {
        for file in Asset::iter() {
            if file == "ksuinit" || file == "waitsys" || file.ends_with(".ko") {
                // don't extract internal executables and kernel modules
                continue;
            }
            let asset =
                Asset::get(&file).ok_or_else(|| anyhow::anyhow!("asset not found: {file}"))?;
            ensure_binary(format!("{BINARY_DIR}{file}"), &asset.data, ignore_if_exist)?;
        }

        // Create resetprop -> ksud symlink (resetprop is now built into ksud)
        let resetprop_link = RESETPROP_PATH;
        let _ = std::fs::remove_file(resetprop_link);
        std::os::unix::fs::symlink("/data/adb/ksud", resetprop_link)?;

        Ok(())
    }
}

#[cfg(target_os = "android")]
pub use android::*;

#[cfg(all(target_arch = "x86_64", target_os = "android"))]
#[derive(RustEmbed)]
#[folder = "bin/x86_64"]
struct Asset;

#[cfg(all(target_arch = "aarch64", target_os = "android"))]
#[derive(RustEmbed)]
#[folder = "bin/aarch64"]
struct Asset;

#[cfg(all(target_arch = "riscv64", target_os = "android"))]
#[derive(RustEmbed)]
#[folder = "bin/riscv64"]
struct Asset;

// If not Android, ie. macos, linux, windows, include all architectures.
#[cfg(not(target_os = "android"))]
#[derive(RustEmbed)]
#[folder = "bin"]
struct Asset;

#[allow(unused)]
pub fn get_asset_data(name: &str) -> Result<std::borrow::Cow<'static, [u8]>> {
    let asset = Asset::get(name).ok_or_else(|| anyhow::anyhow!("asset not found: {name}"))?;
    Ok(asset.data)
}

pub fn get_asset(name: &str) -> Result<Box<dyn AsRef<[u8]>>> {
    let asset = Asset::get(name).ok_or_else(|| anyhow::anyhow!("asset not found: {name}"))?;
    Ok(Box::new(asset.data))
}

/// 把 `android14-6.1` 这样的名字变成可排序的数字三元组。
/// 排序只影响弹窗里的显示顺序，不影响匹配逻辑。
fn kmi_sort_key(kmi: &str) -> (u32, u32, u32) {
    let rest = kmi.strip_prefix("android").unwrap_or(kmi);
    let mut split = rest.split('-');
    let sdk = split.next().and_then(|v| v.parse().ok()).unwrap_or(0);
    let kernel = split.next().unwrap_or_default();
    let mut ver = kernel.split('.');
    let major = ver.next().and_then(|v| v.parse().ok()).unwrap_or(0);
    let minor = ver.next().and_then(|v| v.parse().ok()).unwrap_or(0);
    (sdk, major, minor)
}

pub fn list_supported_kmi() -> std::vec::Vec<std::string::String> {
    let mut list: Vec<String> = Asset::iter()
        .filter_map(|file| {
            // 桌面构建的资源根是 bin/，键里带 `aarch64/` 前缀，这里只取文件名。
            let name = file.rsplit('/').next()?.to_string();
            name.strip_suffix("_musu.ko").map(str::to_string)
        })
        // 只认 androidNN-X.Y，避免把目录名或临时文件当成 KMI。
        .filter(|kmi| kmi.starts_with("android"))
        .collect();
    list.sort_by_key(|kmi| kmi_sort_key(kmi));
    list.dedup();
    list
}
