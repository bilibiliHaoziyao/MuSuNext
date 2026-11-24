# 内建组件技术规格：MuZygisk 与内建元模块

> 版本：0.0.6 设计基线（v1）
> 范围：目录布局、状态存储、CLI 契约、开机编排、互斥判定、SELinux、运行时语义、许可证落地。
> **不含**：Compose 实现、C/ Rust 具体补丁代码、改仓库源码的提交。
> 配套文档：`docs/specs/builtin-ui.md`（界面契约）。本文负责"这些界面字段的事实来源"，两者冲突以本文的代码事实为准，冲突点登记在 §14。
> 术语纪律：全文无 emoji；图标库锁定为 `androidx.compose.material:material-icons-extended`（管理器已在 `manager/gradle/libs.versions.toml:43` 引入），新增图标只允许 `Icons.Filled/ Rounded.Memory`、`Layers`，不引入第二套图标资源。

---

## 0. 结论摘要

| # | 结论 | 一句话理由 |
|---|---|---|
| 1 | 两个组件都不放进 `/data/adb/modules`，统一落 `/data/adb/musu/` | `list_modules()` 只扫 `/data/adb/modules` 且要求存在 `module.prop`（`module.rs:1069-1071`），目录不进去就天然不出现在列表，不依赖任何 UI 过滤 |
| 2 | 内建元模块**不改 ksud 编排代码**，通过 `/data/adb/metamodule` 软链复用现有 `metamodule::*` 五条调用路径 | 元模块的全部钩子都走 `get_metamodule_path()`（`metamodule.rs:28`），它只要求"软链指向一个存在的目录" |
| 3 | 状态唯一真源是 `/data/adb/musu/state.json`，**不使用 `disable` 文件** | 双真源必然分叉；元模块的生效态由软链存在性表达，与既有代码路径一致 |
| 4 | 启用/禁用一律需要重启，且 `pending_reboot` 分 `full` / `soft` 两档 | monitor 只在 post-fs-data 启动（`init_event.rs` 无其它时机），软重启不重跑该阶段 |
| 5 | 内建元模块零源码改动即可，MuZygisk 需要 10 条 fork 改动（§13，其中 7 条必改） | ReZygisk 有 10 处编译期硬编码路径指向 `/data/adb/modules/rezygisk` 与 `/data/adb/rezygisk` |

**风险前置**：最危险的一条是 §2.3 的载荷分发方式（ksud 体积 + 许可边界），第二条是 §6.2 ③ 的"安装即启用"绕过互斥，第三条是 §7 的 SELinux socket 访问未经实机验证。

---

## 1. 现状基线（读代码得到的事实，后文全部引用这些行号）

| # | 事实 | 出处 |
|---|---|---|
| F1 | 模块列表只读 `/data/adb/modules`，且 `module.prop` 不存在则整条跳过 | `module.rs:1149-1151`（`list_modules()` → `list_module(defs::MODULE_DIR)`）、`module.rs:1069-1071` |
| F2 | 模块阶段脚本、sepolicy、system.prop 的遍历入口只有 `foreach_module()` → 只扫 `MODULE_DIR` / `MODULE_UPDATE_DIR` | `module.rs:132-160`、`module.rs:167-180` |
| F3 | `load_sepolicy_rule()` 只处理 `/data/adb/modules/*/sepolicy.rule`，内建目录不在遍历集内 | `module.rs:167-180` |
| F4 | 元模块路径解析：优先读 `/data/adb/metamodule` 软链并解析目标，目标存在且是目录即返回；否则回落到扫 `module.prop` 的 `metamodule=1` | `metamodule.rs:28-67` |
| F5 | 元模块有 5 条使用路径：`exec_stage_script`（各 boot 阶段）、`exec_mount_script`（metamount）、`get_install_script`（metainstall）、`exec_metauninstall_script`、`check_install_safety` | `metamodule.rs:207-288`、`module.rs:103`、`module.rs:726`、`module.rs:832` |
| F6 | `check_metamodule_script()` 遇到元模块目录下有 `disable` 文件即跳过；无 `metamount.sh` 亦跳过 | `metamodule.rs:207-224` |
| F7 | `post-fs-data` 编排固定顺序：清临时配置 → Magisk 检测 → 公共脚本 → 释放二进制 → safe mode 分支 → 处理更新模块 → prune → regenerate_preinit → restorecon → **加载模块 sepolicy** → profile sepolicy → feature 初始化 → **元模块 post-fs-data** → **模块 post-fs-data** → system.prop → **元模块 metamount** → post-mount 阶段 | `init_event.rs:11-125` |
| F8 | `has_magisk()` 为真时 `on_post_fs_data` 直接 return；safe mode 时禁用全部模块并 return（两者都在元模块钩子之前） | `init_event.rs:29-32`、`init_event.rs:55-61` |
| F9 | `ksud` 用 `rust-embed` 按架构嵌 `bin/<arch>` 目录，`ensure_binaries()` 把除 `ksuinit`/`waitsys`/`*.ko` 外的每个 asset 平铺写到 `/data/adb/ksu/bin/`，`ignore_if_exist=true` 时已存在就跳过 | `assets.rs:15-37`、`assets.rs:39-58`、`utils.rs:89-114` |
| F10 | 内核超调用为 ioctl 接口：fd 由 `SYS_reboot(KSU_INSTALL_MAGIC1, KSU_INSTALL_MAGIC2, 0, &fd)` 取回，**无 prctl 命令号通道** | `kernel/preinit/runner/runner.c:340`、`uapi/supercall.h:17-18`、`kernel/supercall/dispatch.c:809+` |
| F11 | `kernel/`、`userspace/`、`manager/` 内 overlayfs 挂载实现零命中（`lowerdir`/`upperdir`/`overlayfs` 全局 grep 为空），即模块文件挂载完全由元模块承担 | 见 §15 Q-1（此项与需求描述不一致，需确认） |
| F12 | 管理器侧 `execKsud()` 只返回 `Boolean`，没有取 stdout 的封装 | `KsuCli.kt:110-117` |
| F13 | 设置页现有一套功能开关的既有映射方式（`onSetKernelUmountEnabled` 等） | `SettingsScreen.kt:64-75` |

**上游事实**（读 `/tmp/upstream` 源码得到）：

| # | 事实 | 出处 |
|---|---|---|
| U1 | ReZygisk 编译期硬编码路径共 10 处：`/data/adb/rezygisk` 4 处、`/data/adb/modules/rezygisk` 6 处；含 `rezygisk` 的标识符/字符串合计 103 处 | `zygiskd/src/zygiskd.c:41-46`、`loader/src/include/daemon.h:44`、`loader/src/ptracer/monitor.c:847,888,961,972`、`loader/src/injector/entry.c:18`、`loader/src/injector/hook.c:545`、`loader/src/ptracer/ptracer.c:184` |
| U2 | monitor 以**相对路径** exec daemon 与 ptracer（`./bin/zygiskd64`、`./bin/zygisk-ptrace64`），依赖启动时的 cwd 等于模块目录 | `loader/src/ptracer/monitor.c:456-460`、`monitor.c:499-520` |
| U3 | 32/64 双 ABI 各自独立：`status32.supported` 在真正 attach 时置真，daemon 退出只影响单 ABI | `monitor.c:443-476`、`monitor.c:479-500` |
| U4 | 机器可读状态已存在：monitor 写 `/data/adb/rezygisk/state.json`（root、monitor.state/reason、64/32 各 ABI 的 daemon 与注入状态） | `monitor.c:888-960` |
| U5 | 模块状态原本写进自身 `module.prop` 的 description（含 emoji），这是我们要替换掉的状态通道 | `monitor.c:827-880` |
| U6 | KSU root 实现层已支持 ioctl 分支，且结构体 ABI 与 MuSu 完全一致（`{u32 uid; u8 granted;}`、`'K',8`/`'K',9`/`'K',10`） | `zygiskd/src/root_impl/kernelsu.c:34-63` vs `uapi/supercall.h:92-104,195` |
| U7 | KSU 管理器包名表里只有 `me.weishu.kernelsu`、`com.rifsxd.ksunext` | `kernelsu.c:16-20` |
| U8 | Hybrid Mount 在 KSU 上安装时删除 `lkm/`（KSU 有 nuke-ext4 ioctl），VFS 内核模块仅 aarch64 保留 | `module/customize.sh:31-49`、`module/lkm/README.md` |
| U9 | Hybrid Mount 自带机器可读通道：`hybrid-mount status` 输出 `RunState`（overlay/magic 模块清单、`mount_error_modules`、`active_mounts`、`degraded`、`partition_errors`），`hybrid-mount modules` 输出模块级快照 | `src/state.rs:127-172`、`src/state.rs:670-689` |
| U10 | Hybrid Mount 自身目录/配置路径硬编码 4 处：`SELF_MODULE_DIR`、`SELF_MODULE_PROP`、`MODULE_LKM_DIR`、`BUNDLED_MODULE_BLACKLIST_PATH`，均为 `/data/adb/modules/hybrid_mount/**`；配置真源在 `/data/adb/hybrid-mount/config.toml` | `src/defs.rs:7-29` |
| U11 | 上述 4 处硬编码的实际使用点只有 3 个，且全部是 best-effort 或非 KSU 分支：description 更新（失败仅 warn）、`install-state` 自检（WebUI 用）、`lkm_compat`（非 KSU 分支） | `src/module_status.rs:22-30`、`src/state.rs:764`、`src/sys/nuke.rs:173` |
| U12 | Hybrid Mount 无 `post-fs-data.sh`；`metamount.sh` 只有一行 `exec "${0%/*}/hybrid-mount boot metamount"`，路径全相对 | `module/metamount.sh`、`module/post-mount.sh`、`module/boot-completed.sh` |
| U13 | Hybrid Mount 的 boot guard 只覆盖 LKM 与 VFS，overlay 模式无失败计数保护 | `src/defs.rs:11-23`、`src/vfs/boot_guard.rs` |

---

## 2. 目录布局

### 2.1 最终布局

