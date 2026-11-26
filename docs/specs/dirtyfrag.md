# DirtyFrag 一键越狱 架构规格（0.0.8）

> 状态：架构定案（本文只定边界、契约与合规，不写实现代码；不修改与本 Spec 无关的文件）
> 范围：GPL 可行性裁决、模块边界与目录、载荷集成、ksud CLI 契约、锁定 MuSu Next、UI 数据流、验收与验证、风险表。
> 读者：主理人（验收）、Rust 后端 / Kotlin 前端（按 §3–§7 落地）、QA（按 §8 验证）。
> 术语纪律：全文无 emoji；图标库锁定 `androidx.compose.material:material-icons-extended`（管理器已在 `manager/gradle/libs.versions.toml` 引入），不引入第二套图标；不出现紫→粉渐变；不写空洞占位文案。
> 版本锚定：MuSu Next 0.0.8（`manager/build.gradle.kts` 当前 `getVersionCode()=7` / `getVersionName()="0.0.7"`，由集成者改为 8 / "0.0.8"，本文不改该文件）；DirtyFrag v1.18（上游 2026-11-17，包 id `df.root`）。

---

## 0. 结论摘要

| # | 结论 | 一句话依据 |
|---|---|---|
| 1 | **GPL 隔离方案成立（有条件）** | DirtyFrag 是 GPL-2.0-only、MuSu Next userspace 是 GPL-3.0-or-later，二者**不能合并成单一作品**；但二者可以"纯粹聚合（mere aggregation）"分发——本仓库 `kernel/`（GPL-2.0-only，`README.md:35`）与 userspace（GPL-3.0-or-later，`README.md:36`）已是这样并存的先例（§2） |
| 2 | 最终架构（一句话） | **MuSu Next 只做原生 Compose 编排与进程级调度：把"锁定版 DirtyFrag"作为独立 GPL-2.0-only 程序分发，管理器经进程边界用单 JSON 调用 ksud 的 `dirtyfrag` 子命令完成 stage→detect→run→status，管理器/ksud 侧不含任何漏洞链代码** |
| 3 | 成立的前提有 5 条，缺一即翻转 | 独立进程、独立源码树、独立许可证、独立完整源码提供、编排层零移植零链接（§2.3）；任一不满足即构成无法许可的衍生作品 |
| 4 | 载荷必须是真源码真二进制 | 从 `mitschud/DirtyFrag` v1.18 固定 commit fork，8 个 ko 取上游 `app/src/main/jni/ko/` 真文件，SHA256 + ELF 头双校验，禁止占位符（§4） |
| 5 | 锁定 MuSu Next 必须改上游 | 上游管理器白名单只有 KernelSU / KSU-Next / KowSU / BakaSU / diabl0w，`org.musu.next` 不在其中；必须在 fork 中加白名单 + "锁定模式"（§6） |

**风险前置**：最危险的是 §2.3（"编排层移植漏洞链"会把合规方案变成侵权方案），第二条是 §9 KMI 误配 bricking，第三条是 §6 的 fork 修改触发 GPL-2.0-only 源码提供义务。

---

## 1. 事实基线（读代码 + 官方 README 得到，后文全部引用）

### 1.1 仓库内事实（F）

| # | 事实 | 出处 |
|---|---|---|
| F1 | 仓库许可证二分：`kernel/` 为 GPL-2.0-only，其余为 GPL-3.0-or-later | `README.md:35-36`、`docs/README.md:55-56`、`LICENSE:1`（GPLv3） |
| F2 | 管理器包名 `org.musu.next`（PR 构建 `org.musu.next.pr`） | `manager/app/build.gradle.kts:26,64,149` |
| F3 | 版本号手工维护，当前 versionCode 7 / versionName 0.0.7 | `manager/build.gradle.kts:18-26` |
| F4 | 现有越狱按钮：Material 仅在 `notInstalled && state.isSELinuxPermissive` 显示，点击调 `actions.onJailbreakClick` | `HomeMaterial.kt:195,235-246`；Miuix 同条件 `HomeMiuix.kt:389-392` |
| F5 | `onJailbreakClick` 现状：显示 loading → `startService(MagicaService)` → 30s 后 Toast `jailbreak_timeout` | `HomeScreen.kt:67-78` |
| F6 | `MagicaService` 另有开机入口，替换按钮时不能删除该服务 | `magica/BootCompletedReceiver.java:27-31`、`SettingsRepositoryImpl.kt:25,152` |
| F7 | 动作契约集中：`HomeActions`（含 `onJailbreakClick`） | `HomeUiState.kt:63` |
| F8 | 双主题由 `LocalUiMode.current` 分流 Material / Miuix | `HomeScreen.kt:83` |
| F9 | 语言资源：values(en) / values-zh-rCN / values-zh-rTW / values-zh-rHK / values-ja（+values-night 主题变体） | `manager/app/src/main/res/` 目录 |
| F10 | ksud CLI 分两份：Android 用 `cli.rs`（含 `Commands` 全表），非 Android 用 `cli_non_android.rs`（子集）；`main.rs` 以 `cfg(target_os="android")` 分流 | `cli.rs:1-180`、`cli_non_android.rs:1-80`、`main.rs:76-84` |
| F11 | 已有单 JSON 输出纪律：`jsoncmd::emit` 打印一个对象后 `process::exit(code)`；`snapshot` / `envcheck` 是范例 | `jsoncmd.rs:1-36`、`cli.rs:597-606` |
| F12 | 内建组件隔离先例：`builtin/` 六个实现文件按 `cfg(target_os="android")` 门控，`error.rs` 全平台编译；CLI 侧一组 `Builtin` 子命令 | `builtin/mod.rs:1-30`、`cli.rs:909-918`、`docs/specs/builtin-components.md` |
| F13 | 载荷用**独立** `RustEmbed`（`#[folder = "bin/builtin"]`），不走主 `Asset`；主 `Asset` 的 `ensure_binaries()` 明确跳过 `*.ko` | `builtin/payload.rs:20-42`、`assets.rs:12-17` |
| F14 | `SOURCE.txt` 格式：`upstream= / upstream_url= / upstream_commit= / license= / note=` | `bin/builtin/aarch64/zygisk/SOURCE.txt` |
| F15 | 管理器自带 `libksud.so`（构建前从 ksud release 拷入 jniLibs） | `AGENTS.md:56-60,85` |
| F16 | 内置组件规格要求"锁定 SVG 图标库、无 emoji、无紫粉渐变、无占位文案" | `docs/specs/builtin-components.md:7`、`builtin-ui.md:331,461-471` |

