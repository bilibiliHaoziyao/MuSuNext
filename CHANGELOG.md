# Changelog

All notable changes to MuSu Next will be documented in this file.

## [1.0.0] - Initial Fork

### Added
- MuSu Next 独立品牌：应用名、主题、图标文案全面替换为 MuSu Next
- 独立包名 `org.musu.next`（上游为 `me.weishu.kernelsu`）
- 自签名证书 CN=MuSu Next（SHA256 `eeed9504…ead0`）
- 管理器关于页新增 B 站主页跳转（https://b23.tv/6UtmCB0）
- BRANDING.md：品牌化与内核兼容性说明
- RELEASE.md：CLI 构建与发布指南

### Changed
- 内核签名校验值同步更新：`KSU_EXPECTED_HASH` / `KSU_EXPECTED_SIZE`
- AIDL 接口、JNI 函数名、Application 类名、资源名同步迁移到新包名
- 仓库外链统一指向 `bilibiliHaoziyao/MuSuNext`

### Preserved (compatibility contract)
- 保留 `/data/adb/ksu`、`ksud`、`KSU_*` IOCTL 等上游内核通信契约
- 保留上游公共服务地址（`modules.kernelsu.org` 等）
- 沿用上游 GPL-2.0 / GPL-3.0 双协议