```
/data/adb/musu/                          root:root 0755，adb_data_file
├── state.json                           唯一真源，0600，见 §3
├── zygisk/                              MuZygisk 载荷根（= monitor 的 cwd）
│   ├── bin/zygiskd64                    41 KB
│   ├── bin/zygisk-ptrace64              58 KB（由 libzygisk_ptrace.so 改名）
│   ├── lib64/libzygisk.so               77 KB
│   ├── lib64/libzygisk_ptrace.so        58 KB（保留原名的副本，见 §13 M4）
│   ├── sepolicy.rule                    来源 ReZygisk module/src/sepolicy.rule
│   └── .payload_version                 内容为 ksud 的 VERSION_CODE
├── meta-hybrid/                         内建元模块载荷根（= 元模块目录）
│   ├── hybrid-mount                     官方完整版二进制（arm64-v8a / x86_64）
│   ├── metamount.sh  post-mount.sh  boot-completed.sh  emulated-soft-reboot.sh
│   ├── metainstall.sh                   元模块接管模块安装的钩子
│   ├── config.toml                      默认配置模板，仅首次 seed 到 /data/adb/hybrid-mount/
│   ├── module_blacklist.toml
│   ├── module.prop                      id=musu_builtin_metamodule, metamodule=1
│   └── .payload_version
└── run/                                 运行期目录，可整体删除重建
    ├── zygisk/                          chmod 0555 + chcon system_file（同 ReZygisk TMP_PATH）
    └── meta_boot_attempts               内建元模块连续失败计数（§11.3）
```

`/data/adb/metamodule` 是软链：启用内建元模块时 → `/data/adb/musu/meta-hybrid`；禁用时删除。

### 2.2 决策：为什么这么放

| 决定 | 选了什么 | 一句话人话 | 改的代价 |
|---|---|---|---|
| D-01 位置 | 独立根 `/data/adb/musu/`，**不进** `/data/adb/modules` | 这两个东西是管理器的零件，不是你装的模块，所以它们不出现在模块列表里——不是被过滤掉的，是根本没进那个目录 | 低：目录是新增的，与现有路径零交集；`restorecon()`/`prune_modules()` 都不会碰它 |
| D-02 元模块目录名 | `meta-hybrid`（而非 `hybrid_mount`） | 名字跟官方模块 id 区分开，用户就不会以为自己装了官方模块 | 中：Hybrid 的 `metainstall.sh` 里 `KSU_METAMODULE="hybrid_mount"` 与目录名不一致，个别模块安装脚本若"比对 `/data/adb/modules/hybrid_mount` 是否存在"会得不到预期结果（见 V-6） |
| D-03 载荷来源 | 打进 ksud 的 rust-embed（**独立** `BuiltinPayload` 结构，folder `bin/builtin/<arch>`） | 开机自解压，不依赖管理器活着；升级跟着 ksud 版本走 | 中高：ksud 二进制 arm64 增大约 1.4–1.8 MB（MuZygisk 约 0.2 MB + Hybrid 官方版实测待补，见 V-1）；riscv64 无载荷 → 该架构下两个开关置灰 |

**为什么不选另外两个方案**：

| 方案 | 否决理由 |
|---|---|
| 载荷放 manager APK assets，启用时由管理器解压 | 启用动作变成"必须经过管理器"；`ksud` CLI 无法独立完成启用，破坏"ksud 是状态唯一写入者"的既有分层；且 APK 资源解压需要额外处理 SELinux 上下文 |
| 载荷随发布 zip 侧载到 `/data/adb/musu`（ksud 不带） | 无升级链路：ksud 版本更新后载荷仍是旧的，两个版本号要各自维护 |

### 2.3 载荷分发与解压算法（必须实现成幂等）

```
ensure_payload(component, arch, asset_iter):
  1. 若 /data/adb/musu/<component>/.payload_version == VERSION_CODE 且 sepolicy.rule 等关键文件存在 → 直接返回
  2. 解到 /data/adb/musu/.staging-<component>/（不覆盖在用目录）
  3. 逐文件 chmod 0755；目录 0755
  4. 写 .staging/.payload_version = VERSION_CODE
  5. rename(/data/adb/musu/.staging-<component>, /data/adb/musu/<component>)
     - 目标已存在时先 rename 到 .old-<component>，rename 成功后异步删除
  6. 失败则保留旧目录并写 state.last_error，不清空在用载荷
```

为什么"必须两步 rename"：正在被 exec 的二进制被 rename 覆盖会产生 ETXTBSY 或半写文件；`rename(2)` 对目录是原子的，旧 inode 继续对运行中进程有效。

**注入点**：`assets.rs::ensure_binaries()` 的 asset 遍历必须跳过 `bin/builtin/**`，否则所有载荷会被平铺写进 `/data/adb/ksu/bin/`（F9）。改为独立 `BuiltinPayload` embed 后此条自然成立——这是选择"独立结构"而不是"复用 Asset"的原因。

---

## 3. 状态存储

### 3.1 文件与格式

路径 `/data/adb/musu/state.json`，权限 `0600`，`root:root`。JSON（`serde_json` 已是 ksud 依赖）。

```json
{
  "schema": 1,
  "boot_id": "b1f2c3d4-....",
  "components": {
    "zygisk": {
      "enabled": false,
      "enabled_boot_id": null,
      "applied_boot_id": null,
      "payload_version": "0",
      "last_error": null,
      "feature_override": {
        "kernel_umount": { "saved": null, "applied": false }
      }
    },
    "metamodule": {
      "enabled": false,
      "enabled_boot_id": null,
      "applied_boot_id": null,
      "payload_version": "0",
      "consecutive_boot_failures": 0,
      "last_error": null
    }
  }
}
```

字段语义（这些字段直接决定 UI 的 `S3 待重启` / `S4 运行中` 判定，不允许管理器自己推导）：

| 字段 | 含义 | 取值规则 |
|---|---|---|
| `enabled` | **用户意图** | 开关点了就写，与是否生效无关 |
| `enabled_boot_id` | 意图写下时的 boot | 用于"这次开机内改过"的判断 |
| `applied_boot_id` | **最后一次成功生效**的 boot | `enabled && applied_boot_id != 当前 boot_id` ⇒ `pending_reboot` |
| `payload_version` | 解压时的 ksud `VERSION_CODE` | 与当前不一致 ⇒ 下次开机会重解压（§2.3） |
| `consecutive_boot_failures` | 内建元模块连续失败次数 | 成功一次清零；≥3 触发自动关闭（§11.3） |
| `feature_override` | MuZygisk 对 `kernel_umount` 的临时改写记录 | 见 §8.4，禁用时用于恢复用户原值 |

`boot_id` 取 `/proc/sys/kernel/random/boot_id`（内核生成，软重启不变、完整重启变）。**这正是"软重启不改变 boot_id"的事实来源**，也是 `pending_reboot` 分档的判据。

### 3.2 损坏与降级（写死，不留解释空间）

| 情形 | 行为 | 依据 |
|---|---|---|
| 文件不存在 | 使用内置默认：两组件 `enabled=false`，立即写盘 | 默认禁用是需求，也是唯一安全默认 |
| JSON 解析失败 / `schema` 不认识 | 重命名为 `state.json.corrupt.<unix_ts>`，写全新默认文件，`last_error.code=1001`，**不启用任何组件** | 损坏时"什么都不开"与默认值同向，不需要额外规则 |
| 单组件字段缺失（老版本升级来） | 按 `serde(default)` 视为 `enabled=false` | 新增字段不得破坏旧文件可读性 |
| 未知组件 id | 保留原样写回，不删 | 降级安装（新→旧）不得丢用户意图 |
| 写盘失败（磁盘满） | 返回 `code=1000`，**内存态不生效**（不写"看起来成功"的状态） | 与 D-05 一致：状态只有落盘才算数 |
| 权限/属主被改 | `chown root:root` + `chmod 0600` 后重写 | 防篡改，属最小修复 |

写入一律"tmp + `sync_all` + `rename`"，与 `feature.rs::save_binary_config` 同构（`feature.rs:94-122`）。

| 决定 | 选了什么 | 一句话人话 | 改的代价 |
|---|---|---|---|
| D-04 存 JSON 而非复用 `.feature_config` 二进制格式 | 独立 `state.json` | 内核 feature 那套是给内核用的开关，内建组件的状态是用户态文件系统对象，混进去要改内核 uapi | 低：多一个文件；好处是可以直接 cat 出来排障 |
| D-05 单一真源，不用 `disable` 文件 | 只认 `state.json` + 软链存在性 | 一个开关只有一个地方记，不会出现"文件说关着、界面说开着" | 中：`check_metamodule_script()` 的 `disable` 分支（F6）对内建元模块永不命中，语义上与"模块式元模块被禁用"不同构（§9.4） |

---

## 4. ksud CLI 契约

### 4.1 命令集

```
ksud builtin list                      # 两组件全量状态，管理器唯一读入口
ksud builtin status <id>               # 单组件详情（id: zygisk | metamodule）
ksud builtin enable <id>
ksud builtin disable <id>
ksud builtin detect <id>               # 只返回冲突探测结果，不改变状态
ksud builtin reset                     # 清载荷 + 清状态（不卸载、不动 /data/adb/hybrid-mount 用户配置）
ksud builtin apply                     # 内部：post-fs-data 编排调用，[hide = true]
```

约束：

- 全部命令 **stdout 只输出一个 JSON 对象**，人类可读信息走 `--human`（默认 JSON，因为唯一调用者是管理器）；日志走 `log::info!` → logd。
- `id` 允许中性别名：`zygisk` / `muz`（MuZygisk）、`metamodule` / `meta`。未知 id → `code=1600`。
- **退出码与错误码并行**：`0` 成功；`1` 内部错误；`2` 被互斥拦截；`3` 环境不支持；`4` 忙/超时。UI 需要的是 JSON 里的 `error.code`（整数），退出码只给脚本用。

### 4.2 `builtin list` 输出（这是 UI 的唯一数据源）