### 1.2 上游事实（U，来自官方 README / releases，必要时已联网核实）

| # | 事实 | 依据 |
|---|---|---|
| U1 | DirtyFrag = diabl0w/DFRoot 的加固 fork，针对 Samsung OneUI（RKP/KDP），许可证 **GPL-2.0-only** | 官方 README（License 段）；releases 徽章 `License-GPL--2.0--only` |
| U2 | README 原话："GPL-2.0-only cannot absorb GPL-3.0-only code. A GPL-3 reference ... may be read for behaviour, record formats and API naming, and **none of its code may be copied, translated or linked** into this tree." | 官方 README（License 段末） |
| U3 | 应用 id `df.root`；纯 Java、不用 androidx；minSdk 32（Android 12L+） | 官方 README（Features / Install / FAQ） |
| U4 | 自 1.10（上游 3.2）起**不再自带 ksud**，从所选管理器的 `libksud.so` **stage**；无该库的管理器无法出 root | 官方 README `[!WARNING]` 段 |
| U5 | 上游 SU Manager 白名单：Samsung 必须 diabl0w's KernelSU；其它 KernelSU / KernelSU-Next / KowSU / BakaSU。`org.musu.next` **不在列** | 官方 README（Install 段） |
| U6 | 8 个预编译 ko 提交在 `app/src/main/jni/ko/`：android12-5.10 / android13-5.10 / android13-5.15 / android14-5.15 / android14-6.1 / android15-6.6 / android16-6.12 / android17-6.18 | 官方 README（Building / Supported Platforms） |
| U7 | 按 GKI level（`uname` 的 `androidNN` token）+ `ro.vendor.build.version.release` 选 ko；无对应行则**拒绝而非猜测** | 官方 README（Supported Platforms）；需求提供的技术事实 |
| U8 | 漏洞链：IpSec AES-CBC ESP → splice 进 `crash_dump64` → 写 `dfroot.ko` 到 `vendor_file` → patch libc++ `std::ostream::sentry` 在 `init(uid=0,tid=1)` 触发 → `insmod` → ko `setenforce permissive` + `call_usermodehelper` 跑 bootstrap → 启动所选管理器 SU daemon | 官方 README（How it works / Exploit chain） |
| U9 | 运行窗口约 70 秒，**重复点击会导致内核冲突**；进度条停在 0% 是日志标记映射、非失败；root 重启即失效 | 官方 README（FAQ） |
| U10 | 1.11 起 auto soft reboot；1.17 起 Odin 写需 root；1.18 起简中 UI。Autorun 有"失败一次即自锁"互锁防 bootloop | 官方 README（Features） |
| U11 | 重装 APK 会换 Android UID，SU 授权需重新给 | 官方 README（FAQ） |

---

## 2. 可行性裁决：GPL 隔离方案成立吗

### 2.1 法律事实（不回避结论）

1. **不能合并**：GPL-2.0-only 的 "only" 意味着作者未授予"或后续版本"许可，因此 GPL-2.0-only 代码**不能被并入 GPL-3.0 作品**，反向也不行。两者互相不兼容，合并后的作品没有任何一个许可证可合法分发。DirtyFrag 自己在 README 写死了这条（U2）。
2. **可以聚合**：GPLv2 §2 与 GPLv3 §5 都明确写了"mere aggregation"例外——把受本许可证约束的作品与其它**独立作品**放在同一存储介质上分发，本许可证不延伸到那些独立作品。判定关键是**是否形成一个更大的程序**（共享地址空间 / 链接 / 衍生翻译），而不是"是否在同一个仓库"。
3. **本仓库已在用这条例外**：`kernel/` 是 GPL-2.0-only，userspace 是 GPL-3.0-or-later（F1），二者经 supercall/ioctl 通信、各自独立编译，从未被视作需要统一许可的单一作品。DirtyFrag 的接入与这是同构的。

### 2.2 裁决

> **成立。** 主理人提出的"体验无缝、代码隔离"方案，在把它落实为**真正的程序边界**（而非仅目录边界）后，属于 GPL 允许的纯粹聚合，不产生 GPL 混合衍生作品。**但成立是有条件的，条件见 §2.3；把 DirtyFrag 漏洞链移植进 MuSu Next 代码则立即不成立。**

### 2.3 成立的 5 条硬条件（缺一即翻转）

| # | 条件 | 违反时的后果 | 落地位置 |
|---|---|---|---|
| C1 | **独立进程**：DirtyFrag 以独立 APK / 独立进程运行（独立 UID），MuSu Next 只通过进程边界（Intent/文件 drop-box/单 JSON）与其交互；不得 dlopen、FFI、共享地址空间、函数调用跨界 | 形成"更大程序"，聚合例外失效 | §3、§6 |
| C2 | **独立源码树**：DirtyFrag fork 放顶层 `dirtyfrag/`，独立 LICENSE/NOTICE/SOURCE.txt，**其源码不参与** ksud 或 manager 的编译单元 | 变成衍生作品且许可冲突 | §3、§4 |
| C3 | **独立许可证**：fork 全程 GPL-2.0-only，LICENSE 原文不改；不得声明为 GPL-3.0 或把其代码贴进 GPL-3.0 文件 | 违反 U2 / GPL 条款 | §4.3 |
| C4 | **完整对应源码**：因 fork 有我们自己的修改（加 `org.musu.next`、锁定模式、去 LAUNCHER），不能再只"指向上游"，必须随分发提供我们的修改源码 + patch | GPL-2.0-only §3 义务未履行 | §4.3、§6.1 |
| C5 | **编排层零移植**：ksud/manager 侧只做 stage、检测、结果解析、UI，**不得照读 DirtyFrag 代码重实现 IpSec/splice/libc++ patch/insmod 任一环** | U2 的 "translated" 命中——即使无逐字复制也构成衍生 | §2.4、§10 |

### 2.4 关键推论：「一键」的正确含义

用户要的"一键跑完整个漏洞链"**必须由 DirtyFrag 引擎自己完成**，MuSu Next 的"一键"只是：一次点击 → 拉起锁定版引擎 → 引擎执行链 → 引擎回报结果。MuSu Next 不参与链的任何一个技术步骤。这是"体验无缝"与"代码隔离"能同时成立的**唯一**解释；任何"我们用 Kotlin/Rust 自己实现一遍漏洞链"的提案都要退回。

### 2.5 被否决方案

