# 内建组件 UI 规格：MuZygisk 与内建元模块

> 状态：设计契约（待主理人确认 3 项决策，见 §12）
> 范围：**只定义信息结构、状态语义、文案与视觉规则**。不含 Compose 实现，不改仓库源码。
> 读者：主理人（验收）、Kotlin 前端（按 §7 映射表落地）、QA（按 §5 矩阵验状态）

---

## 0. 一句话结论

这两个开关不是"再开一个功能"，而是**换掉一层实现**——所以它们不能长成设置页里两个普通的开关行，必须是两个能被"冲突"打断、且打断了还能告诉用户下一步去哪的独立页面。

---

## 1. 现状基线（读代码得到的事实，后面全部沿用）

| # | 事实 | 出处 | 对本设计的约束 |
|---|---|---|---|
| 1 | 设置页是**无组标题**的卡片堆叠，仅靠 13dp（Material）/ 12dp（Miuix）间隔分组 | `SettingsMaterial.kt:99`、`SettingsMiuix.kt:112` | 新增分组要不要加标题，是一个必须显式决策的视觉变化（见 §3.1） |
| 2 | 卡片圆角：外 16dp / 内 4dp，同组项间 2dp | `SegmentedList.kt:76-79` | 任何新容器不得引入第三个圆角值 |
| 3 | 设置页屏边距：Material 16dp，Miuix 12dp + 卡内 `insideMargin 16dp` | `SettingsMaterial.kt:99`、`SettingsMiuix.kt:105` | 两张主题的左边沿天然差 4dp，不能"统一"成一个值 |
| 4 | 「功能不可用」的既有表达 = **控件 `enabled=false` + 副标题换成原因**，副标题取 `feature_status_*_summary` | `SettingsMaterial.kt:185-190,218-232` | 冲突灰态**复用**这个模式，不发明新的灰态样式 |
| 5 | Miuix 侧禁用时 icon 要改 `colorScheme.disabledOnSecondaryVariant`，但现状不一致（`adbRoot` 的 icon 没跟着灰） | `SettingsMiuix.kt:298,342,357` vs `322` | 新页面强制统一：**开关灰 ⇒ icon 一起灰** |
| 6 | 语义色现成：`WarningLevel.Error / Notice` → `errorContainer / tertiaryContainer` | `WarningCard.kt:60-100` | 冲突卡不新增颜色 |
| 7 | `StatusTag` 两主题共用一套 API，圆角 4dp（Material）/ 6dp（Miuix），9sp/750（Miuix） | `StatusTagMaterial.kt`、`StatusTagMiuix.kt` | 状态只用一个 Tag，不叠多个徽章 |
| 8 | 组标题 = `SegmentedColumn(title=…)` → `titleSmallEmphasized` + primary，左沿 = 屏边 + 16dp = **与卡内正文对齐** | `SegmentedList.kt:111-117` | 若加组标题，左沿必须落在 32dp（Material）/ 28dp（Miuix） |

**未写进本节的事**：不设计 WebView、不重画模块列表（只在 §6.3 给出交给模块列表页的**文案契约**）。

---

## 2. 术语与命名

| 概念 | 对外文案 | 说明 |
|---|---|---|
| MuZygisk | **MuZygisk**（不翻译、不加副名） | 内建 Zygisk 实现，上游 ReZygisk 改名 |
| 内建元模块 | **内建元模块** / Built-in metamodule | 内建 Hybrid Mount 完整版；文案里首次出现时点明 `Hybrid Mount` |
| 内建组件 | 组标题「内建组件」/ Built-in components | 指"本来要装模块才能有、现在管理器自带"的东西 |
| 第三方实现 | 「其他实现」/ other implementation | 指 Zygisk Next、Magisk 的 Zygisk、Mountify、其他元模块。**文案里永远用具体名字**（`%1$s`），不用"其他实现"当显示文本 |

**禁用词**：`实现层`、`hook`、`overlayfs`、`namespace`、`zygote` 只允许出现在"详情区"的技术行，不允许出现在开关区与冲突区。冲突区是给"想知道为什么开不了"的人看的，不是给内核开发者看的。

---

## 3. 设置页的两个入口

### 3.1 位置：新增一组，放在「安全」组之后、「模块」组之前

理由（按优先级）：

1. **语义**：安全组的开关是"关掉某个会被检测到的行为"；这两个是"换掉一层实现"。混进安全组会让用户以为"又是一个规避开关"，而它一开就可能让设备里另一个模块失效——语义重量不同，不能同组。
2. **频率**：安全组是高频开关（SELinux 隐藏、SU 日志、ADB Root）；内建组件是一次性配置。低频不挡在高频前面，所以排在其后。
3. **邻近**：紧跟其后的是「模块默认卸载 / Web 调试 / 自动越狱」与「卸载」，都属于模块体系，视觉上连续。

> **视觉变化必须显式记账**：现有设置页**没有任何组标题**（事实 #1）。新组是唯一带标题的组。
> **我推荐加标题**：这两个条目一旦进入"被占用"状态，副标题会变长、还要承载"下一步去哪"，没有标题的一堆卡片里它会像一条错误提示而不像一个功能。
> **替代方案（若团队要求零视觉变化）**：不加标题，两个条目自成一组卡片，副标题前面固定加 `内建 · ` 前缀（例：`内建 · 已关闭 · 模块的 Zygisk 脚本不会被执行`）。代价是副标题多 5 个字，且"内建"要在两条里各说一次。