```json
{
  "schema": 1,
  "boot_id": "b1f2c3d4-...",
  "components": [
    {
      "id": "zygisk",
      "name": "MuZygisk",
      "ui_state": "off",
      "enabled": false,
      "pending_reboot": "none",
      "available": true,
      "conflict": null,
      "error": null,
      "metrics": {
        "zygote64_injected": false,
        "zygote32_injected": false,
        "zygote32_covered": false,
        "modules_loaded": 0,
        "modules_total": 0
      },
      "detail": {
        "version": "0.0.6",
        "upstream": "ReZygisk",
        "upstream_url": "https://github.com/PerformanC/ReZygisk",
        "upstream_commit": "<40 位 sha>",
        "license": "AGPL-3.0-or-later",
        "disk_bytes": 208896
      }
    },
    {
      "id": "metamodule",
      "name": "内建元模块",
      "ui_state": "blocked",
      "enabled": false,
      "pending_reboot": "none",
      "available": false,
      "conflict": {
        "kind": "metamodule",
        "id": "mountify",
        "name": "Mountify",
        "version": "1.0",
        "evidence": "/data/adb/modules/mountify/module.prop: metamodule=1"
      },
      "error": null,
      "metrics": {
        "mounted_modules": 0,
        "failed_modules": 0,
        "scope": [],
        "degraded": false
      },
      "detail": {
        "version": "6.2.6",
        "upstream": "Hybrid Mount",
        "upstream_url": "https://github.com/Hybrid-Mount/meta-hybrid_mount",
        "upstream_commit": "<40 位 sha>",
        "license": "GPL-3.0-only",
        "disk_bytes": 1519616
      }
    }
  ]
}
```

### 4.3 `ui_state` 取值与判定（ksud 算，管理器不重算）

| `ui_state` | 判定顺序（自上而下，命中即停） | 对应 UI 状态 |
|---|---|---|
| `read_error` | 状态文件不可读且无法恢复 | S9 |
| `unsupported` | `available=false` 且原因属环境（架构/内核/Magisk） | S1 |
| `blocked` | 存在 `conflict != null` | S6 |
| `failed` | `enabled && error != null`（本次开机尝试失败） | S5 |
| `active` | `enabled && applied_boot_id == boot_id && 运行证据成立` | S4（`blocking_others` 作叠加字段，见 §4.5） |
| `pending_disable` | `!enabled && applied_boot_id == boot_id` | S8 |
| `pending_enable` | `enabled && applied_boot_id != boot_id` | S3 |
| `off` | 其它 | S2 |

"运行证据"按组件不同：

- `zygisk`：monitor 进程存活（扫 `/proc/*/comm` 命中 `zygisk-ptrace64`）**且** `/data/adb/musu/run/zygisk/state.json` 可解析。
- `metamodule`：软链存在指向内建目录 **且** `/data/adb/hybrid-mount/run/state.json` 的 `timestamp` 属于本次开机（或 `hybrid-mount status` 成功返回）。

### 4.4 `enable` / `disable` 输出

```json
{ "ok": false, "code": 1200, "message": "检测到 Zygisk Next 正在提供 Zygisk 功能",
  "conflict": { "kind": "zygisk", "id": "zygisksu", "name": "Zygisk Next", "version": "4.0.1",
                "evidence": "/data/adb/modules/zygisksu/zygisk/arm64-v8a.so" },
  "state": { "id": "zygisk", "enabled": false, "pending_reboot": "none" } }
```

成功时：

```json
{ "ok": true, "code": 0, "message": "已启用，完整重启后生效",
  "state": { "id": "zygisk", "enabled": true, "pending_reboot": "full" } }
```

### 4.5 错误码表（UI 的「错误码 %d」直接显示这个数）

| code | 常量 | 触发 | UI 落点 |
|---|---|---|---|
| 0 | `OK` | 成功 | — |
| 1000 | `IO_ERROR` | 读写/解压失败 | S5 错误码 |
| 1001 | `STATE_CORRUPT` | 状态文件损坏已恢复 | S5 或静默（仅日志） |
| 1100 | `UNSUPPORTED_ARCH` | 当前架构无载荷（riscv64） | S1 |
| 1101 | `UNSUPPORTED_ENV_MAGISK` | 检测到 Magisk（`utils::has_magisk()`，F8） | S1 |
| 1102 | `UNSUPPORTED_KERNEL` | 内建元模块：`is_overlay_supported()` 为否 | S1 |
| 1200 | `CONFLICT_ZYGISK_OTHER` | 已有其它 Zygisk 实现且处于启用态 | S6 |
| 1201 | `CONFLICT_ZYGISK_BUILTIN_ON` | 内建已启用，拒绝启用/安装其它实现 | 模块列表侧提示 |
| 1300 | `CONFLICT_META_OTHER` | 已有其它元模块 | S6 |
| 1301 | `CONFLICT_META_BUILTIN_ON` | 内建元模块已启用，拒绝其它元模块 | 模块列表侧提示 |
| 1400 | `PAYLOAD_MISSING` | 载荷文件缺失或校验不符 | S5 |
| 1401 | `EXTRACT_FAILED` | 解压/rename 失败 | S5 |
| 1500 | `MONITOR_SPAWN_FAILED` | monitor fork/exec 失败 | S5 |
| 1501 | `INJECT_FAILED_64` | 64 位注入失败（来自 monitor state.json） | S5（B2 行显示异常） |
| 1502 | `INJECT_FAILED_32` | 32 位注入失败 | 仅 B2 行，不升级为 S5 |
| 1600 | `NOT_FOUND` | 组件 id 不存在 | 管理器内部 |
| 1601 | `SAFE_MODE` | safe mode 下拒绝启用 | S5/提示 |
| 1700 | `METAMOUNT_FAILED` | 元模块挂载流程返回非零 | S5 |
| 1701 | `METAMOUNT_DEGRADED` | 挂载成功但 `degraded=true` | S4 + 警告行 |
| 1702 | `BOOT_GUARD_TRIPPED` | 连续失败达阈值已自动关闭 | S2 + 一次性提示 |
| 1800 | `READ_ERROR` | 状态探测失败 | S9 |
| 1801 | `BUSY` | 3 秒内未取到锁 | 开关回弹 |

### 4.6 并发保护

所有 `enable/disable` 用 `/data/adb/musu/.lock` 的 `flock(LOCK_EX|LOCK_NB)`，取不到即 `1801`。理由：管理器可能在 `LifecycleResumeEffect` 和开关回调里同时打进来（`SettingsScreen.kt:39-48`），双击也会。

| 决定 | 选了什么 | 一句话人话 | 改的代价 |
|---|---|---|---|
| D-06 命令粒度 | 一组 `builtin` 子命令，不挂到 `feature` / `module` 下 | 「设置里的开关」和「内核 feature」是两码事，混在一起以后每加一个都要改内核 | 低：`cli.rs` 新增一个 `Commands::Builtin` 分支 |
| D-07 输出格式 | 全 JSON + 退出码 | 管理器要显示"为什么开不了"，就必须拿到结构化的原因，不能靠解析中文 | 中：管理器要新增一个取 stdout 的封装（F12 现在只有 `Boolean`） |

---

## 5. 开机编排

### 5.1 启动时序表

"插入点"列的行号指现有 `init_event.rs::on_post_fs_data()`。

| # | 现有阶段（行号） | MuZygisk 动作 | 内建元模块动作 | 失败处理 |
|---|---|---|---|---|
| 1 | `clear_all_temp_configs()`（22） | — | — | 已有 warn，不阻塞 |
| 2 | `has_magisk()` → return（29-32） | **不启动**（F8 早退）；`ui_state` 归 `unsupported(1101)` | **不启动** | 早退，符合"MuSu 不与 Magisk 共存" |
| 3 | 公共 `post-fs-data.d`（43） | — | — | 已有 warn |
| 4 | `ensure_binaries(true)`（50） | **新增：`builtin::ensure_payload("zygisk")`**（版本戳不符才解压，§2.3） | **新增：`builtin::ensure_payload("metamodule")`** | 解压失败 → 写 `last_error`，本组件本次开机跳过，**不影响**既有二进制释放 |
| 5 | safe mode 分支 → 禁用全部模块后 return（55-61） | **不启动**，且**删除**运行目录 | **不接管**：若软链存在则临时移除（避免 safe mode 下由元模块接管挂载），不删除用户配置 | 早退；safe mode 的语义是"尽量少动系统" |
| 6 | `handle_updated_modules()`（63） | — | — | 已有 warn |
| 7 | `prune_modules()`（67） | 不涉及（不在 `MODULE_DIR`，F2） | 不涉及 | — |
| 8 | `regenerate_preinit()`（74） | — | — | 已有 warn |
| 9 | `restorecon()`（78） | — | — | 不改 `/data/adb/musu` 标签（§7.2） |
| 10 | `load_sepolicy_rule()`（83-85） | **新增下一行：`builtin::load_sepolicy("zygisk")`**，读 `/data/adb/musu/zygisk/sepolicy.rule` 走 `sepolicy::apply_file` | — | 失败 warn + 继续（规则缺失会导致注入失败，但不该阻塞开机） |
| 11 | `profile::apply_sepolies()`（87） | — | — | 已有 warn |
| 12 | `init_forced_features()` / `init_features()`（91-98） | 之后 **新增 `builtin::apply_feature_overrides()`**：内建 zygisk 启用时把 `kernel_umount` 置 0 并记录原值（§8.4） | — | 失败 warn；不改用户其它 feature |
| 13 | 元模块 `post-fs-data.sh`（101） | — | 内建目录**没有**该脚本 → `check_metamodule_script` 返回 `None`，no-op | — |
| 14 | 模块 `post-fs-data.sh`（106） | — | — | 已有 warn |
| 15 | **（新增，插在第 106 行模块脚本之后、第 111 行 `load_system_prop` 之前）**：`builtin::start_zygisk()` | `mkdir/chmod 0555/chcon` 运行目录 → `chdir(/data/adb/musu/zygisk)` → fork+exec `./bin/zygisk-ptrace64 monitor`（setsid、stdout 重定向到 `/data/adb/ksu/log/zygisk.log`、**不等待**）→ 写 `applied_boot_id` | — | 失败 → `last_error=1500`，写 `applied_boot_id=null`，**继续往下走** |
| 16 | `load_system_prop()`（111） | — | — | — |
| 17 | 元模块 `metamount.sh`（116） | — | **既有代码自动执行**：`exec_mount_script` → `get_metamodule_path()` 返回内建目录 → 执行 `metamount.sh`。前后各加一次 boot guard 计数（§11.3） | `exec_mount_script` 返回 Err → `1700`，**不阻塞开机**（既有代码就是 warn） |
| 18 | `run_stage("post-mount")`（120） | — | 自动执行 `post-mount.sh` | 已有 warn |
| 19 | `on_boot_completed()`（177-189） | 汇总 monitor 的 `run/zygisk/state.json` → 写自身 `applied_boot_id`、清零失败计数 | 汇总 `hybrid-mount status` → 成功则 `consecutive_boot_failures=0`，失败则 +1 | 汇总失败只 warn |
| 20 | `run_stage("emulated-soft-reboot")`（软重启，`soft_reboot.rs:168`） | 内建目录无该脚本 → no-op；monitor 保持运行（孤儿进程），新 zygote 会被重新注入 | 自动执行 `emulated-soft-reboot.sh` → `hybrid-mount runtime prepare-reboot` | — |