| 方案 | 否决理由 |
|---|---|
| A. 把 DirtyFrag Java 源码直接编进 manager（同 dex / 同源码树） | 违反 C2/C3，GPL-2.0-only 与 GPL-3.0-or-later 不可合并，产物无法合法分发 |
| B. 在 ksud(Rust) 或 manager(Kotlin) 中按 DirtyFrag 代码重实现漏洞链 | 违反 C5；U2 明令禁止 "copied, translated or linked"；且 8 个 ko 与 shellcode 行为难以在无源码情况下对齐，属"沉默逻辑错误"高发区 |
| C. 由 MuSu Next 动态加载 DirtyFrag 的 dex/so 到本进程执行 | 违反 C1（共享地址空间），且 Android 上 DexClassLoader 需要额外 ClassLoader 隔离，法律上仍被视作组合 |
| D. 保持上游 DirtyFrag 原样、仅外链引导用户另行安装 | `org.musu.next` 不在白名单（U5），无法锁定；且出现独立界面与图标，违反用户决策 1/3 |
| E. 在载荷里放占位 ko / 假 APK 先搭框架 | 违反 §4 真源码要求；MuZygisk 已踩过占位符坑；运行期必失败且难归因 |

**退化方案（若 C1 无法在工程上满足，例如无法安装隐藏 APK）**：改为**纯外部聚合**——仓库内不保留任何 DirtyFrag fork，仅在做完兼容性检测后深度链接到用户自行安装的锁定版引擎 APK。代价：失去无缝体验、多一次安装步骤、授权 UID 变更需重授权（U11）。此方案 0.0.8 不采用，仅作为裁决失败时的兜底。

---

## 3. 模块边界与目录结构

### 3.1 依赖方向（硬性，禁止反向）

```
manager Compose 页面（page）
        │  只依赖 ↓
        ▼
manager Compose 组件（component：状态卡、风险确认卡、进度）
        │  只依赖 ↓
        ▼
DirtyFragRepository（数据层：进程外调用 + JSON 解析）
        │  只依赖 ↓
        ▼
KsuCli.execKsudJson("dirtyfrag …")   ← CLI 边界（stdin/stdout 单 JSON）
        │  进程边界 ↓
        ▼
ksud dirtyfrag 子命令（编排：stage / detect / run / status）
        │  进程边界 ↓
        ▼
锁定版 DirtyFrag 引擎（独立进程，GPL-2.0-only，黑盒）
```

- **禁止反向**：CLI/ksud/引擎不得 import manager 的 UI 包；Repository 不得直接 new Compose 组件；引擎不得回调 MuSu Next 内部类。
- **禁止跨层直达**：页面不得直接 `execKsud`；必须经 Repository。这样 detect 的 JSON 解析与错误归一化只有一处（呼应 runtime-defensive §二：归一化发生在公共接口）。

### 3.2 目录布局

```
/workspace/MuSuNext/
├── dirtyfrag/                              【新增，独立源码树，GPL-2.0-only，C2/C3/C4】
│   ├── LICENSE                             GPL-2.0 原文，逐字不改
│   ├── NOTICE                              上游版权 + "Modifications Copyright (C) 2026 MuSu Next" + 修改清单
│   ├── SOURCE.txt                          U1/U6 的锚：upstream/tag/commit/date/license/note（F14 格式）
│   ├── README.md                           来源、fork 范围、修改清单、许可证与兼容性声明、构建与 SHA256 清单
│   ├── patches/                            我们对上游的每一处修改，逐 patch 可审
│   │   ├── 0001-add-musu-manager.patch     白名单加 org.musu.next
│   │   ├── 0002-locked-mode.patch          intent extra 指定锁定管理器，跳过选择器 UI
│   │   └── 0003-no-launcher.patch          去 LAUNCHER category（无独立图标）
│   └── upstream/                           （可选）上游源码快照，仅在需要随分发提供修改源码时保留
│
├── userspace/ksud/
│   ├── src/
│   │   ├── dirtyfrag/                      【新增，GPL-3.0-or-later，我们原创的编排层】
│   │   │   ├── mod.rs                      facade：命令分发 + 全平台类型 + cfg 门控导出
│   │   │   ├── error.rs                    常量/错误码/DResult（无 Unix-only API，全平台编译）
│   │   │   ├── report.rs                   JSON 组装（serde_json）
│   │   │   ├── state.rs                    boot-scoped 状态 + 幂等/去重
│   │   │   ├── detect.rs                   KMI/内核/SELinux/载荷探测
│   │   │   ├── payload.rs                  独立 RustEmbed + SHA256/ELF 校验 + 暂存
│   │   │   └── run.rs                      进程启动 + 70s 监控 + 结果收口
│   │   ├── cli.rs                          Commands 增加 Dirtyfrag { command: Dirtyfrag }
│   │   ├── cli_non_android.rs              同上增加命令（stub 实现，保证 Windows 编译）
│   │   └── main.rs                         mod dirtyfrag;
│   └── bin/dirtyfrag/arm64/                【新增，载荷产物，独立 RustEmbed】
│       ├── dirtyfrag-locked.apk            真构建产物（非占位）
│       ├── ko/                             8 个真 ko（来自上游 jni/ko/）
│       ├── VERSION                         "1.18-musu"
│       ├── SOURCE.txt  LICENSE  NOTICE     GPL-2.0-only 原文与来源
│       └── SHA256SUMS                      每个文件的 sha256
│
└── manager/app/src/main/
    ├── java/org/musu/next/
    │   ├── ui/screen/dirtyfrag/            【新增，原生 Compose，GPL-3.0-or-later】
    │   │   ├── DirtyFragScreen.kt          路由入口，按 LocalUiMode 分流
    │   │   ├── DirtyFragMaterial.kt        Material3 实现
    │   │   ├── DirtyFragMiuix.kt           Miuix 实现
    │   │   ├── DirtyFragUiState.kt         状态 + 动作契约（对齐 HomeUiState.kt:63 风格）
    │   │   ├── DirtyFragViewModel.kt       状态机 S0–S9
    │   │   └── DirtyFragRepository.kt      唯一 CLI 调用点
    │   ├── data/repository/                新增 DirtyFragRepositoryImpl（或直接放上表目录）
    │   ├── ui/navigation/                  Route 增加 DirtyFrag
    │   └── ui/screen/home/                 HomeMaterial/HomeMiuix/HomeScreen 仅改按钮与动作
    └── res/values[-zh-rCN|-zh-rTW|-zh-rHK|-ja]/strings.xml   新增 dirtyfrag_* 文案（四语言）
```

### 3.3 边界不变量（写进验收）