组件：`SegmentedColumn(title = "内建组件") + 2 项`（Material）/ `SmallTitle("内建组件") + Card + 2 项`（Miuix）。
左沿：Material 32dp（= 屏边 16 + 卡内 16），Miuix 28dp（= 屏边 12 + 卡内 16）。**标题必须与卡内正文左对齐**，不居中、不加大字号。

### 3.2 两个条目：箭头条目，不是开关

| 位置 | 元素 | Material | Miuix |
|---|---|---|---|
| 左 | 图标 24dp | `Icons.Filled.Memory` / `Icons.Filled.Layers` | `Icons.Rounded.Memory` / `Icons.Rounded.Layers` |
| 中 | 标题 14sp | `MuZygisk` / `内建元模块` | 同 |
| 中 | 副标题 12sp，`onSurfaceVariant` | 状态前缀 + 一句（见 §3.3） | `onSurfaceVariantSummary` |
| 右 | 箭头 24dp | `Icons.AutoMirrored.Filled.KeyboardArrowRight` | `ArrowPreference` 自带 |

**硬规则**：

- 设置页条目**右侧只有箭头**，不带开关、不带 `StatusTag`。开关只在独立页出现——两处开关必然状态分叉。
- 副标题**不上色**。状态靠前缀词识别（见 §3.3），不靠颜色。理由：列表行副标题一旦上色，整页会出现 4 种颜色的副标题，视觉噪音大于信息增益。
- 副标题 ≤ 2 行（中文 ≤ 40 字）。超过就压缩，把完整句子挪进独立页的冲突区。

**图标语义**（不新增图标库，沿用 Material Icons 一套；两个图标在现有代码里均未被占用）：

| 条目 | 图标 | 语义 | 为什么不用它旁边那个 |
|---|---|---|---|
| MuZygisk | `Memory`（芯片） | 进程内的实现，与"模块"是两回事 | 不用 `Extension`（拼图）——那是模块的语言，会让人以为它是个模块 |
| 内建元模块 | `Layers`（叠层） | 叠层挂载 | 不用 `LayersClear`——已被"内核 umount"占用（`SettingsMaterial.kt:209`），同屏两个"层"会混 |

### 3.3 副标题的前缀体系（只允许 5 个前缀）

| 前缀 | 用于 | 例子 |
|---|---|---|
| `已关闭 · ` | 未启用 | `已关闭 · 模块的 Zygisk 脚本不会被执行` |
| `已启用 · ` | 已开但未生效（待重启） | `已启用 · 重启后开始接管 Zygisk 模块` |
| `运行中 · ` | 已生效 | `运行中 · 已接管 3 个 Zygisk 模块` |
| `被「%1$s」占用 · ` | 被第三方实现阻塞 | `被「Zygisk Next」占用 · 需先停用它` |
| `不支持 · ` / `启动失败 · ` | 环境不支持 / 启动失败 | `启动失败 · 点状态区查看原因` |

`运行中` 与 `已启用` 必须分开：前者"现在就是好的"，后者"重启后才好"。合成一个"已开启"是这两页最容易犯的错。

---

## 4. 独立页面的信息结构（两页同构，从上到下）

页面骨架：Material = `ExpressiveScaffold` + `LargeFlexibleTopAppBar`（带返回）；Miuix = `Scaffold` + `TopAppBar` + `LazyColumn`（屏边 12dp）。**无底部导航、无 FAB**。

### 4.1 区块 A：总开关区（无标题，页面第一张卡，只含 1 行）

| 元素 | 内容 | 规则 |
|---|---|---|
| icon | 同设置页入口图标（24dp） | 禁用时随文字一起变灰 |
| 标题 | `MuZygisk` / `内建元模块` | 不与页面标题重复冗余信息 |
| 副标题 | 当前状态 + 下一步（见 §5 矩阵） | ≤ 2 行；灰态时必须含"可执行的那一步" |
| 右侧 | 开关（`ExpressiveSwitch` / Miuix 开关） | `checked` / `enabled` 按 §5 矩阵 |
| 热区 | 整行可点（沿用 `SegmentedSwitchItem` / `SwitchPreference` 的既有行为） | 高 ≥ 56dp |

### 4.2 区块 B：状态区（组标题「状态」/ Status）

固定 3 行 + 1 行条件行。溢出内容（如模块清单）不做，链接到模块列表。

**MuZygisk 页**

| 行 | 标题 | 值 | 备注 |
|---|---|---|---|
| B1 | 运行状态 | `StatusTag`（运行中 / 待重启 / 已关闭 / 启动失败 / 被占用 / 未知） | Tag 在行尾；同一信息必须在可被 TalkBack 读到的文本里复述一次（见 §8） |
| B2 | 注入 | `已注入 zygote64` / `未注入` / `注入异常` | 取不到时显示 `—`，不编造 |
| B3 | Zygisk 模块 | `3 个已加载，共 5 个` | 0 个时写 `没有安装 Zygisk 模块`，不写"暂无数据" |
| B4 | 需要重启（**条件行**） | `是 · 关闭后生效` + 行尾文字按钮「立即重启」 | **只在需要重启时出现**。不需要重启时整行隐藏——一行常驻的"否"是噪音 |