### 5.2 为什么插在 15（模块脚本之后、system.prop 之前）

1. **顺序等价性**：ReZygisk 作为独立模块时，它的 `post-fs-data.sh` 就在"模块脚本"集合里执行。放在集合之后，是对"最后一个模块脚本"的确定性近似（`foreach_module` 用 `readdir` 顺序，本身不确定，见 F2），不会比现状更差。
2. **前置依赖满足**：monitor 需要的 sepolicy（#10）和载荷（#4）都已就绪；且仍在 zygote 启动之前（`init_event.rs` 整段都在 `/system/bin/init second_stage` 之后的 exec 钩子里完成，`ksud_integration.c:179-183`）。
3. **不改变既有挂载语义**：位于 `metamount`（#17）之前，模块挂载结果不受影响。

### 5.3 "失败绝不阻塞开机"的三条硬规则

1. 新增调用一律 `if let Err(e) = ... { warn!(...); }`，**不返回 `Result`**，与 `init_event.rs` 现有风格一致（第 43/63/67/74/78/83/87/96/101/106/111/116 行全部如此）。
2. monitor 用 `Command::spawn` 后**立即 detach**：不在 `on_post_fs_data` 的进程生命周期里（`ksud` 退出后由 init 收养），避免 `ScriptWait::Until` 的 35 s（`defs.rs:6`）被拖长。
3. 解压失败时保留旧载荷（§2.3 step 6）：宁可跑旧版本，也不要留半成品。

| 决定 | 选了什么 | 一句话人话 | 改的代价 |
|---|---|---|---|
| D-08 编排方式 | 元模块**零改动**（复用软链）；zygisk 只增 4 个新函数调用 | 少改一处就少一处回归；元模块本来就是"另一个元模块目录"，没必要为它写新代码 | 低：`init_event.rs` 净增约 8 行 |
| D-09 monitor 启动时机 | 模块脚本之后、system.prop 之前 | 让模块先做完自己的准备，monitor 最后上场 | 中：若某个模块的 post-fs-data 依赖 zygisk 已注入，会不满足（ReZygisk 自身也不满足，不是回归） |

---

## 6. 互斥判定

### 6.1 判定矩阵（识别特征）

`置信度` = 该特征单独出现时能否作为拦截依据；`用途` 说明它进不进 §6.2 的拦截逻辑。

**A. "设备上已有其他 Zygisk 实现"**

| # | 识别特征 | 证据路径 / 判据 | 置信度 | 用途 |
|---|---|---|---|---|
| A1 | 已启用模块目录内存在 `zygisk/` 且其中有任一 ABI 命名文件：`arm64-v8a.so` / `x86_64.so` / `armeabi-v7a.so` / `x86.so` | `/data/adb/modules/<id>/zygisk/<abi>.so` 且 `<id>/disable` 不存在 | 高 | **拦截依据**（这是 Zygisk 模块 API 的唯一约定，`zygiskd.c:82` 同款判据） |
| A2 | 上述模块的 `module.prop` 中 `name`/`description` 命中 `/zygisk/i` | `read_module_prop()` | 中 | 只用于**生成可读原因**（"被「Zygisk Next」占用"），不单独拦截 |
| A3 | 运行期进程：`/proc/*/comm` ∈ {`zygiskd`, `zygiskd64`, `zygiskd32`, `zygisk-ptrace64`, `zygisk-ptrace32`} | `/proc` 扫描 | 高 | 拦截依据（"实现正在跑"，比文件更硬） |
| A4 | 残留目录：`/data/adb/rezygisk`、`/data/adb/zygisksu` | `Path::exists()` | 低 | 只进 `warnings`，不拦截（卸载残留不该拦住新功能） |
| A5 | `utils::has_magisk()` 为真（Magisk 内置 Zygisk） | 现有函数 | 高 | 拦截依据，且归为 `unsupported(1101)` 而非 `conflict`（MuSu 不与 Magisk 共存，F8） |
| A6 | 内核自带 Zygisk（部分定制内核） | 待验证：`/proc/kallsyms` 或 `/sys/module/*` 特征 | 低 | **本版不实现**，登记 V-9 |
| A7 | 第三方实现自身声明（如 `module.prop` 的 `managedFeatures=*zygisk*`） | `module.prop` | 低 | 不入本版逻辑（字段不通用，见 V-9） |

**B. "设备上已有其他元模块"**

| # | 识别特征 | 证据路径 / 判据 | 置信度 | 用途 |
|---|---|---|---|---|
| B1 | `/data/adb/metamodule` 是软链，且 `read_link` 解析后 ≠ `/data/adb/musu/meta-hybrid` | `metamodule.rs:28-49` 同款解析 | 高 | **拦截依据** |
| B2 | `/data/adb/modules/*/module.prop` 里 `metamodule=1|true` | `metamodule::is_metamodule`（`metamodule.rs:19-24`） | 高 | 拦截依据（既有函数直接复用） |
| B3 | 上述模块目录存在 `metamount.sh` | `path.join("metamount.sh").exists()` | 中 | 只用于提高原因可信度 |
| B4 | 运行期进程 `hybrid-mount`（非内建路径） | `/proc/*/exe` 解析 | 低 | 不入拦截逻辑（可能是当次开机的内建进程） |
| B5 | `/data/adb/hybrid-mount` 或 `/data/adb/mountify` 等运行目录存在 | `exists()` | 低 | 只进 `warnings` |

**C. 跨组件**：MuZygisk 与内建元模块**不互斥**（职责不同）。组合风险见 §11.5，不进拦截逻辑。

### 6.2 双向拦截落点（四侧）

| 场景 | 落点（文件:行） | 行为 | 错误码 |
|---|---|---|---|
| ① 用户启用内建 zygisk，但 A1/A3/A5 成立 | 新增 `builtin::enable("zygisk")` | 拒绝，`ok=false`，`conflict` 填 A2 拿到的名字与版本；**不改任何状态**；UI 由 `ui_state=blocked` 变灰 | 1200 |
| ② 用户启用内建元模块，但 B1/B2 成立 | 新增 `builtin::enable("metamodule")` | 拒绝，`conflict` 填对方 id/name/version | 1300 |
| ③ 内建 zygisk 已启用，用户**安装**或**启用**含 `zygisk/` 的模块 | 安装：`module.rs::install_module_to_system` 的元模块检查块之后（`module.rs:723-738` 附近）；启用：`module.rs::enable_module`（`module.rs:906`） | 拒绝。**必须拦安装**，因为 KernelSU 的"安装完成 = 无 `disable` 文件 = 已启用"，只拦 `enable` 会被整条绕过（见 §8.3、V-2） | 1201 |
| ④ 内建元模块已启用，用户安装 `metamodule=1` 的模块 / 启用其它元模块 | 安装：`module.rs:745-770`（既有"已有元模块"分支）；启用：`module.rs::enable_module` | 拒绝；把既有分支里 `existing_id` 回落成 `"unknown"` 的问题修掉——内建元模块目录带 `module.prop`（`id=musu_builtin_metamodule`），能读到名字，提示直接写"内建元模块已启用" | 1301 |

反向（内建后开）：③④ 描述的就是反向；正向（对方后开）在 ① ②。两侧都覆盖。

### 6.3 明确不做

- **不自动停用对方模块**（与 `builtin-ui.md` §6.2 一致）：只拒绝 + 给原因 + 指路到模块列表。落点里没有任何写对方 `disable` 文件的代码。
- **不提供 `--force`**：绕过互斥的开关必然产生"两个 zygisk 同时注入"的状态，用户无法自行恢复，代价高于收益。
- 不做安装时的"自动改对方配置"，不做"询问后自动停用"。

### 6.4 模块列表页的灰态契约（交给前端）

`builtin list` 额外给一个顶层字段供模块列表页使用：

```json
{ "blocked_module_ids": ["zygisksu", "rezygisk"], "blocked_reason_code": 1201 }
```

当内建 zygisk 处于 `active`（或 `pending_enable`，见 V-3）时列出来源模块 id；元模块同理给 `blocked_metamodule_ids`。模块列表页据此把对应开关灰掉 + 副标题用 `builtin_conflict_blocking` 文案（`builtin-ui.md` §6.3）。**后端只给数据，不替前端判定**。

| 决定 | 选了什么 | 一句话人话 | 改的代价 |
|---|---|---|---|
| D-10 拦截粒度 | 安装与启用**两处都拦** | 只管启用不管安装，用户装完就发现"两个都在跑"——因为安装即启用 | 中：`install_module_to_system` 需要多一次目录扫（读 zip 内 `zygisk/` 存在性即可，成本可忽略） |
| D-11 冲突可见性 | 每组件一个 `conflict` 对象 + 顶层 `blocked_module_ids` | 界面要显示"被「Zygisk Next 4.0.1」占用"，只给布尔值等于没给 | 低：多几个字段 |

---

## 7. SELinux

### 7.1 需要的上下文