| 不变量 | 检查方式 |
|---|---|
| `dirtyfrag/` 不被任何 `build.gradle.kts` / `Cargo.toml` 作为 source set 或 crate 依赖 | 静态 grep（§8.1 G-1） |
| ksud/manager 源码中不出现 exploit 标识符（`IpSecManager`、`splice`、`crash_dump64`、`ostream::sentry`、`call_usermodehelper`） | 静态 grep（§8.1 G-2） |
| 引擎修改只在 `dirtyfrag/patches/`，且每个 patch 都能应用到固定 commit | `git apply --check`（§8.2） |

---

## 4. 载荷集成方案（真源码、真二进制、可核验）

### 4.1 来源与固定

- 上游：`https://github.com/mitschud/DirtyFrag`，**固定到 v1.18 tag 的具体 40 位 commit**（构建时写入 `SOURCE.txt` 的 `upstream_commit=`；本文不臆造 sha，集成者必须填真实值）。
- 8 个 ko 直接取上游 `app/src/main/jni/ko/` 提交文件（U6），**逐个记录 sha256**；不得重新编译、不得用同名假文件替代。
- 引擎 APK 用 fork 在受控环境构建（构建命令与签名要求写进 `dirtyfrag/README.md`），产物落到 `userspace/ksud/bin/dirtyfrag/arm64/dirtyfrag-locked.apk`。

### 4.2 进 APK assets 的文件

| 文件 | 用途 | 校验 |
|---|---|---|
| `dirtyfrag-locked.apk` | 独立进程运行的引擎本体 | sha256 + ZIP 完整性 + 签名证书指纹固定 |
| `ko/*.ko`（8 个） | 按 KMI 选用 | sha256 + ELF 魔数 + `e_machine=183(AArch64)` + 内嵌 KMI 字符串 |
| `VERSION` | 版本戳，触发重解压 | 内容等于 `1.18-musu` |
| `LICENSE` / `NOTICE` / `SOURCE.txt` | 合规随附（C3/C4） | 必须存在，缺失即拒绝 stage（code 2104） |
| `SHA256SUMS` | 上面所有文件的哈希清单 | stage 时逐行核验 |

嵌入方式：仿 `BuiltinPayload` 建**独立** `DirtyfragPayload`（`#[folder = "bin/dirtyfrag"]`）。绝不能进主 `Asset`——主 `ensure_binaries()` 会跳过 `*.ko`（F13），且会把 APK 平铺到 `/data/adb/ksu/bin/`，破坏独立载荷边界。

### 4.3 合规结构（C3/C4 落地）

- `dirtyfrag/LICENSE`：GPL-2.0 原文逐字复制，**不得改动一个字符**。
- `dirtyfrag/NOTICE`：上游版权声明原文 + `Modifications Copyright (C) 2026 MuSu Next` + 修改清单（与 patches 一一对应）。
- `dirtyfrag/SOURCE.txt`（F14 格式）：
  ```
  upstream=DirtyFrag
  upstream_url=https://github.com/mitschud/DirtyFrag
  upstream_tag=v1.18
  upstream_commit=<40 位真实 sha>
  upstream_date=2026-11-17
  license=GPL-2.0-only
  note=Fork: added org.musu.next manager, locked mode (no chooser), launcher category removed. Modifications under GPL-2.0-only; see patches/ and NOTICE.
  ```
- `dirtyfrag/README.md` 必写六项：① 上游 URL + fork commit + fork 日期；② 修改清单（对应 patches）；③ 许可证 GPL-2.0-or-**only**，LICENSE/NOTICE 相对路径；④ **兼容性声明**，措辞同时给出上游原文（U2）与我们的结论：
  > DirtyFrag is GPL-2.0-only. MuSu Next userspace is GPL-3.0-or-later; they are distributed as separate programs (mere aggregation) with no code copied, translated or linked across the boundary. See `docs/specs/dirtyfrag.md` §2.
  ⑤ 构建命令 + 8 个 ko 与 APK 的 SHA256 清单；⑥ "锁定模式"如何复现（intent extra 名）。
- 仓库 `THIRD_PARTY.md` 增加 DirtyFrag 条目：项目名 / URL / commit / 许可证 / 分发文件清单 + 每个 sha256 / 修改源码位置（`dirtyfrag/`）。
- **结论**：因为我们有修改（C4 触发），合规成本从"指向上游 commit"升级为"自备修改源码 + patch"。这是锁定的必然代价，须让主理人知悉。

### 4.4 真实性校验（fail-closed）

```
stage(payload):
  1. DirtyfragPayload::get("VERSION") 存在且 == "1.18-musu"       否则 2104
  2. 逐行读 SHA256SUMS，对每个目标文件计算 sha256 比对            否则 2103
  3. 每个 ko：头 4 字节 == \x7fELF 且 e_machine == 183             否则 2103
  4. APK：ZIP 可打开且签名证书指纹 == 固定值                       否则 2103
  5. 解到 0700 私有随机目录（O_EXCL），成功后再 rename 到目标       否则 2000
```
- 配置/载荷错误**加载时大声失败**（错误码 + 期望值 + 实际值），不静默跳过（runtime-defensive §二铁律）。
- 不把可预测路径交给不可信输出；删除可能符号链接的路径先 `lstat` 再 `unlink`，不递归删除（runtime-defensive §第一部分 6）。

---

## 5. CLI 契约（关键）

### 5.1 命令集

```
ksud dirtyfrag detect      # 只探测兼容性，不改状态；stdin 一个 JSON
ksud dirtyfrag run         # 执行一键越狱（含 stage）；stdin 一个 JSON（确认令牌）
ksud dirtyfrag status      # 当前 boot 的暴露态与上次结果；stdin 一个 JSON
ksud dirtyfrag stage       # [hide=true] 内部：仅暂存载荷 + libksud.so，供 run 调用
```

约束：
- **stdin/stdout 单 JSON**：每条命令从 stdin 读**一个** JSON 对象（无内容按 `{}`），stdout 只打印**一个** JSON 对象；日志走 `logd`（复用 `jsoncmd::emit`，F11）。
- stdin JSON 是边界输入，必须校验（runtime-defensive §二：用户输入解析）：未知字段忽略，缺必填字段返回 2002。
- 退出码与 JSON 并行：`0` 成功；`1` 内部错误；`2` 忙/已 rooted；`3` 环境不支持。UI 只读 JSON 里的 `error.code`，退出码给脚本用。

### 5.2 `ok` / `error` / `exposed` 的精确语义（避免误读）