**内建元模块 页**

| 行 | 标题 | 值 | 备注 |
|---|---|---|---|
| B1 | 运行状态 | 同 B1 | |
| B2 | 挂载 | `8 个模块已挂载，0 个失败` | 有失败时：`8 个已挂载，3 个失败`，此时该行文字用 `error` |
| B3 | 接管范围 | `/system · /vendor · /product`（+ 副行 `模块文件覆盖同名系统文件，原文件不被修改`） | 这是 Hybrid Mount 的差异化能力，必须写出来，否则用户不知道它比旧方案强在哪 |
| B4 | 需要完整重启（**条件行**） | `是 · 开机挂载未执行` + 「立即重启」 | 文案固定为**完整重启**，不许写"重启"——软重启在这里无效（见 §9.4） |

### 4.3 区块 C：冲突区（仅在 S6 / S7 / S5 / S9 出现）

位置：紧接状态区之后、详情区之前。**不设组标题**——语义色卡片自己就承担了分区职责，再加标题会重复。

| 场景 | 卡片 | 正文 | 动作 |
|---|---|---|---|
| S6 被占用（开关灰） | Error | `「Zygisk Next 4.0.1」正在提供 Zygisk 功能，MuZygisk 无法同时运行。要使用 MuZygisk，请先在模块列表停用 Zygisk Next，然后重启设备。` | 「去模块列表」→ 模块 Tab |
| S7 自身阻塞他人（开关正常） | Notice | `MuZygisk 正在运行，因此「Zygisk Next」在模块列表里无法启用。要使用它，请先关闭 MuZygisk 并重启。` | 无（当前页不需要动作） |
| S5 启动失败 | Error | `MuZygisk 启动失败（错误码 %1$d）。` | 「查看日志」→ 现有 SU 日志页 |
| S9 读不到状态 | Error | `读不到运行状态。` | 「重试」 |

**硬规则**：

- 表中 `Zygisk Next 4.0.1` 只是**示例值**，实际显示取运行时的模块名与版本（`%1$s` / `%2$s`），设计不硬编码任何第三方模块的版本号。
- 冲突区正文必须出现**具体名字 + 版本号**（`%1$s` 取模块名，`%2$s` 取版本），不许写"存在冲突的模块"。
- 处理建议必须是**用户能点的或能照着做的一步**（"停用某模块 + 重启"），不许写"请检查模块冲突"。
- 冲突卡只有 1 个动作。两个动作（"去模块列表"+"查看日志"）会让卡片变成工具栏。
- S7 用 Notice 不用 Error：它不是错误，是"你正占着位置"。用红色会让用户以为坏了。

### 4.4 区块 D：详情区（组标题「详情」/ Details）

只读行，无开关。值全部运行时读取，**设计只定义行名、格式与来源，不硬编码任何版本号/许可证/大小**。

| 行 | 值格式 | 来源 | 备注 |
|---|---|---|---|
| 类型 | `内建组件 · 不显示在模块列表` | 固定文案 | 专门回答"我为什么在模块列表找不到它" |
| 版本 | `1.2.3（内建）` | 打包元数据 | 括号里固定"内建" |
| 上游 | `ReZygisk` / `Hybrid Mount` | 打包元数据 + 链接 | 有链接时行尾给链接按钮（`FilledTonalButton` + `Icons.Outlined.Link` / Miuix `IconButton` + `MiuixIcons.Link`），沿用 `ModuleRepoMaterial.kt:966-998` 的既有样式 |
| 许可证 | SPDX 标识，如 `GPL-3.0` | 打包元数据 | 取不到就整行隐藏，**不许写"未知"** |
| 占用空间 | `12.4 MB`（<1 GB 保留 1 位小数；≥1 GB 用 `X.X GB`） | 运行时统计 | 用 `NumberFormat` 格式化；副行写 `卸载或更新时自动清理` |

---

## 5. 状态枚举 × 开关可用性 × 文案（主矩阵）

### 5.1 状态枚举

| ID | 状态 | 定义 | 是否终态 |
|---|---|---|---|
| S0 | `Probing` 探测中 | 页面刚打开，正在读运行状态（≤ 2s） | 否，超时转 S9 |
| S1 | `Unsupported` 环境不支持 | 内核/框架缺接口（如无 overlayfs、zygote 结构不匹配） | 是（需换内核） |
| S2 | `Off` 未启用 | 用户关着，且次态干净（无残留进程/挂载） | 是 |
| S3 | `PendingEnable` 已启用待重启 | 开关已置位，运行层尚未接管 | 否，重启转 S4 |
| S4 | `Active` 运行中 | 进程注入成功 / 挂载完成且校验通过 | 是 |
| S5 | `Failed` 启动失败 | 已置位但启动/挂载失败，带错误码 | 否，可重试或关闭 |
| S6 | `BlockedByOther` 被其他实现阻塞 | 检测到另一个同类实现处于启用态 | 否，等用户处理对方 |
| S7 | `BlockingOther` 自身阻塞其他 | 自身 S4 成立，且检测到另一个同类实现因此不可用 | 否，是 S4 的**叠加标记**，不是并列状态 |
| S8 | `PendingDisable` 已关闭待重启 | 开关已归零，进程/挂载仍在 | 否，重启转 S2 |
| S9 | `ReadError` 读不到状态 | 探测失败（权限、系统服务不可用） | 否，可重试 |