| 对象 | 上下文 | 依据 |
|---|---|---|
| `/data/adb/musu/**`（载荷） | `u:object_r:adb_data_file:s0`（`/data/adb` 的默认标签，**不主动改**） | `restorecon()` 只改 `DAEMON_PATH`（→`ksu_file`）和 `MODULE_DIR` 下的 unlabeled 项（`restorecon.rs:61-65`）；`/data/adb/musu` 不会被它碰到 |
| `/data/adb/musu/run/zygisk`（运行目录） | `u:object_r:system_file:s0`，`chmod 0555` | 与 ReZygisk `post-fs-data.sh` 的 `create_sys_perm()` 完全一致（U 段已核） |
| 被注入进程（zygote / system_server / app） | 平台既有（`u:r:zygote:s0` 等） | 不改 |
| 模块的 `zygisk/<abi>.so` | `adb_data_file`（模块目录默认） | ReZygisk 的规则里已含 `allow zygote adb_data_file file *` |

`lib64/libzygisk.so` 位于 `adb_data_file` 上，zygote 需要读它 → 由 ReZygisk 规则中的 `allow zygote adb_data_file file *` 覆盖，**这也是不把载荷放进 `/data/adb/ksu/bin/`（可能被标成 `ksu_file`）的原因**之一。

### 7.2 规则加载

```
init_event.rs:83-85  module::load_sepolicy_rule()        # 既有：只扫 /data/adb/modules/*/sepolicy.rule（F3）
init_event.rs:86     builtin::load_sepolicy("zygisk")     # 新增：sepolicy::apply_file("/data/adb/musu/zygisk/sepolicy.rule")
init_event.rs:87     profile::apply_sepolies()            # 既有
```

- **与 `load_sepolicy_rule()` 的关系**：不改后者。原因是它的遍历源固定为 `MODULE_DIR`（F2/F3），把内建目录塞进去会让"内建"变成"伪模块"，违反 D-01。新增一条并列调用，顺序放在模块规则之后——policy patch 是**累加**的，先后不影响 allow 的最终结果，放在后面只是为了让模块规则先落地（便于日志阅读）。
- **规则内容**：直接采用 ReZygisk `module/src/sepolicy.rule`，不做裁剪。理由：这份文件是"在 KernelSU 上把 zygisk 注入跑通"的经验集合（含 `allow zygote su/ksu dir search`、`allow zygote proc file {read open}`、Tango 的 `execmod`），裁剪只在能实机验证缺失项时才做。
- **不支持 `setype` 语法**：`sepolicy.rs` 的解析器支持 `allow / type / typeattribute / attr / type_transition / type_change / genfscon`（`sepolicy.rs:234-344`），没有 `setype`。因此**不能**在规则里给文件打标签，`chcon` 必须由 ksud 自己做（`restorecon::lsetfilecon` 现成，`restorecon.rs:18`）。

### 7.3 未验证项（必须实机确认）

- ReZygisk 把运行目录标成 `system_file`，但规则里只声明了 `type zygisk_file` + `allow zygote zygisk_file sock_file {read write}`，**没有任何对象被标成 `zygisk_file`**——即 socket 的实际放行依赖平台策略对 `system_file` 的既有允许，而非这条规则。若实机 denied，补 `allow zygote system_file sock_file { read write }`（V-4）。
- 内建元模块的 `hybrid-mount` 进程在 `ksu` 域下要执行 overlay 挂载，规则需求与它作为普通模块时相同（`magisk`/`ksu` 域的既有权限），无需新增（V-5）。

| 决定 | 选了什么 | 一句话人话 | 改的代价 |
|---|---|---|---|
| D-12 规则文件位置 | `/data/adb/musu/zygisk/sepolicy.rule`（随载荷走，不落到 `/data/adb/modules`） | 规则跟程序放一起，升级时一起换 | 低 |
| D-13 标签策略 | 载荷留在 `adb_data_file`，只有运行目录改 `system_file` | 尽量用系统给 `/data/adb` 的默认标签，少改一处就少一个坑 | 低：`chcon` 只在 `start_zygisk` 里做一次 |

---

## 8. 启用/禁用的运行时语义

### 8.1 是否要重启

| 组件 | 启用后生效条件 | 禁用后生效条件 | `pending_reboot` |
|---|---|---|---|
| MuZygisk | monitor 必须在某次 `post-fs-data` 启动（#15）；它只对新出现的 `app_process` 注入 | 杀掉 monitor + 已注入进程内的 hook 无法撤销，只能等 zygote 重建 | 启用：若本 boot 的 `post-fs-data` 已过去 → `full`；若 monitor 已在运行（同一次开机内 disable→enable）→ `soft` |
| 内建元模块 | 开机阶段一次性完成挂载，`post-fs-data` 不重跑 | 已挂载的 overlay 无法安全回滚 | 一律 `full` |

**这一条与 `builtin-ui.md` §9.4 有冲突**：UI 写"MuZygisk 可走软重启"。事实是软重启（`soft_reboot.rs:148+`：跑 `emulated-soft-reboot` 阶段 → `stop` → `start`）**不会重跑 `post-fs-data`**，所以 monitor 若从未启动，软重启也起不来。修正建议：UI 的 MuZygisk 页按 `pending_reboot` 字段切换文案（`soft` → "重启后生效"；`full` → "完整重启后生效"），不写死。登记为 §14 的 C-1。

### 8.2 启用流程（幂等，可重复执行）

```
enable(zygisk):
  1. flock
  2. 环境检查：架构有载荷？Magisk？safe mode？→ 1100/1101/1601
  3. 冲突检查（A1/A2/A3/A5）→ 1200
  4. ensure_payload（若需要）
  5. 写 state.enabled=true, enabled_boot_id=当前
  6. 若当前 boot 的 post-fs-data 未执行过本组件（applied_boot_id != boot_id）→ pending_reboot=full，返回 ok
  7. 否则（monitor 已在跑）→ pending_reboot=soft
```

`disable(zygisk)`：

```
  1. flock
  2. 写 state.enabled=false（这是唯一真源，先落盘）
  3. 杀进程：按 /proc 精确匹配 comm ∈ {zygisk-ptrace64, zygiskd64, zygiskd32, zygisk-ptrace32}，
     且 /proc/<pid>/cwd 解析后 == /data/adb/musu/zygisk → SIGTERM，2 s 后仍存活则 SIGKILL
     （用 cwd 和 exe 双重限定，绝不按名字裸杀，避免误杀用户的第三方 zygisk 进程）
  4. rm -rf /data/adb/musu/run/zygisk
  5. 恢复 feature_override（§8.4）
  6. pending_reboot = full（已注入的进程只有重启才干净）
```

### 8.3 不做的清理（说清楚，避免被当成 bug）

- **不杀 zygote / app 进程**：注入已在内存里，杀进程等于制造重启风暴，且用户会认为"关了这个开关手机就崩了"。
- **不回滚 `/data/adb/metamodule` 之外的挂载**：disable 内建元模块只删软链，当次开机的 overlay 挂载保持到下次重启。
- **不删 `/data/adb/hybrid-mount/`**：那是用户配置与运行状态（`config.toml`、`module_blacklist.toml`），重开时应当复用。`builtin reset` 也不删它。

### 8.4 MuZygisk 与 `kernel_umount` 的耦合（容易漏）

ReZygisk 作为模块时在 `module.prop` 声明 `managedFeatures=kernel_umount`，等价于"把 `kernel_umount` 强制置 0"（`get_managed_features` / `feature.rs` 的 managed 语义）。内建件不是模块，没有 `module_config` 可用，因此：

```
apply_feature_overrides()（init_event.rs:98 之后）:
  若 state.zygisk.enabled:
     读当前 kernel_umount 值 → 存 state.feature_override.kernel_umount.saved（仅当首次）
     set_feature(kernel_umount, 0)
     state.feature_override.kernel_umount.applied = true
```

禁用时（§8.2 step 5）把 `saved` 写回。**不做这件事的后果**：内核会对 app 自动 umount 模块，而 zygisk 自己也做 umount 判定（`ksu_uid_should_umount`），两套判定叠加会出现"某些 app 看不到模块文件、另一些看得到"的不一致。

| 决定 | 选了什么 | 一句话人话 | 改的代价 |
|---|---|---|---|
| D-14 不即时生效 | 启用/禁用都要求重启，禁用时只清理到"下次开机不再启动" | 这两个开关换的是系统底层的实现层，中途换会留下半新半旧的状态，比不生效更糟 | 中：每次切换都要重启一次，体验比"立即生效"差；换来的是可预期的状态 |
| D-15 精确杀进程 | 用 `cwd` + `comm` 双重限定 | 只杀"我们自己启的那个"，不碰用户装的第三方 zygisk 进程 | 低：多两次 `readlink` |
| D-16 接管 `kernel_umount` | 启用时置 0 并记录原值，禁用时恢复 | 不接管会出现两套卸载判定打架 | 中：会临时改动一个用户可见的功能开关，所以必须在详情区显示"MuZygisk 已临时接管 kernel_umount" |

---

## 9. 元模块接管

### 9.1 接管动作只有两件事

```
enable(metamodule):
  ensure_payload
  若 /data/adb/hybrid-mount/config.toml 不存在 → 从载荷复制 config.toml（首次 seed）
  metamodule::ensure_symlink(/data/adb/musu/meta-hybrid)     # 既有函数，metamodule.rs:124
  写 state

disable(metamodule):
  metamodule::remove_symlink()                               # 既有函数，metamodule.rs:155
  写 state
```

`ensure_symlink()` 会自动移除旧的软链/目录（`metamodule.rs:134-144`），因此"从第三方元模块切过来"不需要额外代码——但**前置的冲突检查必须放在前面**（§6.2 ②），否则等于替用户静默替换了别人的元模块。

### 9.2 `get_metamodule_path()` 能否直接复用

**能，且是本次设计的关键复用点**。它只做两件事：软链解析 + 目标存在性检查（F4）。内建目录是普通目录，满足条件。由此 5 条使用路径全部自动生效：