| 字段 | 语义 | 反例（不得这样用） |
|---|---|---|
| `ok` | **命令本身是否可信地执行完成**。true 表示"我拿到了可信结果"，**不代表"设备已 root"** | 不得把"探测到不兼容"写成 `ok:false` |
| `error` | 仅当命令无法产出可信结果时非 null，结构 `{code,message,retryable}`。兼容性阻断放 `supported:false` + `reasons`，**不放 error** | 不得 `error:"not supported"` |
| `exposed` | 当前 boot **是否已实际暴露 root**（dfroot.ko 已加载且 su 可达，经独立探针验证）。仅在本 boot 有效（重启即 false） | 不得把"预计会成功"写成 exposed:true |

### 5.3 `detect` 输出（UI 唯一兼容性数据源）

```json
{
  "ok": true, "schema": 1, "error": null,
  "exposed": false,
  "supported": false,
  "verdict": "unsupported",
  "device": {
    "kmi": "android14-6.1",
    "kernel_release": "6.1.157-android14-11-o-xxxx",
    "android_release": "14",
    "abi": "arm64-v8a",
    "selinux": "enforcing"
  },
  "match": {
    "payload": null, "matched_by": null,
    "payload_sha256": null, "verified": false
  },
  "reasons": [
    { "code": "KMI_NO_PAYLOAD", "severity": "blocker",
      "message": "内核 android14-6.1 有对应 ko，但设备回移了官方修复，实测不可用" }
  ],
  "risks": ["root_cleared_on_reboot", "setenforce_permissive", "kernel_conflict_on_repeat"],
  "payload": { "version": "1.18", "license": "GPL-2.0-only", "source": "dirtyfrag/SOURCE.txt" },
  "engine": { "package": "df.root", "mode": "locked", "manager": "org.musu.next" }
}
```

- `verdict ∈ {ready, unsupported, already_rooted, unknown}`；判定顺序：`exposed → already_rooted`；`!supported → unsupported`；`supported && verified → ready`；否则 `unknown`。
- `reasons[].severity ∈ {blocker, warning}`；`KMI_UNKNOWN / KMI_NO_PAYLOAD / ABI_UNSUPPORTED / PAYLOAD_VERIFY_FAILED` 为 blocker，`VENDOR_PATCH_SUSPECTED / SELINUX_ENFORCING` 为 warning。
- `android_release` 取 `ro.vendor.build.version.release`（U7）；`kmi` 取 `uname` 的 `androidNN` token；**无对应 ko 行返回 KMI_NO_PAYLOAD，禁止猜测**（U7）。

### 5.4 `run` 输入 / 输出

stdin（边界校验：必须 `confirm==true`）：
```json
{ "confirm": true, "client_request_id": "8f14e45f" }
```
stdout：
```json
{
  "ok": true, "schema": 1, "error": null,
  "exposed": true,
  "state": "succeeded",
  "request_id": "8f14e45f",
  "elapsed_ms": 68234,
  "used": { "kmi": "android14-6.1", "ko": "dfroot-android14-6.1.ko",
            "manager": "org.musu.next", "libksud_sha256": "<sha256>" },
  "stages": [
    { "name": "detect", "ok": true },
    { "name": "stage_ksud", "ok": true },
    { "name": "engine_exec", "ok": true },
    { "name": "verify_exposed", "ok": true }
  ],
  "next": "no_reboot_needed"
}
```
- `state ∈ {running, succeeded, failed, timeout}`；`running` 用于二次点击返回（见 §5.6）。
- **幂等/去重**：同一 boot 内以 `client_request_id` + 锁文件去重（runtime-defensive §第一部分 3：自己定义区间）；70s 窗口内重复调用返回 `2201 BUSY`，**绝不重复执行引擎**（U9 内核冲突）。
- 成功后记 `active_boot = boot_id`；`status` 只在该 boot 内报 `exposed:true`。

### 5.5 `status` 输出

```json
{
  "ok": true, "schema": 1, "error": null,
  "exposed": true, "state": "succeeded",
  "boot_id": "<boot_id>", "active_boot": "<boot_id>",
  "last_attempt": { "request_id": "8f14e45f", "result": "succeeded", "code": 0,
                    "started_ms": 1700000000000, "finished_ms": 1700000068234 },
  "next": "no_reboot_needed", "risk_note": "root_cleared_on_reboot"
}
```
- 状态文件损坏 → 备份 + 重置为默认（`state=idle, exposed=false`），不猜（对齐 builtin-components §3.2）。

### 5.6 错误码表（UI 错误码直接显示该数）

| code | 常量 | 触发 | UI 落点 |
|---|---|---|---|
| 0 | `OK` | 成功 | — |
| 2000 | `IO_ERROR` | 读写/解压/rename 失败 | 失败卡 |
| 2001 | `STATE_CORRUPT` | 状态文件损坏已恢复 | 静默 + 日志 |
| 2002 | `BAD_REQUEST` | stdin JSON 非法 / 缺 confirm | 失败卡（内部） |
| 2003 | `UNSUPPORTED_PLATFORM` | 非 unix/host stub | 不支持态 |
| 2100 | `UNSUPPORTED_ARCH` | 非 arm64（无载荷） | 不支持态 |
| 2101 | `KMI_UNKNOWN` | uname 无 androidNN token | 不支持态 |
| 2102 | `KMI_NO_PAYLOAD` | 无对应 ko 行 | 不支持态 |
| 2103 | `PAYLOAD_VERIFY_FAILED` | sha256/ELF/签名不符 | 失败卡（安全事件） |
| 2104 | `PAYLOAD_MISSING` | 载荷/合规文件缺失 | 失败卡 |
| 2105 | `SELINUX_BLOCKED` | 前置条件不满足 | 风险说明 |
| 2200 | `ALREADY_ROOTED` | 本 boot 已暴露，拒绝重跑 | 状态提示 |
| 2201 | `BUSY` | 70s 窗口内重复调用 | 防连点提示 |
| 2202 | `ENGINE_MISSING` | 引擎 APK 未安装/未暂存 | 失败卡 |
| 2203 | `ENGINE_LAUNCH_FAILED` | 启动引擎失败 | 失败卡 |
| 2204 | `STAGE_KSUD_FAILED` | libksud.so 缺失/哈希不符 | 失败卡 |
| 2300 | `EXPLOIT_FAILED` | 引擎回报 patching failed | 失败卡（告知设备不在支持范围，勿重试） |
| 2301 | `TIMEOUT` | 超过 70s + 余量仍未结束 | 失败卡（提示勿重启、勿连点） |
| 2302 | `BOOTSTRAP_FAILED` | su daemon 启动失败（ksud exited error） | 失败卡 |
| 2400 | `MANAGER_MISMATCH` | 锁定管理器 ≠ org.musu.next | 失败卡（构建错误，非用户问题） |