> **关键判断**：S7 不是独立状态，是 `S4 + suppressedOthers > 0` 的显示层叠加。因此它**不占用独立 Tag 文案**（Tag 仍显示「运行中」），只在冲突区加一张 Notice 卡。
> 若把 S7 做成独立 Tag（如「独占运行」），用户会以为出了新问题，而实际上一切正常——只是别人被挡住了。

### 5.2 主矩阵

`checked` = 开关显示位置；`enabled` = 是否可点；灰态三行（S1/S6/S9）必须给出可执行下一步。

| ID | Tag 文案 | checked | enabled | Tag 配色（Material / Miuix 同名 token） | 开关区副标题（中文） | 冲突区 | 用户下一步 |
|---|---|---|---|---|---|---|---|
| S0 | 无（骨架 3 条） | 上次值 | **false** | — | `正在读取状态…` | 无 | 等 2s，超时进 S9 |
| S1 | 不支持 | false | **false** | `surfaceVariant` / `onSurfaceVariant` | `不支持 · 当前内核缺少 Zygisk 所需接口`（元模块：`不支持 · 当前内核不支持 overlayfs 挂载`） | 无 | 换内核；现状下仍可用第三方实现 |
| S2 | 已关闭 | false | true | `surfaceContainerHighest` / `onSurfaceVariant` | `已关闭 · 模块的 Zygisk 脚本不会被执行` | 无 | 打开开关 |
| S3 | 待重启 | true | true | `tertiaryContainer` / `onTertiaryContainer` | `已启用 · 重启后开始接管 Zygisk 模块` | 无 | 状态区「立即重启」 |
| S4 | 运行中 | true | true | `primary` / `onPrimary` | `运行中 · 已接管 3 个 Zygisk 模块` | 无 | 无 |
| S5 | 启动失败 | true | true | `error` / `onError` | `启动失败 · 点状态区查看原因` | Error 卡 +「查看日志」 | 看日志→关闭或重试 |
| S6 | 被占用 | false | **false** | `errorContainer` / `onErrorContainer` | `被「Zygisk Next」占用 · 需先在模块列表停用它` | Error 卡 +「去模块列表」 | 停用对方→重启→再开 |
| S7 | 运行中 | true | true | `primary` / `onPrimary` | `运行中 · 已接管 3 个 Zygisk 模块`（同 S4） | Notice 卡（无动作） | 想用对方：关本开关→重启 |
| S8 | 待重启 | false | true | `tertiaryContainer` / `onTertiaryContainer` | `已关闭 · 重启后彻底停止`（元模块：`已关闭 · 完整重启后停止挂载`） | 无 | 状态区「立即重启」 |
| S9 | 未知 | 上次值 | **false** | `surfaceVariant` / `onSurfaceVariant` | `读不到运行状态 · 点状态区重试` | Error 卡 +「重试」 | 重试；仍失败看日志 |

### 5.3 三档颜色的排序依据

不是"好看"，是**用户需要多快动手**：

| 档 | 含义 | token | 用在哪 |
|---|---|---|---|
| 1（最重） | 坏了 / 堵住了，必须现在处理 | `error` 实心 + `onError`（S5）/ `errorContainer` + `onErrorContainer`（S6、S9 卡） | 失败、被占用、读不到 |
| 2 | 等你重启，步骤已知 | `tertiaryContainer` + `onTertiaryContainer`（S3、S8） | 待重启 |
| 3 | 正常 / 无关 | `primary` + `onPrimary`（S4、S7）/ `surfaceVariant`（S1、S2、S9 Tag） | 运行中、已关闭、不支持 |

- **不引入绿色**。项目里没有 success 语义色，`primary` 就是"正常"（`META` 徽章已用 `primary` + `onPrimary`，见 `ModuleRepoMaterial.kt:303-307`）。新造一个绿色会让状态标签多出一个只有它自己用的颜色。
- S5 用 `error` 实心、S6 用 `errorContainer`：前者"这东西坏了"，后者"这东西没坏，位置被占了"。同色不同重。
- 上表 token 在 Miuix 主题下**全部存在**（`errorContainer`、`onErrorContainer`、`tertiaryContainer`、`onTertiaryContainer`、`primary`、`onPrimary`、`surfaceVariant`、`onSurfaceVariant` 均有）。**两套主题的差异不在颜色，在容器与排版**（见 §7）。

---

## 6. 冲突规则（双向阻塞）

### 6.1 判定顺序（决定显示哪个状态）

```
探测中(S0) → 读不到(S9) → 环境不支持(S1) → 其他实现已启用(S6)
  → 自身已置位但启动失败(S5) → 自身生效(S4，+S7 叠加) → 待重启(S3/S8) → 未启用(S2)
```

**S6 排在 S5 之前**：如果用户开着第三方 Zygisk，本实现的启动失败大概率只是"没启动"，报"失败"会让人去查日志，而真正原因是"轮不到它"。先报被占用，用户才不会白折腾。

### 6.2 两边都不许自动操作对方

