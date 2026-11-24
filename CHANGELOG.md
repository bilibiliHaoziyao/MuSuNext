# Changelog

All notable changes to MuSu Next will be documented in this file.

## [0.0.6] - 2026-11-24

### Added
- 内建 MuZygisk：基于 ReZygisk fork 的内建 Zygisk 实现，默认禁用
  - 不在模块列表显示，仅在「设置 → MuZygisk」中用原生界面（非 WebUI）管理，页面顶部为总开关
  - 与设备上的其他 Zygisk 实现硬互斥：已有其他实现时开关置灰并点名冲突对象；MuZygisk 开启后其他实现也无法启用
  - 开启后需完整重启生效；以 AGPL-3.0-or-later 单独授权，见 `muzygisk/`
- 内建 MuMount：基于 Hybrid Mount 的内建元模块，默认禁用
  - 同样不在模块列表显示，在「设置 → MuMount」中用原生界面管理，顶部总开关
  - 与其他元模块互斥，规则与 MuZygisk 一致；可与 MuZygisk 同时开启
  - 打包上游完整载荷（overlay / magic / vfs 三后端及内核模块），GPL-3.0-only
- ksud 新增 `builtin` 子系统与 `ksud builtin list/status/enable/disable/detect/reset/apply` 命令：状态唯一真源、幂等载荷解压、互斥探测、开机编排与失败降级
- 官网「特色功能」新增 MuZygisk 与 MuMount 四语言文档

## [0.0.5] - 2026-11-23

### Added
- 新增底部「安全」Tab（位于底部栏中间）：Root 访问审计面板
  - 审计状态卡片：显示 sulog 记录是否启用，未启用时一键开启
  - 按应用聚合统计：每个应用（comm/exe）的 Root 请求次数、最近调用时间，以及 root execve / sucompat / ioctl 授权三类事件的分类计数
  - 快捷入口跳转完整 SU 日志页查看逐条记录
  - Material 与 Miuix 双主题适配；桌面横屏侧边栏同步加入「安全」入口

## [0.0.4] - 2026-11-23

### Changed
- 应用图标换成新形象，图形内容按画布 75% 居中收边，主体更饱满、四周保留均匀留白
- 图标统一为圆角矩形：桌面图标、启动页、关于页三处形状一致
- 修复系统层把图标裁成圆形/方形的问题：桌面移除自适应图标遮罩，直接使用圆角矩形位图；启动页改用在圆形安全区内完整显示的圆角矩形图标，并去掉原来的圆形底色
- 启动页图标由原来的 KernelSU 方角描边矢量动画换成新形象的圆角矩形位图
- 关于页图标改用新形象；Miuix 主题不再把图标单色化，保留原本配色
- 官网同步换图标：`logo.png`（分享卡片 192px）与 `favicon.ico`（16/32/48 多尺寸），导航与浏览器标签一致

## [0.0.3] - 2026-11-23

### Fixed
- 「下载 boot 并修补」报 `write out new boot failed`：产物目录写不进去时整个修补白做。现在按顺序自动换位置——原路径 → `/data/media/0` 原始路径（绕开 FUSE 的 uid 限制）→ boot 镜像所在目录 → 临时目录，并把每次失败的路径和 errno 打进日志

### Changed
- 启动字符画由 `KernelSU` 换成 `MuHan`

## [0.0.2] - 2026-11-23

### Added
- 管理器内置全部 8 个 KMI 的 `musu.ko`：装完 APK 直接选 boot 修补，全程无需联网下载模块
- KMI 选择弹窗：显示内置模块数量，列表按 Android 版本排序，默认选中当前设备的 KMI
- `scripts/verify_apk_lkm.py`：从 APK 的 `libksud.so` 里检出实际内置的 `musu.ko`，发布前逐个 ABI 校验
- 官网新增两个在线工具：`/kmi` 内核模块匹配、`/verify` APK 签名校验（浏览器内解析 v2/v3 证书）

### Changed
- `ksud boot-info supported-kmis` 输出去重并按 Android 版本排序（原为资源遍历顺序）
- `getSupportedKmis()` 在内置清单读取失败时回退到编译期常量，弹窗不再空白
- CI 拦截半成品：ksud 打包时 ko 不足 8 个直接失败；repack 后再校验成品 APK
- 修复 `ddk-lkm.yml` 写死带空格的工作区路径，导致 `git config` 失败、Release 的 LKM 构建全红

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
- 修复误改的 4 处 git 依赖地址（`KernelSU2/*`）

### Preserved
- `ksud boot-patch`（LKM 注入主入口）、`boot-patch-v2`、`boot-restore` 及全部 CLI 命令
- `parse_kmi` / `get_current_kmi` / 分区选择等 LKM 公共设施
- `lkm_image.rs` 的 `GKI_ABI` 等 LKM 注入所需内核 ABI 常量
- CI 全部 LKM 构建链（`gki_defconfig` 为 LKM 编译必需步骤）

## [1.0.0] - Initial Fork

### Added
- 基于 KernelSU 的衍生版本：应用名、主题、图标文案为 MuSu Next
- 包名 `org.musu.next`（上游为 `me.weishu.kernelsu`）
- 签名证书 CN=MuSu Next（SHA256 `eeed9504…ead0`）
- 管理器关于页新增 B 站主页跳转（https://b23.tv/6UtmCB0）
- RELEASE.md：CLI 构建与发布指南

### Changed
- 内核签名校验值同步更新：`KSU_EXPECTED_HASH` / `KSU_EXPECTED_SIZE`
- AIDL 接口、JNI 函数名、Application 类名、资源名同步迁移到新包名
- 仓库外链统一指向 `bilibiliHaoziyao/MuSuNext`

### Preserved (compatibility contract)
- 保留 `/data/adb/ksu`、`ksud`、`KSU_*` IOCTL 等上游内核通信契约
- 保留上游公共服务地址（`modules.kernelsu.org` 等）
- 沿用上游 GPL-2.0 / GPL-3.0 双协议