### 5.7 cfg 隔离：保证 Android / Windows 交叉编译（0.0.7 坑的正面回答）

需求写的是 `cfg(unix)/cfg(not(unix))`，但**只用这一对是不够的**：Android 是 unix，macOS/Linux 也是 unix，却都没有 Android 的 property/`__system_property_get`；0.0.7 的交叉编译失败正是"Unix-only API 泄漏到非目标编译单元"。正确做法是**外层按仓库惯例 `cfg(target_os="android")` 门控模块，内层在 syscall 边界用 `cfg(unix)/cfg(not(unix))` 成对提供 stub**：

```rust
// dirtyfrag/mod.rs —— facade，全平台编译
mod error;                                   // 纯常量与类型，无 Unix-only API
#[cfg(all(unix, target_os = "android"))]
mod detect; mod payload; mod run; mod state; mod report;   // 真实现
#[cfg(not(all(unix, target_os = "android")))]
mod stub;                                    // 同形状 JSON，返回 ok=false/code=2003

// 真实现内部，凡用 Unix-only API（flock/fork/exec/std::os::unix）处：
#[cfg(unix)]    fn flock_lock(...) {...}
#[cfg(not(unix))] fn flock_lock(...) -> ... { /* unreachable on target */ }
// Android property：
#[cfg(target_os = "android")]      fn vendor_release() -> String { /* __system_property_get */ }
#[cfg(all(unix, not(target_os = "android")))] fn vendor_release() -> String { /* 读测试 fixture */ }
```

- `cli.rs` 与 `cli_non_android.rs` **都**加 `Dirtyfrag` 命令分支（F10），因此 `cargo check`（host，含 Windows `not(unix)`）与 `cargo ndk -t arm64-v8a check` 都能过；host 上执行 `dirtyfrag detect` 返回 code 2003 而非 panic。
- **绝不允许**在 `mod.rs` / `error.rs` / `report.rs` 顶层出现 `std::os::unix::*`。这是门禁 G-4 的扫描目标（§8.1）。

---

## 6. 锁定 MuSu Next 的技术路径

### 6.1 fork 必须改的三处（对应 patches/）

| patch | 改动 | 理由 |
|---|---|---|
| 0001 | SU Manager 白名单加入 `org.musu.next`，`libksud.so` 来源指向本管理器 | U5：不在白名单则被拒；U4：stage 依赖管理器自带库 |
| 0002 | 增加"锁定模式"：接受 intent extra `org.musu.next.dirtyfrag.LOCKED_MANAGER=org.musu.next`，命中则跳过选择器 UI、固定管理器 | 用户决策 3：不显示管理器选择 |
| 0003 | 移除 LAUNCHER category（引擎无独立图标），提供可由 MuSu Next 显式组件名拉起的入口 | 用户决策 1：无独立 APP 图标；C1：独立进程 |

### 6.2 stage 与 bootstrap 闭环

1. MuSu Next 自带 `libksud.so`（F15）。`ksud dirtyfrag stage` 把自身 `libksud.so` 与载荷解到私有目录，记录 sha256。
2. `run` 以锁定模式拉起引擎 APK；引擎按 U4 从"所选管理器"（= `org.musu.next`）暂存 `libksud.so`——**自给自足，不依赖任何第三方管理器安装**。
3. 引擎执行链（U8），最后 `call_usermodehelper` 启动 `org.musu.next` 的 SU daemon（即 MuSu Next 的 ksud late-load 路径）。
4. `verify_exposed` 用独立探针确认 dfroot.ko 已加载且 su 可达，写 `active_boot`。
5. 构建期常量 `LOCKED_MANAGER` 必须为 `org.musu.next`；运行期校验，不符返回 2400（防错包发布）。

### 6.3 不做

- 不做管理器选择 UI（决策 3）；不做多管理器回退；不接管 MagicaService / late-load 既有逻辑（F6）。

---

## 7. UI 数据流（原生 Compose，双主题，四语言）

### 7.1 入口改造（最小改动）

- `HomeMaterial.kt:235-246` / `HomeMiuix.kt:389-392`：按钮条件由 `notInstalled && isSELinuxPermissive` 改为**常驻显示**（不受安装态 / SELinux 态限制）。
- 点击动作由 `MagicaService` 改为 `navigator.push(Route.DirtyFrag)`（`HomeScreen.kt:67-78` 的 `onJailbreakClick` 重指向；保留 MagicaService 供开机路径 F6 使用）。
- `Route` 新增 `DirtyFrag`；`LocalUiMode` 分流沿用 F8。

### 7.2 状态机（Repository 只认 §5 JSON）

| ID | 状态 | 触发 | 按钮/文案 |
|---|---|---|---|
| S0 | `Idle` | 首帧 | 显示"检测兼容性…"骨架；按钮禁用 |
| S1 | `Probing` | 调 detect（≤2s 超时转 S9） | 骨架，不显示进度条 |
| S2 | `Ready` | `verdict=ready` | 显示 KMI/内核/风险清单 +「一键越狱」可点 |
| S3 | `Unsupported` | `verdict=unsupported` | 按钮灰 + 展示 `reasons[].message` + 可执行下一步（换内核/等更新） |
| S4 | `ConfirmRisk` | 用户点一键 | 风险确认卡：重启即失效 / setenforce permissive / 70s 内勿重复点击勿重启 |
| S5 | `Running` | 调 run，70s 窗口 | 按钮禁用 + 阶段文字（detect/stage/exec/verify）；**不做百分比**（U9 进度条映射会误导） |
| S6 | `Succeeded` | `exposed=true` | 就地显示"已获得临时 root，重启后失效" |
| S7 | `Failed` | `ok=false` 或 `state=failed` | 错误码 + 具体原因；2300 明确"设备不在支持范围，勿反复重试" |
| S8 | `AlreadyRooted` | detect 即 `exposed=true` | 短路，按钮显示"本次启动已越狱" |
| S9 | `ReadError/Busy` | 读失败 / 2201 / 2200 | 「重试」；Busy 时"正在运行，请勿重复点击" |

- **防连点**：点击后 `enabled=false` 直到下层返回或 90s 超时；不做乐观更新（会读成"开了又坏了"，对齐 builtin-ui §9.5）。
- **反馈就地**：状态卡在同一页更新，不用 Toast/Snackbar；失败不弹全局提示。
- **风险确认必须有**：与 builtin 开关不同（builtin-ui §9.6 无确认框），本功能会改内核态且不可在当前 boot 撤销，S4 确认卡不可省。