| 使用路径 | 对内建元模块的效果 |
|---|---|
| `exec_stage_script("post-fs-data")` | 目录内无该脚本 → `None` → no-op（`metamodule.rs:207-224`） |
| `exec_stage_script("post-mount")` / `("service")` / `("boot-completed")` | 分别执行 `post-mount.sh` / （无 `service.sh`，no-op）/ `boot-completed.sh` |
| `exec_mount_script(MODULE_DIR)` | 执行 `metamount.sh` → `hybrid-mount boot metamount`，并注入 `MODULE_DIR` 等环境（`module::get_common_script_envs`） |
| `get_install_script()` | 读到 `metainstall.sh` → 后续所有模块安装都会走 Hybrid 的分区链接逻辑（这是元模块的职责，符合预期） |
| `check_install_safety()` | 有 `metainstall.sh` 且无 `update`/`remove`/`disable` 标记 → `Ok`，不阻塞安装 |

反向确认：`get_metamodule_id()` 返回目录名 `meta-hybrid`，被塞进 `KSU_MODULE` 环境变量（`module.rs:63-90` 的 `get_common_script_envs`）。Hybrid 的脚本不读这个变量，无副作用；但 `metainstall.sh` 自己 `export KSU_METAMODULE="hybrid_mount"`（U 段已核），与目录名不一致 —— 登记为 V-6。

### 9.3 是否放进 `/data/adb/modules`

**不放**。放进去的直接后果：出现在模块列表（F1 只要求有 `module.prop`）、被 `foreach_module` 当成普通模块跑阶段脚本（F2 → 与元模块钩子重复执行）、被 `disable_all_modules()`（safe mode）写 `disable` 文件（导致下次开机元模块被 F6 跳过，用户会以为内置坏了）。

代价（必须在验收时确认）：`get_metamodule_path()` 的**回落分支**（扫 `module.prop` 的 `metamodule=1`）永远找不到内建元模块。所以任何依赖"回落到目录扫描"的场景（软链丢失）都不会自愈 —— 因此 §11.4 要求启动时校验软链并在必要时重建。

### 9.4 与"模块式元模块被禁用"的语义差异

| 行为 | 模块式元模块 | 内建元模块 |
|---|---|---|
| 禁用表达 | `disable` 文件（F6 命中后 `check_metamodule_script` 返回 `None`） | 软链删除（`get_metamodule_path()` 返回 `None`） |
| `check_install_safety()` | 有 `metainstall.sh` + `disable` → `Err(true)`，安装被阻塞并提示"元模块被禁用" | 软链不存在 → `Ok(())`，安装走默认 installer |
| 用户可见差异 | 禁用后装模块会被拒 | 禁用后装模块正常，但模块不会被挂载（因为没有挂载器，见 §15 Q-1） |

这个差异是**接受的**：内建元模块禁用 = 回到"没有元模块"的世界，而不是"元模块被禁用"的世界。代价写在这里，避免验收时被当成 bug。

### 9.5 零源码改动的边界

不 patch Hybrid Mount 的前提是接受 §9.4 的语义与 U11 的三处 best-effort 失效：

| 失效点 | 实际影响 | 是否可接受 |
|---|---|---|
| `update_description()` 找不到 `/data/adb/modules/hybrid_mount/module.prop` | 每次挂载后一条 `warn` 日志，无功能影响 | 可接受（但日志噪音需在 v2 处理） |
| `set_temporary_override()` 调 `ksud module config set override.description --temp`（`KSU_MODULE=hybrid_mount`） | 生成 `/data/adb/ksu/module_configs/hybrid_mount/tmp.config` 残留；该文件在下次开机的 `clear_all_temp_configs()`（`init_event.rs:22`）被清掉 | 可接受（自愈） |
| `install-state` 的 `self_module=false` | 只影响 WebUI（我们不用 WebUI） | 可接受 |

若后续要 patch，改动量是 `src/defs.rs` 4 个常量；**0.0.6 不做**，理由：patch 后我们就有"修改版"，GPL 合规要求随之从"指向上游 commit"变成"自备对应源码"（§12.3）。

| 决定 | 选了什么 | 一句话人话 | 改的代价 |
|---|---|---|---|
| D-17 接管方式 | 软链 + 既有函数，零 patch | 官方完整版原样跑，出问题能直接对照上游 | 中：`update_description` 等 3 处 best-effort 失效，日志有噪音；`install-state` 自检在内建形态下不准确 |
| D-18 配置文件 | `/data/adb/hybrid-mount/config.toml` 为真源，载荷里的 `config.toml` 只做首次 seed | 用户改的配置在升级后不丢 | 低：多一步"不存在才复制" |
| D-19 不带 LKM / VFS | 载荷不含 `lkm/`、`vfs/binaries/`，`default_mode` 保持 `overlay` | KSU 有 nuke-ext4 ioctl，官方在 KSU 上也删 `lkm/`；省约 1.1 MB | 中：`vfs` 模式不可用（详情区必须写明"当前仅支持 overlay 模式"） |

---

## 10. 架构限制

### 10.1 只构建 arm64-v8a / x86_64 时，32 位 zygote 怎么办

事实：monitor 对 32/64 独立处理（U3），`bin/zygiskd32` 与 `bin/zygisk-ptrace32` 缺失时 `execl` 失败 → 该 ABI 记错误，64 位不受影响（`monitor.c:456-476`）。但这会产生每 30 s 重复的错误日志与 `state.json` 里的失败噪声。

处理：

1. **fork 里加一处显式判定**（§13 M5）：启动时检查 `./bin/zygiskd32` 是否存在；不存在则把 32 位标记为"不覆盖"，`PRE_INJECT` 直接跳过，不尝试 exec、不刷日志。
2. `builtin list` 的 `metrics.zygote32_covered` 反映该判定；`zygote32_injected` 恒为 `false`。
3. 设备若有 32 位 zygote（`/system/bin/app_process32` 存在且 `ro.product.cpu.abilist` 含 `armeabi-v7a`）→ `ui_state` 仍为可启用，但带 `warnings: [{code:"abi32_not_covered"}]`；UI 在详情区显示一行"32 位应用不会加载 Zygisk 模块"。**不置灰**：64 位能力是完整的，置灰会让绝大多数用户误以为功能不可用。
4. 纯 32 位设备（只有 `app_process32`）→ `available=false`、`code=1100`。

### 10.2 riscv64

| 组件 | riscv64 状态 | 依据 |
|---|---|---|
| MuZygisk | `available=false`，`code=1100` | ReZygisk `zygiskd.c` 的 `ARCH_STR` 对非 arm/x86 直接 `#error`（`zygiskd.c:49-60`）；我们不为 riscv64 出载荷 |
| 内建元模块 | `available=false`，`code=1100` | 0.0.6 只为 arm64-v8a / x86_64 打包 Hybrid 二进制；官方虽有 riscv64 构建，但本版不纳入 |

实现要求：`BuiltinPayload` 的 riscv64 目录放占位文件（`README`），保证 `rust-embed` 编译通过；`ensure_payload()` 在载荷缺失时返回 `1100` 而不是 panic。

### 10.3 体积

| 项 | 量级 | 来源 |
|---|---|---|
| MuZygisk 载荷 | 约 208 KB（41+58+77+58，含重名副本） | 需求提供的已编译产物实测 |
| Hybrid 官方二进制 | **待实测（V-1）**，预期 1.2–1.6 MB/架构（官方说明 strip 后四架构二进制占总包约 75%） | `Cargo.toml` 的 `[profile.release] strip="symbols"` 注释 |
| 省掉的部分 | `lkm/` 188 KB、`vfs/binaries/` 960 KB、`webroot/` 与 WebUI | `du -sh`（§1 U8/U12） |

| 决定 | 选了什么 | 一句话人话 | 改的代价 |
|---|---|---|---|
| D-20 32 位不置灰 | 只提示不拦截 | 64 位能用就不该拦着用户 | 中：32 位应用里装了 zygisk 模块的用户会遇到"模块不生效"，靠提示行解释 |
| D-21 riscv64 明确不支持 | 置灰 + 原因码 | 说"不支持"比"开了没用"诚实 | 低：riscv64 用户量小，且本来该架构的 zygisk 生态几乎为零 |

---

## 11. 失败模式与降级

### 11.1 失败模式表

| # | 失败 | 检测点 | 用户可见 | 系统行为 | 恢复 |
|---|---|---|---|---|---|
| 1 | `zygiskd` 起不来（载荷损坏/权限） | monitor 的 `state.json` 中 `daemon_running=false` + `reason` | S5，`code=1500/1400`，B2 行显示原因串 | zygote 照常启动，app 正常运行（无注入） | 重新 enable（会重解压）；仍失败看 `/data/adb/ksu/log/zygisk.log` |
| 2 | 注入失败但 daemon 正常 | `state.json` 的 `64.zygote_injected=false` | S5，`code=1501` | 同上 | 通常是 SELinux 或 ptrace 被拦，见 V-4 |
| 3 | 32 位注入失败 | `state.json` 的 `32` 段 | 不进 S5，仅 B2 行（`code=1502`） | 64 位不受影响 | 不处理（架构上不覆盖） |
| 4 | 元模块挂载失败 | `exec_mount_script` 返回 Err 或 `hybrid-mount status` 的 `mount_error_modules` 非空 | S5，`code=1700/1701`；B2 行显示"N 已挂载，M 失败" | 开机继续，模块文件未挂载（部分挂载则部分可见） | 看 `/data/adb/hybrid-mount/run/state.json` 的 `mount_error_reasons` |
| 5 | 元模块挂载**挂死**（不是失败，是不返回） | `exec_mount_script` 无超时（既有代码就是 `status()` 同步等待，**这是个真实风险**） | 无界面的卡开机 | 可能导致开机卡住 | 见 §11.2 的 boot guard；另建议给 metamount 加超时（V-7） |
| 6 | 用户同时开了两者 | 不是错误 | 都显示运行中 | 需要验证组合（§11.5） | — |
| 7 | 升级残留：`state.json` 里 `payload_version` 落后 | `ensure_payload` 版本比较 | 无感 | 重解压，保留 `enabled` 意图 | 自动 |
| 8 | 升级残留：新版本删了某组件 | `state.json` 有未知 id | 无感 | 保留原样写回（§3.2） | 自动 |
| 9 | 软链丢失（用户手动删/其它工具清理） | 启动时校验 | S8→重启后 S2 | 不自动重建（避免与"用户主动禁用"混淆），但 `enable` 会重建 | 重开开关 |
| 10 | 状态文件被外部改成非法 JSON | 解析 | S9 一次 | 备份 + 重置为默认（§3.2） | 自动 |

