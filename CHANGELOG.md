# Changelog

All notable changes to MuSu Next will be documented in this file.

## [1.1.0] - LKM-Only

### Removed（移除 GKI 支持）
- ksud：`boot-patch --kernel` 内核替换分支与 `ensure_gki_kernel` 门禁、`parse_kmi_from_kernel`
- kernel：`setup.sh`（GKI 内核源码树集成脚本）
- 管理器：GKI 工作模式警告卡与 `home_gki_warning`、`require_kernel_version_gki`、`select_file_tip_nogki` 字符串
- 文档：GKI 安装章节（installation 4 语言）、GKI 内核编译教程（how-to-build 重写为 LKM 指南）、非 GKI 集成教程（how-to-integrate-for-non-gki 整页下线）

### Changed
- 产品定位：主攻 LKM（可加载内核模块），不再提供 GKI boot 镜像安装方式
- `isGKI()` 更名 `isSupportedKernel()`；首页工作模式徽章内置模式显示 `Built-in`
- 应用语言精简为简体中文 / 繁体中文 / 英语 / 日语（README 与文档站同步）
- `boot_patch` 分区选择函数移除 `is_replace_kernel` 参数（LKM 注入恒为非替换路径）
- 修复品牌化误伤的 4 处 git 依赖地址（`KernelSU2/*`）

### Preserved
- `ksud boot-patch`（LKM 注入主入口）、`boot-patch-v2`、`boot-restore` 及全部 CLI 命令
- `parse_kmi` / `get_current_kmi` / 分区选择等 LKM 公共设施
- `lkm_image.rs` 的 `GKI_ABI` 等 LKM 注入所需内核 ABI 常量
- CI 全部 LKM 构建链（`gki_defconfig` 为 LKM 编译必需步骤）

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