### 7.3 双主题映射（复用既有组件，零新颜色/圆角/间距）

| 区域 | Material3 | Miuix |
|---|---|---|
| 页面骨架 | `ExpressiveScaffold` + `LargeFlexibleTopAppBar` | `Scaffold` + `BlurredBar{TopAppBar}` |
| 屏边距 | 16dp | 12dp |
| 分组容器 | `SegmentedColumn` | `Card` |
| 风险/失败卡 | `TonalCard(errorContainer)` | `WarningCard(Error)` |
| 状态标签 | `StatusTag`（4dp） | `StatusTagMiuix`（6dp） |
| 一键按钮 | `Button(error colors)`（沿用现有越狱按钮配色） | 同语义 Miuix 按钮 |
| 重启动作 | 复用 `RebootListPopup`（F16 之外的既有浮层） | 同 |
| 图标 | `Icons.Filled.*`（如 `Bolt` / `Warning`），24dp | `Icons.Rounded.*` 同语义 |

状态、颜色 token、文案、顺序、判定两主题**零差异**；只容器与屏边距不同（对齐 builtin-ui §7 的原则）。

### 7.4 四语言（values / zh-rCN / zh-rTW / zh-rHK / ja）

- 键名前缀 `dirtyfrag_`，覆盖：入口按钮、KMI/内核/状态行、风险清单三项、S0–S9 文案、错误码后缀、许可证来源行。
- **四语言 = en / 简体(zh-rCN) / 繁体(zh-rTW、zh-rHK) / ja**；zh-rHK 与 zh-rTW 内容一致但都要落文件（F9）。
- 文案纪律：不写"敬请期待/功能开发中/暂无数据"；失败必须带错误码或具体原因；占位符取不到就隐藏整行，不显示"未知/-"（对齐 builtin-ui §10.4）。
- 术语：对外一律写"一键越狱 / DirtyFrag"，首次出现可注"临时 root，重启失效"；不暴露 IpSec/splice/overlayfs 等实现黑话。

---

## 8. 成功标准 + 端到端验证步骤

### 8.1 静态门（L1，秒级，每次改动后跑）

在仓库根执行：
```bash
cd /workspace/MuSuNext
# G-1: dirtyfrag/ 不进任何编译单元
grep -RIn "dirtyfrag" manager/app/build.gradle.kts manager/build.gradle.kts Cargo.toml userspace/ksud/Cargo.toml || echo "PASS: not a source set"
# G-2: 编排层零移植漏洞链标识符（应只在 dirtyfrag/ 下命中）
grep -RInE "IpSecManager|crash_dump64|ostream::sentry|call_usermodehelper|splicehelper" userspace/ksud/src manager/app/src/main/java || echo "PASS: no exploit code in orchestration"
# G-3: 无 emoji 作功能图标（项目 P0）
python3 scripts/gates/gates.py G01 2>/dev/null || echo "gate script not installed; manual scan required"
# G-4: mod.rs/error.rs/report.rs 顶层无 Unix-only API
grep -nE "std::os::unix|libc::" userspace/ksud/src/dirtyfrag/mod.rs userspace/ksud/src/dirtyfrag/error.rs userspace/ksud/src/dirtyfrag/report.rs || echo "PASS: facade host-safe"
```

### 8.2 载荷真实性门（L1）

```bash
cd /workspace/MuSuNext/userspace/ksud/bin/dirtyfrag/arm64
sha256sum -c SHA256SUMS                       # 全部 OK
test "$(ls ko/*.ko | wc -l)" -eq 8            # 恰好 8 个
for f in ko/*.ko; do readelf -h "$f" | grep -q 'Machine: AArch64' || exit 1; done
test "$(cat VERSION)" = "1.18-musu"
grep -q '^license=GPL-2.0-only$' SOURCE.txt
cd /workspace/MuSuNext && git apply --check dirtyfrag/patches/0001-add-musu-manager.patch
```

### 8.3 编译门（L2，按 AGENTS.md 的规定顺序）

```bash
cd /workspace/MuSuNext
cargo ndk -t arm64-v8a check -p ksud
cargo ndk -t arm64-v8a clippy -p ksud
cargo fmt --check
cargo check                       # host：验证 stub 路径（含非 unix 分支）
# Windows 交叉编译目标（若工具链可用）：
rustup target add x86_64-pc-windows-gnu && cargo check --target x86_64-pc-windows-gnu -p ksud
cd manager && ./gradlew clean assembleRelease   # 需先按 AGENTS.md:56-60 备好 libksud.so
```
成功判据：全部 exit 0；host 上 `ksud dirtyfrag detect < /dev/null` 返回 JSON 且 `error.code=2003`（stub 正确兜底），不 panic。

### 8.4 CLI 契约门（L2）

```bash
# 不支持路径（host 或不支持设备）：detect 返回 ok=true, supported=false, 无 error
echo '{}' | ksud dirtyfrag detect | jq -e '.ok == true and (.error == null) and (.reasons|length>0)'
# 缺 confirm 被边界拒绝
echo '{"confirm":false}' | ksud dirtyfrag run | jq -e '.ok == false and .error.code == 2002'
# 幂等：同一 request_id 二次 run 返回 2201（模拟，利用状态文件）
echo '{"confirm":true,"client_request_id":"t1"}' | ksud dirtyfrag run | jq '.state'
echo '{"confirm":true,"client_request_id":"t1"}' | ksud dirtyfrag run | jq -e '.error.code == 2201'
# 锁定管理器校验
jq -e '.engine.manager == "org.musu.next"' <(echo '{}' | ksud dirtyfrag detect)
```

### 8.5 设备端到端（L3，需一台在支持范围内的真机）

```
1. 安装 0.0.8 管理器（含锁定版引擎载荷），确认桌面无 DirtyFrag 独立图标。
2. 打开主页：越狱按钮常驻（无论 notInstalled / SELinux 状态）。
3. 点击进入 DirtyFrag 页 → 自动 detect → 显示真实 KMI/内核/风险清单。
4. 不支持设备：按钮灰 + reasons 文案；确认绝不出现"猜测匹配"（U7）。
5. 支持设备：点一键 → 风险确认卡 → run；70s 内重复点击被拦截（无第二次执行日志）。
6. 成功：status exposed=true，su 可达；记录 active_boot。
7. 重启设备 → status exposed=false（root 清除）；再次一键可重跑。
8. 失败路径：人为损坏 SHA256SUMS → stage 返回 2103，UI 显示失败卡，引擎不启动。
9. 双主题各走一遍步骤 3–6；四语言各截图核对文案键完整。
```
成功判据：核心成功流（3→4→5→6）+ 两条关键错误流（不支持 / 载荷校验失败 / 防连点）全部符合；无 avc/内核 panic；回归率为零。