| 场景 | 允许 | 禁止 |
|---|---|---|
| S6（对方先开，我想开） | 灰掉本开关 + 说明 + 「去模块列表」 | 自动停用对方模块；弹窗问"要不要帮你停用" |
| S7（我先开，对方后到） | 对方在模块列表里灰掉 + 一句原因 | 自动把对方标记为"已停用"或改动它的配置 |

理由：自动停用他人模块 = 替用户改设备状态，且失败时用户不知道该怪谁。设计上只做"告知 + 指路"，动作由用户在模块列表里做——那个页面本来就有停用开关。

### 6.3 交给模块列表页的文案契约（不在本页实现）

当 S7 成立时，模块列表里该模块的开关必须是灰态，副标题固定：

- 中文：`MuZygisk 正在运行，停用它后才能启用此模块`
- 英文：`MuZygisk is running. Turn it off to enable this module.`
- 元模块版：`内建元模块正在运行，停用它后才能启用此模块`

这条只是文案契约，视觉与布局由模块列表页负责。**若模块列表页暂不实现**，必须在 §12 登记为已知缺口——否则用户会看到"能点但点了没反应"，比不灰更糟。

---

## 7. Material / Miuix 组件映射（主交付表 2）

原则：**同一个信息，两套主题各用自己语言里最"原生"的容器；语义（状态、颜色、文案、顺序）完全一致。**

| 区域 | Material3 | Miuix | 差异说明 |
|---|---|---|---|
| 页面骨架 | `ExpressiveScaffold` + `LargeFlexibleTopAppBar`（`expressiveTopAppBarColors`）+ `Column.verticalScroll` | `Scaffold` + `BlurredBar{TopAppBar}` + `LazyColumn`（`overScrollVertical` / `scrollEndHaptic`） | Material 用滚动折叠大标题，Miuix 用固定栏 + 模糊 |
| 屏边距 | 16dp | 12dp（LazyColumn `padding(horizontal = 12.dp)`） | **不统一**，各自原生 |
| 分组容器 | `SegmentedColumn`（`surfaceBright`，外 16 / 内 4，项间 2dp） | `Card`（默认圆角与 `insideMargin`，不覆写） | Material 是"分段列表"，Miuix 是"卡片"，行间不用分割线 |
| 组标题 | `SegmentedColumn(title)` → `titleSmallEmphasized` + `primary` | `SmallTitle` | 左沿都对到卡内正文（32dp / 28dp） |
| 卡间距 | `bottom = 13.dp` | `top = 12.dp` | 沿用现状，不统一成 12 或 16 |
| 总开关行 | `SegmentedSwitchItem(icon, title, summary, checked, enabled)` + `ExpressiveSwitch` | `SwitchPreference(title, summary, startAction=Icon, checked, enabled)` | Miuix 的 icon 在 `startAction`，需自行 `padding(end = 6.dp)` |
| 状态行（只读） | `SegmentedListItem`（`headline` + `supporting` + `trailing=StatusTag`），无 onClick | `InfoRowMiuix` 风格：`Row` 内 `Column(weight 1f)` + 12sp 值 + `HorizontalDivider(0.5.dp)` 分隔 | Miuix 卡内用 0.5dp 分割线是既有语言（`ModuleRepoMiuix.kt:1104-1125`），不改成无分割线 |
| 状态标签 | `StatusTag`（4dp 圆角，`labelSmallEmphasized`） | `StatusTagMiuix`（6dp 圆角，9sp/750） | API 相同，内部不同；**调用方不传字号/圆角** |
| 冲突卡 | `TonalCard(containerColor = errorContainer 或 tertiaryContainer, contentColor = onErrorContainer / onTertiaryContainer)` | `WarningCard(level = Error 或 Notice)` | Miuix 用现成 `WarningCard`；Material 侧用 `TonalCard` 而非 `WarningCard`——后者是 Miuix 风格卡片（`PressFeedbackType.Sink`、Miuix 圆角），放进 M3 页面会两种圆角并存 |
| 详情行 | `SegmentedListItem`（只读，`bodyMedium` 值 + `supporting`）+ 行尾链接 `FilledTonalButton` | `InfoRowMiuix`（14sp 标题 / 12sp 值 + 行尾 `IconButton`） | 见 `ModuleRepoMaterial.kt:966-998` / `ModuleRepoMiuix.kt:1061-1102` |
| 文字按钮（「立即重启」「重试」） | `TextButton`（M3） | `Text` + `Modifier.clickable` 包一层（Miuix 无对应小按钮） | 高 ≥ 48dp |
| 加载骨架 | 3 条 `Box` 圆角 4dp / `surfaceContainerHighest` | 3 条 `Box` 圆角 6dp / `surfaceVariant` | 只在首帧，≤ 2s |
| 重启动作 | 复用现有 `RebootListPopup`（已有 portal 与层级） | 同 | **不新建浮层** |

**一句话**：两套主题之间只有 4 处真实差异——屏边距（16 / 12）、分组容器（分段列表 / 卡片）、组标题组件、冲突卡组件。状态、颜色、文案、顺序、判定逻辑**零差异**。

---

## 8. 视觉规则（可量化的部分）

### 8.1 间距：只允许 6 个值