### 11.2 boot guard（内建元模块必须有，Hybrid 的 guard 不覆盖 overlay）

Hybrid 自带 guard 只管 LKM/VFS（U13），overlay 模式下挂死没有任何保护。设计：

```
post-fs-data，执行 metamount 之前：
  attempts = read(/data/adb/musu/run/meta_boot_attempts) + 1
  write(attempts)
  若 attempts > 3:
      state.metamodule.enabled = false
      state.metamodule.last_error = { code: 1702, message: "连续 3 次挂载未确认成功，已自动关闭内建元模块" }
      metamodule::remove_symlink()
      log::error!(...)
      本次开机不执行 metamount（回落到"无元模块"路径）
      return
  执行 metamount
```

`on_boot_completed` 里确认成功（`hybrid-mount status` 返回 0 且 `timestamp` 属本 boot）→ `attempts = 0`。

判据选择说明：用"boot_completed 时能否确认成功"而不是"metamount 返回码"，因为挂死的场景根本没有返回码。

### 11.3 MuZygisk 的等价保护

不需要 boot guard（monitor 挂死不影响开机，zygote 照常启动）。但需要一次"monitor 死了"的自愈检查：`on_boot_completed` 若发现 `enabled && !monitor_alive` → 记 `last_error=1500` 并**不自动关闭**（用户可能只是想重启一次试试）。自动关闭用在"连续 3 次开机都没起来"上，与元模块同一套计数（独立文件 `run/zygisk_boot_attempts`）。

### 11.4 启动时的一致性校验（每次都跑，成本可忽略）

```
1. state 与磁盘一致性：
   - metamodule.enabled=true 但软链不存在/指向别处 → 记 last_error，ui_state=failed（不自动重建）
   - metamodule.enabled=false 但软链指向内建目录 → 自动删除软链（这是"意图=关"的确定性修复，安全）
   - zygisk.enabled=true 但载荷缺失 → ensure_payload 重试一次
2. 检查 /data/adb/metamodule 是否指向一个**已不存在的模块目录**（第三方元模块被卸载的残留）→ 删除软链并 warn
```

### 11.5 两者同时开启的组合风险（待验证）

| 风险 | 机理 | 处置 |
|---|---|---|
| 挂载隐藏了 zygisk 模块的 `.so` | Hybrid 用 overlay 替换模块目录内容后，zygiskd 读 `/data/adb/modules/<id>/zygisk/*.so` 读到的是替换后的视图 | 待验证（V-8）：两者语义上不应冲突（`/data/adb` 不在 Hybrid 的 `MANAGED_PARTITIONS` 内） |
| umount 判定叠加 | Hybrid 维护自己的 active mounts，内核 `kernel_umount` 另有一套 | 由 D-16（MuZygisk 接管 `kernel_umount=0`）覆盖 |
| 用户同时开导致排查困难 | 两个"接管型"功能同开 | 允许，但 `builtin list` 在两者都 `active` 时给一条 `warnings: [{code:"both_active"}]`，UI 在详情区提示 |

| 决定 | 选了什么 | 一句话人话 | 改的代价 |
|---|---|---|---|
| D-22 不自动重建软链 | 只在 `enable` 时建，启动时只删不建 | 自动重建会让"用户手动禁用了但重启又回来"变成灵异事件 | 中：软链被外部删掉时用户要重开一次开关 |
| D-23 boot guard 判据 | boot_completed 时确认，而非返回码 | 挂死没有返回码，只能事后确认 | 低 |
| D-24 失败不自动关闭（首次） | zygisk 失败只记录；元模块连续 3 次才关 | 一次失败就关等于不让用户试 | 低 |

---

## 12. 许可证落地

### 12.1 仓库级边界

| 路径 | 许可证 | 变更 |
|---|---|---|
| `muzygisk/`（新增，ReZygisk fork） | **AGPL-3.0-or-later** | 新增目录，独立 `LICENSE`（AGPL 原文，不改）+ `NOTICE`（保留 `Copyright (C) 2026 The PerformanC Organization`，追加 `Modifications Copyright (C) 2026 MuSu Next`）+ `README.md`（见 §12.2） |
| `userspace/ksud/bin/builtin/<arch>/` | 随载体（载荷是构建产物） | 只放产物，不放源码；每个目录加 `SOURCE.txt` 指向上游 commit 与许可证 |
| `third_party/meta-hybrid_mount/` | 不落源码，只在 `THIRD_PARTY.md` 登记 | 见 §12.3 |
| 仓库其余部分 | GPL-3.0-or-later（`README.md:35-36`） | 不动 |
| `kernel/` | GPL-2.0-only | 不动 |

### 12.2 `muzygisk/README.md` 必须写清的六项

1. 上游项目 URL + 我们 fork 的**具体 commit**（40 位 sha）+ fork 日期。
2. 改名范围：目录名、二进制名、进程名（`zygiskd*` → `muz` 前缀？**不改**，见下）与**路径**（必改，§13 M1–M3）。
3. 改动清单（与 §13 的 M1–M6 一一对应），每条一句。
4. 许可证：AGPL-3.0-or-later，附 `LICENSE` 与 `NOTICE` 的相对路径。
5. **AGPL §13 的后果声明**：本组件被 MuSu Next 以"编入 ksud 载荷"的方式分发，属于 GPLv3 §13 允许的组合；组合物的网络交互条款随 AGPL 生效。措辞：
   > MuZygisk is a fork of ReZygisk, licensed under AGPL-3.0-or-later. It is distributed as part of MuSu Next, whose userspace is GPL-3.0-or-later. Combined distribution is permitted by GPLv3 §13; the AGPL's section 13 (network interaction) applies to the combined work.
6. 如何构建（`make release NDK_PATH=... ARCHS="arm64-v8a x86_64"`）与产物 SHA256 清单。

**命名决策**：二进制名与进程名**保留上游**（`zygiskd64`、`zygisk-ptrace64`），只改路径。理由：改名 103 处标识符会引入零收益的差异，且未来 rebase 上游时冲突面巨大；而"真改名"的需求实质是"不冒充 ReZygisk / 不出现 rezygisk 字样在用户可见处"。所以：

| 面 | 处理 |
|---|---|
| 用户可见（模块名、UI 文案、日志前缀、状态字符串） | 全改（`ReZygisk` → `MuZygisk`，`rezygiskd` → `muzygiskd` 在 `LOGI` 里） |
| 内部二进制名 / 进程名 | **不改**，避免 rebase 成本与 `zzygiskd` 生态判据（A3）互相纠缠 |
| 路径 | 必改（这是唯一功能性硬编码） |

若主理人要求"内部名也全改"，代价：上游 rebase 每次需手工合并约 100 处标识符，且 A3 的进程识别判据要同时保留旧名（否则识别不到第三方 ReZygisk）。登记为 §14 决策项 Q-2。

### 12.3 Hybrid Mount 的合规（我们不改它，所以最省）

- 只分发**未修改的官方二进制**。对应源码 = 上游同一 commit（`GPL-3.0-only`）。
- `THIRD_PARTY.md` 新增条目：项目名、URL、commit、发布日期、许可证、我们分发的文件清单 + 每个文件 SHA256、以及一句"未修改，对应源码见上述 commit"。
- 载荷里保留上游 `LICENSE`（`meta-hybrid/LICENSE`）与 `USAGE_NOTICE.md`（若上游有此文件）。
- 一旦我们发现必须 patch（例如 §9.5 的 4 个常量），**必须**同时落 `third_party/meta-hybrid_mount/` 完整源码 + 我们的 patch 文件。0.0.6 明确不 patch。

| 决定 | 选了什么 | 一句话人话 | 改的代价 |
|---|---|---|---|
| D-25 fork 改名粒度 | 用户可见面全改，内部符号与二进制名保留 | 用户看到的都是 MuZygisk；内部保留上游名字，以后同步上游省一半力气 | 中：`docs` 里要解释"为什么进程名还是 zygiskd" |
| D-26 载荷编入 ksud | 是（AGPL 组合，Declared） | 一个二进制自带全部零件，升级不会版本打架 | 中高：组合物受 AGPL §13 约束，须在 README 声明；将来若做任何网络服务需评估 |
| D-27 Hybrid 不 patch | 原样分发 + 指向上游 commit | 零改动=零合规成本 | 中：3 处 best-effort 失效（§9.5） |

---

## 13. MuZygisk fork 改动清单（评审用，不给代码）

路径相对 `muzygisk/`（fork 根）。每条标注"必改/建议"与影响面。