---

## 9. 已知坑与风险表

| # | 风险 | 机理 / 证据 | 处置 | 剩余风险 |
|---|---|---|---|---|
| R1 | 70s 重复点击导致内核冲突/卡死 | U9；两次引擎运行争用同一内核补丁 | 锁文件 + request_id 去重，窗口内返回 2201；按钮禁用到返回（§5.4/§7.2） | 低 |
| R2 | KMI 误配 bricking | U7 无对应行必须拒绝；厂商回移修复（U9 FAQ）使"版本号匹配"仍失败 | KMI 只做初筛 + 载荷 sha256/ELF 校验；不猜测；失败明确"设备不在支持范围，勿重试" | **中**：仍需真机验证各 KMI |
| R3 | SELinux permissive 安全含义 | U8 ko 会 `setenforce permissive`，设备在整个 boot 期间处于宽松态 | S4 风险确认卡显式告知；status 常驻 `risk_note`（§7.2） | 中：这是功能固有代价，须用户知情 |
| R4 | 交叉编译失败（0.0.7 坑） | Unix-only API 泄漏到 host / Windows 编译单元 | §5.7 双层 cfg + 双 CLI 分支 + G-4 门禁 + L2 host check | 低 |
| R5 | GPL 合规破裂 | C1–C5 任一违反；尤其编排层"翻译"漏洞链（U2） | §2.3 五条件 + §8.1 G-1/G-2 静态门 + 评审 | **中高**：需法务/主理人确认 C4 的源码提供范围 |
| R6 | fork 修改触发源码提供义务 | C4：有修改就不能只指向上游 | `dirtyfrag/patches/` + `NOTICE` + `THIRD_PARTY.md`（§4.3） | 中 |
| R7 | 重启即失效被误读为失败 | U9；用户重启后看不到 root | UI 常驻"临时 root，重启失效"；status 用 active_boot 判定（§5.5） | 低 |
| R8 | 重装换 UID 需重授权 | U11 | 文档说明；不做静默重授权 | 低 |
| R9 | 进度条误导 | U9：0% 是日志映射非失败 | UI 不显示百分比，只显示阶段文字（§7.2 S5） | 低 |
| R10 | 引擎 APK 被替换/劫持 | 独立 APK 是攻击面 | 签名证书指纹固定 + sha256 + 私有 0700 暂存（§4.4） | 低 |
| R11 | 授权/SU daemon 启动失败 | U9 FAQ："SUCCESS 但无 root"多因 auto soft reboot | 本版不接 autorun/auto soft reboot；BOOTSTRAP_FAILED 给明确下一步（§5.6 2302） | 低 |

---

## 10. 明确不做（out-of-scope，杜绝范围蔓延）

- 不在 MuSu Next 代码实现 CVE-2026-43284 漏洞链的任何一环（C5）。
- 不打包 riscv64 / x86_64 的 DirtyFrag 载荷（0.0.8 只做 arm64）。
- 不接引擎的 Autorun / 自动软重启 / Odin 写 / KernelSU 模块开关（U10）；仅跑一次性链。
- 不做管理器选择 UI 与多管理器回退（决策 3）。
- 不改 MagicaService / late-load 既有逻辑（F6），只重定向按钮。
- 不做 WebView / WebUI（用户要求原生 Compose）。
- 不做 root 持久化（重启即失效是设计目标，U9）。
- 本文不改版本号文件、不改现有 Home 之外的文件、不写实现代码。

---

## 11. 验收清单（逐条对账）

- [ ] §2 裁决被主理人确认；5 条硬条件 C1–C5 逐条有落地证据（§8.1 G-1/G-2 通过）
- [ ] `dirtyfrag/` 为独立 GPL-2.0-only 源码树：LICENSE 原文、NOTICE、SOURCE.txt（真实 commit）、patches、README 六项齐全
- [ ] `THIRD_PARTY.md` 有 DirtyFrag 条目 + 每文件 sha256 + 修改源码位置
- [ ] 8 个 ko 为真文件：sha256/ELF/AArch64/KMI 全过（§8.2）
- [ ] 载荷用独立 `DirtyfragPayload`（`bin/dirtyfrag`），未进主 `Asset`（F13）
- [ ] CLI 三命令 + stage，stdin/stdout 单 JSON；`ok/error/exposed` 语义与 §5.2 一致；错误码表实现完整
- [ ] `mod.rs/error.rs/report.rs` 无 Unix-only API；`cli.rs` 与 `cli_non_android.rs` 均有 Dirtyfrag 分支；host 与 Android 双编译过（§8.3）
- [ ] 锁定模式三 patch 可应用；`LOCKED_MANAGER=org.musu.next` 运行期校验（2400）
- [ ] Home 越狱按钮常驻 + 跳转 DirtyFrag 页；MagicaService 保留供开机路径
- [ ] UI 状态机 S0–S9 完整；S4 风险确认不可跳过；防连点生效；无百分比进度
- [ ] 双主题状态/文案/顺序一致；四语言（en/zh-CN/zh-TW/zh-HK/ja）文案键齐全
- [ ] 设备 E2E：成功流 + 不支持流 + 载荷校验失败流 + 防连点流全过（§8.5）
- [ ] 全项目 P0：无 emoji 功能图标、无紫粉渐变、无占位文案、图标只用锁定库

### 11.1 待主理人确认（不自作主张）

| # | 决策 | 候选 | 代价 |
|---|---|---|---|
| Q1 | C4 的源码提供范围：随 APK 附带 `dirtyfrag/upstream/` 全量快照，还是提供 patch + 指向上游 commit | A) 全量快照（本文默认） B) patch + 上游链接 | A：仓库体积增大、同步成本高。B：合规性依赖上游 commit 长期可访问，若上游删库则源码提供义务落空 |
| Q2 | 引擎作为"无图标独立 APK"安装 vs 载荷 dex 由管理器拉起 | A) 无图标独立 APK（本文默认，C1 最强） B) 同进程 dex | B 违反 C1，法律与工程均不推荐；列出仅为对照 |
| Q3 | 是否在 0.0.8 随附"设备支持清单"（真机验证过的 KMI 列表） | A) 附，并在 detect 里给 warning（本文倾向） B) 不附，仅按 KMI 初筛 | A：需真机投入；B：R2 bricking 剩余风险维持中等 |