| 值 | 用在哪 |
|---|---|
| 2dp | 同组相邻项之间（`SegmentedColumn` 内部） |
| 4dp | `StatusTag` 内竖向 |
| 8dp | 组标题下方；Miuix 卡内信息行之间的分割线上下 |
| 12dp | Miuix 卡间距、Miuix 屏边距 |
| 13dp | Material 卡间距 |
| 16dp | 卡内边距、Material 屏边距 |

**禁止引入 24 / 32 / 48 等"更大留白"**。理由：本页是目录式信息页，不是营销页；一旦出现 24dp，同一屏里两种节奏，用户会觉得某些区块"掉了"。

### 8.2 字号：4 档，不覆写

| 档 | 值 | 用于 |
|---|---|---|
| 标题 | 14sp（Material `bodyLarge` 默认 / Miuix `14.sp`） | 条目名、开关名、行标题 |
| 副标题/值 | 12sp（`onSurfaceVariant` / `onSurfaceVariantSummary`） | 状态说明、详情值 |
| Tag（Material） | `labelSmallEmphasized`（≈11sp） | StatusTag |
| Tag（Miuix） | 9sp / `FontWeight(750)` | StatusTagMiuix |

**不覆写组件默认字号**：所有 4 档都来自现有组件默认值。9sp 已经是可读性下限，因此 Tag 文本 ≤ 3 个汉字（`运行中`、`待重启`、`被占用`），且同一信息必须在正文里再出现一次（见 8.5）。

### 8.3 圆角：只允许 3 个值

| 值 | 用于 |
|---|---|
| 4dp | Material 卡片内段、Material StatusTag |
| 6dp | Miuix StatusTag |
| 16dp | Material 卡片外框 |

Miuix 卡片圆角用库默认值，**不覆写**。

### 8.4 边距 / 横向

Material 16dp、Miuix 12dp（+ 卡内 16dp）。**无最大内容宽度**——沿用现状（不限宽）。平板上会拉满，这是项目既有行为；若要限宽（如 600dp 居中），是全局改动，登记在 §12 而非本页单独改。

### 8.5 可访问性（设计阶段就定死）

| 项 | 规则 |
|---|---|
| 对比度 | 不新增颜色 ⇒ Tag 前景/背景全部继承 M3 语义 token（`onError/error` 等），无需新增对比度测试 |
| 灰态不只靠颜色 | 灰态时**副标题必须换成原因文案**（复用事实 #4 的模式），并同时把 icon 变灰（事实 #5） |
| Tag 的朗读 | `StatusTag` 是 `Text`，会被 TalkBack 读到；但 9sp 视觉上限 3 字 ⇒ B1 行必须另有完整文字（如 `已注入 zygote64`），不允许只有 Tag |
| 焦点顺序 | 开关行 → 状态区各行（不可点则跳过）→ 冲突卡动作 → 详情区链接。冲突卡出现/消失时焦点不迁移（它不在 Tab 首位） |
| 触控 | 所有可点行高 ≥ 56dp；文字按钮 ≥ 48dp 高、≥ 48dp 宽 |
| 动效降级 | 系统关闭动画时：骨架停止微光（改静态灰块）、冲突卡直接出现（无淡入）、开关仍保留系统自带的即时切换 |

### 8.6 图标

- 只有 2 个功能图标（`Memory` / `Layers`），24dp，Material 用 Filled、Miuix 用 Rounded —— 与全项目一致。
- **状态标签不带图标**，冲突卡不带图标。理由：一个页面里出现第 3 种图标语义（状态图标）会和功能图标抢注意力，且状态靠色彩+文字已足够。
- 禁止 emoji，禁止彩色插画，禁止 WebView。

---

## 9. 交互与反馈决策（每页必须明确的三项）

### 9.1 反馈形态：就地，不用 Toast / Snackbar

开关结果**绑定在这个开关上**，且状态区就在同一屏 → 就地更新（开关位置 + 副标题 + B1 行 + 冲突区）。
失败时不弹任何全局提示：开关回到原值，副标题变为 S5 文案，冲突区出现 Error 卡。理由：Toast 一飘走，用户只剩下"我刚才点的那下到底成没成"。

### 9.2 加载形态：只做骨架，不做 spinner

- 首帧：开关行显示上次值（灰）+ 状态区 3 条骨架（S0）。
- 只有这一处加载；重试（S9）时**不清空已显示的值**，只在 B1 行尾显示 3 点脉冲，避免整页闪白。

### 9.3 浮层：0 个新浮层

本页不需要下拉、菜单、气泡。唯一可能的浮层是「立即重启」的多选菜单——**复用现有 `RebootListPopup`**，不新建。若决定只做完整重启，则退化为行内文字按钮，连浮层都不需要。

### 9.4 「待重启」不是同一件事（这是最容易画错的地方）

| 页面 | 重启方式 | 文案 | 理由 |
|---|---|---|---|
| MuZygisk | 可走**软重启**（尊重现有「软重启代替重启」偏好） | `重启后生效` | 依赖 zygote 重启，软重启会重建 zygote |
| 内建元模块 | **必须完整重启**，且**忽略**软重启偏好 | `完整重启后开始挂载` / `完整重启后停止挂载` | 挂载发生在开机阶段，软重启不重新执行开机挂载；用户以为生效了但实际没有，这比不生效更糟 |