| # | 位置 | 改动 | 级别 | 理由 |
|---|---|---|---|---|
| M1 | `zygiskd/src/zygiskd.c:41-46` | `TMP_PATH` → `/data/adb/musu/run/zygisk`；`ZYGISKD_FILE`/`ZYGISKD_PATH` → `/data/adb/musu/zygisk/bin/zygiskd` LP_SELECT("32","64")；`PATH_MODULES_DIR` **保持不变** | 必改 | `PATH_MODULES_DIR` 保持 `/data/adb/modules` 是 Zygisk 模块 ABI 的一部分，不能改 |
| M2 | `zygiskd/src/zygiskd.c:78` | 模块扫描的排除项 `"rezygisk"` → 改为排除内建自身目录名的判据（内建不在 `/data/adb/modules`，此项实际不命中，但保留以防未来把载荷放进去） | 建议 | 消除误导 |
| M3 | `loader/src/include/daemon.h:44`、`loader/src/ptracer/monitor.c:888,961` | `TMP_PATH` 与两处 `state.json` 字面量 → `/data/adb/musu/run/zygisk/state.json` | 必改 | 状态通道位置 |
| M4 | `loader/src/injector/entry.c:18`、`hook.c:545`、`ptracer/ptracer.c:184` | `/data/adb/modules/rezygisk/lib64/libzygisk.so` → `/data/adb/musu/zygisk/lib64/libzygisk.so` | 必改 | 被注入侧要 stat/dlopen 这个路径 |
| M5 | `loader/src/ptracer/monitor.c`（`PRE_INJECT` 宏内，约 499-520） | 启动时探测 `./bin/zygiskd32` 与 `./bin/zygisk-ptrace32` 是否存在；不存在则跳过 32 位且把该 ABI 标为"不覆盖" | 必改 | §10.1，否则每 30 s 一次 exec 失败噪声 |
| M6 | `loader/src/ptracer/monitor.c:847,972` | 删除写 `module.prop` 的状态逻辑，改为**只**写 `state.json`（含 `version` 字段） | 必改 | U5：内建形态没有 `module.prop`，且 emoji 状态串违反项目 P0 规则 |
| M7 | `zygiskd/src/root_impl/kernelsu.c:16-20` | `ksu_manager_pkgs` 追加 MuSu 管理器包名（`org.musu.next` 与构建期 `KSU_PACKAGE_NAME`） | 必改 | U7：不加则 `PROCESS_IS_MANAGER` 判定对内建管理器失效 |
| M8 | `module/src/*`（`post-fs-data.sh`、`service.sh`、`customize.sh`、`rezygisk.sh`、`verify.sh`、`uninstall.sh`） | 不参与构建；仅把 `sepolicy.rule` 作为载荷文件复制进 `bin/builtin/<arch>/zygisk/` | 必改 | 内建形态没有安装脚本；模块脚本里的 `which magisk` 循环逻辑不适用 |
| M9 | `webroot/` | 不打包 | 必改 | 需求：原生界面，不要 WebUI |
| M10 | 全局 `LOGI/LOGE` 前缀 | `ReZygisk` → `MuZygisk` | 建议 | 日志可辨识 |

不改的部分（明确写出来，避免评审反复问）：

- `zygiskd`/`libzygisk.so`/`libzygisk_ptrace.so` 的文件名与进程名（§12.2）。
- KSU ioctl 结构体与命令号（U6 已核与 MuSu 完全一致）。
- 上游 `Makefile` 的 `ARCHS` 机制（我们用 `ARCHS="arm64-v8a x86_64"`，不新增架构支持）。

---

## 14. 与 `builtin-ui.md` 的差异与依赖

| # | 项 | UI 文档现写法 | 本文结论 | 处置 |
|---|---|---|---|---|
| C-1 | MuZygisk 的重启方式 | §9.4：可走软重启（尊重软重启偏好） | monitor 只在 post-fs-data 启动，软重启不重跑该阶段 → 常规情形必须完整重启；仅"同一次开机内已启动过 monitor"时软重启足够 | **需要前端按 `pending_reboot` 字段切换文案**，不要写死"重启后生效"。请主理人确认（Q-3） |
| C-2 | 元模块重启用词 | 「完整重启」 | 一致（§8.1） | 无 |
| C-3 | S1 不支持条件 | "内核缺少 Zygisk 接口" / "内核不支持 overlayfs" | zygisk 的不支持实际是**架构无载荷**（riscv64）或 **Magisk 存在**；"内核缺接口"不是主因 | 建议文案改成"当前架构无 MuZygisk 载荷"（`code=1100`）与"检测到 Magisk"（`code=1101`），由 `error.code` 驱动 |
| C-4 | B3 行「Zygisk 模块 N 个已加载，共 M 个」 | 需要两个数 | `metrics.modules_loaded / modules_total` 已提供；`modules_total` 统计"已启用且含 `zygisk/<abi>.so` 的模块数"，`loaded` 取 monitor `state.json` | 无 |
| C-5 | 元模块 B3 行「接管范围 /system · /vendor · /product」 | 需要列表 | `metrics.scope` 来自 `hybrid-mount status` 的 `active_mounts` 顶层目录去重 | 无 |
| C-6 | 详情区「占用空间」 | 需要字节数 | `detail.disk_bytes` = 载荷目录 `du` 之和 | 无 |
| C-7 | S7「自身阻塞他人」 | 需要知道哪些模块被挡 | 本文 §6.4 的 `blocked_module_ids` | 与 D3 一致（模块列表页需同批实现） |

---

## 15. 待验证与待决策

### 15.1 待验证（按风险降序）

| # | 待验证项 | 怎么验 | 不验的后果 |
|---|---|---|---|
| V-1 | Hybrid Mount 官方二进制单架构体积与 SHA256 | 取官方 release 对应架构二进制 `ls -l` + `sha256sum` | 只能给"预期 1.2–1.6 MB"，ksud 体积评估不准 |
| V-2 | "安装即启用"是否真的绕过互斥 | 内建 zygisk 开启状态下装一个含 `zygisk/arm64-v8a.so` 的模块，观察是否出现双注入 | §6.2 ③ 的拦截点选错，互斥形同虚设 |
| V-3 | 内建 zygisk `pending_enable` 期间是否应该已拦截第三方 | 决策项（见 Q-1），影响 `blocked_module_ids` 的判定时机 | 用户可能先在 0.5 秒内装上对方模块 |
| V-4 | 运行目录标 `system_file` 后 zygote 能否访问 socket | 实机 `dmesg | grep avc` | 注入全线失败 |
| V-5 | `ksu` 域执行 `hybrid-mount` overlay 挂载是否被 SELinux 拦 | 实机启用后看挂载是否成功 + avc 日志 | 内建元模块开了等于没开 |
| V-6 | `KSU_METAMODULE="hybrid_mount"` 与目录名 `meta-hybrid` 不一致的实际影响 | 用 2–3 个会读该变量的模块安装测试 | 个别模块安装脚本行为异常，且难归因 |
| V-7 | `exec_mount_script` 无超时导致开机挂死的实际概率 | 故意构造失败配置（错误的 `overlay_mode`）跑两轮 | 用户可能卡开机，只能靠 §11.2 guard 兜 |
| V-8 | 两者同开时挂载视图是否影响 zygiskd 读 `.so` | 同开 + 装一个带 `zygisk/` 的模块 | 组合场景不可用 |
| V-9 | 第三方实现的完整识别特征（Zygisk Next 的目录布局、是否有内核内置 zygisk） | 装 Zygisk Next / 定制内核分别 `find` | A1/A3 之外漏检，互斥漏判 |
| V-10 | 软重启后 monitor 是否存活并注入新 zygote | 启用 → 软重启 → 看 `state.json` 的注入时间戳 | C-1 的结论可能反过来 |

### 15.2 需要主理人决策（不自作主张）

| # | 决策 | 候选 | 代价 |
|---|---|---|---|
| Q-1 | `pending_enable` 期间是否也算"占用"，从而立刻拦截第三方 | A) 立刻拦截（严格） B) 等 `active` 才拦（宽松） | A：用户点了开关但还没重启，就去装对方模块会被拒，可能觉得"管太宽"。B：存在"两边都开了，重启后撞车"的窗口 |
| Q-2 | fork 改名是否覆盖内部符号与二进制名 | A) 只改路径与用户可见面（本文推荐） B) 全量改名 | A：`docs` 需解释进程名仍是 `zygiskd`。B：每次 rebase 上游约 100 处冲突，且 A3 判据要同时兼容旧名 |
| Q-3 | MuZygisk 的重启文案是否按 `pending_reboot` 切换（即接受 C-1 对 UI 的修改） | A) 接受，两档文案 B) UI 保持"可软重启"一句话 | A：前端多一个分支。B：用户软重启后发现没生效，会当成 bug |
| Q-4 | 内建元模块是否为 `vfs` 模式保留可能性（即是否打包 `vfs/binaries`，+960 KB） | A) 不打包，仅 overlay（本文推荐） B) 打包，详情区给模式选择 | A：`vfs` 模式的差异化能力用不上。B：ksud 体积再涨约 1 MB，且 VFS 与 zygisk 的交互是全新未验证面 |
| Q-5 | 是否允许"两者同时开启" | A) 允许（本文推荐，只加提示） B) 互斥 | A：用户可能让两个"接管型"功能叠加，出问题归因难。B：两个功能本可互补，强行互斥会让用户以为二选一 |

### 15.3 与需求描述不一致、需要澄清的一条

**Q-1（技术事实级）**：需求说"无元模块时核心用内置兜底挂载"。但在本次核实范围内，`kernel/`、`userspace/ksud`、`manager/` 对 `lowerdir` / `upperdir` / `overlayfs` 的引用**全部为零**，内核也不引用 `/data/adb/modules`（§1 F11）。这意味着：

- 若"内置兜底"确实存在，请指出实现位置（文件/函数），因为它决定内建元模块启用后**是否与它叠加**（叠加会导致同一目标双重挂载）。
- 若并不存在，则内建元模块的定位要改写成"**提供**目前缺失的模块挂载器"，而 §9.4 的"禁用后回到没有元模块的世界"实际等于"模块文件全都不挂载"——这会改变 UI 上"已关闭"的措辞重量。

---

## 16. 验收清单

- [ ] `ksud builtin list` 在两个组件均未启用时返回两个 `ui_state=off`，且 `available` 正确反映架构
- [ ] 目录布局符合 §2.1；`list_modules()` 输出中不含 `musu` 相关项
- [ ] 载荷版本戳与 ksud 版本不一致时自动重解压，且在用二进制不被半写（§2.3）
- [ ] `state.json` 被破坏后自动备份并回到"两个都关"
- [ ] 启动时序与 §5.1 一致，且任一新增调用失败后开机流程继续（连续 3 次人为制造失败验证）
- [ ] 互斥矩阵 A1/A3/A5/B1/B2 逐条构造场景验证拦截与放行
- [ ] 安装即启用的绕过路径被拦住（V-2）
- [ ] 内建元模块启用后 `/data/adb/metamodule` 指向内建目录，`metamount` 由既有代码触发（打日志确认）
- [ ] 内建元模块禁用后软链消失，下次开机走"无元模块"路径
- [ ] SELinux 无 avc denied（V-4/V-5）
- [ ] 许可证：`muzygisk/LICENSE` 为 AGPL、`NOTICE` 保留上游版权、`THIRD_PARTY.md` 有 Hybrid 条目、载荷内有 `LICENSE`
- [ ] 与 `builtin-ui.md` 的 7 条对齐项（§14）逐条确认，C-1/C-3 的文案变更已获前端确认