> 若 kernel 侧确认"元模块可通过重新触发 preinit 生效"，则此项可放宽——**列入 §12 待确认项，不自行放宽**。

### 9.5 防连点

开关点击后立即 `enabled = false` 直到下层返回（≤ 3s 超时转 S9）。**不做乐观更新**：这两个开关会真的改变模块行为，先翻绿再翻回来，用户会认为"开了又坏了"。

### 9.6 无对话框

开启/关闭都不弹确认框。状态全部可逆（再点一次或重启），且操作后果在副标题与冲突区已经写清。加确认框只会让"试一下"的成本变高。

---

## 10. 文案表（zh-rCN / en，键名建议 `builtin_*`）

键名沿用项目既有 `settings_*` / `feature_status_*` 风格；英文放 `values/`，中文放 `values-zh-rCN/`。zh-rTW / zh-rHK / ja 需同步补（当前这三份文件未覆盖全部键，属既有缺口，不阻塞本功能）。

### 10.1 结构文案

| key | zh-rCN | en |
|---|---|---|
| `builtin_group` | 内建组件 | Built-in components |
| `builtin_section_status` | 状态 | Status |
| `builtin_section_detail` | 详情 | Details |
| `builtin_zygisk_title` | MuZygisk | MuZygisk |
| `builtin_meta_title` | 内建元模块 | Built-in metamodule |
| `builtin_tag_running` | 运行中 | Running |
| `builtin_tag_pending_reboot` | 待重启 | Reboot needed |
| `builtin_tag_off` | 已关闭 | Off |
| `builtin_tag_failed` | 启动失败 | Failed |
| `builtin_tag_blocked` | 被占用 | In use |
| `builtin_tag_unknown` | 未知 | Unknown |
| `builtin_tag_unsupported` | 不支持 | Unsupported |

### 10.2 设置页入口副标题

| key | zh-rCN | en |
|---|---|---|
| `builtin_zygisk_summary_off` | 已关闭 · 模块的 Zygisk 脚本不会被执行 | Off · Zygisk scripts in modules will not run |
| `builtin_zygisk_summary_pending` | 已启用 · 重启后开始接管 Zygisk 模块 | On · Takes over Zygisk modules after reboot |
| `builtin_zygisk_summary_active` | 运行中 · 已接管 %1$d 个 Zygisk 模块 | Running · %1$d Zygisk modules loaded |
| `builtin_zygisk_summary_blocked` | 被「%1$s」占用 · 需先停用它 | In use by "%1$s" · Disable it first |
| `builtin_summary_failed` | 启动失败 · 点状态区查看原因 | Failed · Open status for details |
| `builtin_zygisk_summary_unsupported` | 不支持 · 当前内核缺少 Zygisk 所需接口 | Unsupported · Kernel lacks the required Zygisk interfaces |
| `builtin_summary_unknown` | 读不到运行状态 · 点状态区重试 | Status unavailable · Open status to retry |
| `builtin_meta_summary_off` | 已关闭 · 模块文件不会挂载进系统 | Off · Module files are not mounted into the system |
| `builtin_meta_summary_pending` | 已启用 · 完整重启后开始挂载 | On · Mounting starts after a full reboot |
| `builtin_meta_summary_active` | 运行中 · 已挂载 %1$d 个模块，%2$d 个失败 | Running · %1$d modules mounted, %2$d failed |
| `builtin_meta_summary_blocked` | 被「%1$s」占用 · 需先停用它 | In use by "%1$s" · Disable it first |
| `builtin_meta_summary_unsupported` | 不支持 · 当前内核不支持 overlayfs 挂载 | Unsupported · Kernel does not support overlayfs mounts |
| `builtin_meta_summary_pending_disable` | 已关闭 · 完整重启后停止挂载 | Off · Mounting stops after a full reboot |

`builtin_summary_failed` 与 `builtin_summary_unknown` 两页共用（文案不含名字，不需要按页拆）。

### 10.3 状态区 / 冲突区 / 详情区

| key | zh-rCN | en |
|---|---|---|
| `builtin_status_label` | 运行状态 | Status |
| `builtin_zygisk_inject` | 注入 | Injection |
| `builtin_zygisk_inject_ok` | 已注入 zygote64 | Injected into zygote64 |
| `builtin_zygisk_inject_none` | 未注入 | Not injected |
| `builtin_zygisk_modules` | Zygisk 模块 | Zygisk modules |
| `builtin_zygisk_modules_value` | %1$d 个已加载，共 %2$d 个 | %1$d of %2$d loaded |
| `builtin_zygisk_modules_empty` | 没有安装 Zygisk 模块 | No Zygisk modules installed |
| `builtin_meta_mount` | 挂载 | Mounts |
| `builtin_meta_mount_value` | %1$d 个模块已挂载，%2$d 个失败 | %1$d mounted, %2$d failed |
| `builtin_meta_scope` | 接管范围 | Scope |
| `builtin_meta_scope_hint` | 模块文件覆盖同名系统文件，原文件不被修改 | Module files shadow system files; originals are untouched |
| `builtin_reboot_needed` | 需要重启 | Reboot needed |
| `builtin_reboot_needed_full` | 需要完整重启 | Full reboot needed |
| `builtin_reboot_now` | 立即重启 | Reboot now |
| `builtin_conflict_blocked_by` | 「%1$s %2$s」正在提供 Zygisk 功能，MuZygisk 无法同时运行。要使用 MuZygisk，请先在模块列表停用 %1$s，然后重启设备。 | "%1$s %2$s" already provides Zygisk. MuZygisk cannot run at the same time. Disable %1$s in the module list, then reboot. |
| `builtin_conflict_blocking` | MuZygisk 正在运行，因此「%1$s」在模块列表里无法启用。要使用它，请先关闭 MuZygisk 并重启。 | MuZygisk is running, so "%1$s" cannot be enabled in the module list. Turn MuZygisk off and reboot to use it. |
| `builtin_conflict_goto_modules` | 去模块列表 | Open module list |
| `builtin_failed` | %1$s 启动失败（错误码 %2$d）。 | %1$s failed to start (error %2$d). |
| `builtin_view_log` | 查看日志 | View log |
| `builtin_read_failed` | 读不到运行状态。 | Status unavailable. |
| `builtin_retry` | 重试 | Retry |
| `builtin_probing` | 正在读取状态… | Reading status… |
| `builtin_detail_type` | 类型 | Type |
| `builtin_detail_type_value` | 内建组件 · 不显示在模块列表 | Built-in · Hidden from the module list |
| `builtin_detail_version` | 版本 | Version |
| `builtin_detail_version_value` | %1$s（内建） | %1$s (built-in) |
| `builtin_detail_upstream` | 上游 | Upstream |
| `builtin_detail_license` | 许可证 | License |
| `builtin_detail_size` | 占用空间 | Disk usage |
| `builtin_detail_size_hint` | 卸载或更新时自动清理 | Removed automatically on uninstall or update |

### 10.4 文案纪律

- 不许出现「已开启」三个字单独成句——必须带"重启后"或"运行中"。
- 不许出现「发生错误」「操作失败」这类无主语套话——失败必须带错误码或具体模块名。
- 「重启」在元模块页一律写「完整重启」。
- 占位符 `%1$s` 取不到值时，整行/整卡隐藏，**不许显示「未知」「-」**（S9 的 Tag 例外，因为它本身就是"读不到"的语义）。

---

## 11. 禁止项与自检

### 11.1 禁止项

| 禁止 | 原因 |
|---|---|
| emoji 作功能图标 | 全项目禁用；本页只用 `Memory` / `Layers` 两个 Material Icons |
| 紫→粉渐变、渐变文字、发光 | 与 root 工具类应用的克制感冲突 |
| WebView / 网页式布局 / 内嵌 HTML | 需求明确要求原生；且这两页需要跟随主题与无障碍 |
| 空洞占位（`功能开发中`、`敬请期待`、`暂无数据`） | 每一行都要有真实语义；取不到就隐藏 |
| 在设置页条目上放开关或 Tag | 两处开关必然状态分叉 |
| 新增颜色 / 新增圆角 / 新增间距值 | 只允许 §8 的刻度，否则两张皮 |
| 自动停用用户的其他模块 | 只告知 + 指路，不替用户改设备状态 |
| 乐观更新开关 | 失败回滚会被读成"开了又坏了" |
| 状态标签叠两个（如「运行中」+「独占」） | 一个 Tag，一个信息 |
| 用「其他实现」当显示文本 | 必须写具体模块名与版本 |

### 11.2 交付前自检

- [ ] 5 个状态（S1/S6/S9 三个灰态 + S5/S8 两个"能点但有话要说"）都在真机上走过一遍，副标题与冲突卡文案对得上
- [ ] 灰态下开关、icon、副标题三者同时变化（不留"图标还亮着"的旧问题）
- [ ] 副标题在中文 40 字、英文 2 行内不溢出、不换 3 行
- [ ] 两套主题都验证：状态、文案、顺序一致，只有容器与屏边距不同
- [ ] TalkBack 走查：Tag 之外的正文能读出完整状态；灰态原因被读到
- [ ] 关闭动画时骨架不闪、冲突卡直接出现
- [ ] 全页零 emoji、零硬编码颜色、零 `px` 级魔数（只用 §8 刻度）
- [ ] 元模块页的重启动作是完整重启，不受「软重启」偏好影响

---

## 12. 需要主理人确认的 3 件事

| # | 决策 | 我的建议 | 代价 |
|---|---|---|---|
| D1 | 新分组**要不要加组标题**（现有设置页全页无组标题） | **加**（「内建组件」）。冲突态副标题较长，无标题的卡片会像一条错误提示 | 全页只有这一组有标题，视觉上像特例；彻底一致要给所有组补标题（更大改动） |
| D2 | 元模块「待重启」是否必须完整重启 | **必须**，且不跟随软重启偏好 | 若 kernel 侧能做到热重载，这条可放宽，但需 kernel 开发确认后再改文案 |
| D3 | 模块列表页是否接受 §6.3 的灰态文案契约（S7 时对方模块变灰） | 接受，且与本期同批 | 若模块列表延后，用户会遇到"能点但无反应"，比不灰更差——需登记为已知缺口 |

未决期间，本规格按"建议"列执行；任何一项被否，改动只落在对应节（D1→§3.1，D2→§9.4，D3→§6.3），不影响 §5 状态矩阵。
