---
description: MuZygisk 是 MuSu Next 内建的 Zygisk 实现，源自 ReZygisk，默认禁用，在设置中用原生界面管理。
---

# MuZygisk

MuZygisk 是 MuSu Next 内建的 [Zygisk](https://github.com/topjohnwu/Magisk) 实现，源自 [ReZygisk](https://github.com/PerformanC/ReZygisk)，针对 MuSu Next 重新编译并改名。启用后，无需刷入单独的 Zygisk 实现即可使用 Zygisk 模块。

> MuZygisk **默认禁用**。开启后需要**完整重启**才能生效。

## 在哪里找到它

MuZygisk **不会**出现在模块列表中。请打开管理器的「设置」页面，从中进入「MuZygisk」。管理界面为纯原生界面（非 WebUI），顶部有唯一的总开关。

## 状态说明

| 状态 | 含义 | 开关 |
|---|---|---|
| 未启用 | 未开启，当前没有 Zygisk 注入 | 可操作 |
| 已启用 · 待重启 | 已打开，等待完整重启后生效 | 可操作 |
| 运行中 | Zygisk 守护进程已启动并正在注入 | 可操作（关闭以停用） |
| 被占用 | 设备上已安装其他 Zygisk 实现 | 置灰不可开 |
| 无可用载荷 | 当前架构没有 MuZygisk 构建（如 riscv64） | 置灰不可开 |

## 互斥规则

MuZygisk 不能与其他 Zygisk 实现（如 Zygisk Next，或内核内置的 Zygisk）共存。

- 如果设备上已有其他实现，MuZygisk 开关会**置灰**，页面会点名冲突的模块，并提供一键前往模块列表停用的入口。
- 如果 MuZygisk 已经开启，那些实现也无法从模块列表启用——它们的开关同样会被置灰。

这是硬性互斥：同时运行两个 Zygisk 实现会导致双重注入与开机失败。

## 启用与禁用

1. 打开「设置 → MuZygisk」。
2. 阅读顶部状态卡，如有冲突请先处理。
3. 拨动开关。
4. 执行**完整重启**——软重启不会重启 Zygisk 守护进程。

关闭 MuZygisk 后，守护进程与运行文件会在下一次完整重启时清理。

## 与 ReZygisk 的关系

MuZygisk 源自 PerformanC 的 ReZygisk，采用 **AGPL-3.0-or-later** 许可。本 fork 将存储路径改为 MuSu Next 自有目录，去除模块安装脚本与 WebUI，并加入对 MuSu Next 管理器的识别。MuSu Next 用户态仍为 GPL-3.0-or-later；组合分发由 GPLv3 第 13 节允许，AGPL 的网络交互条款对组合作品生效。详见源码树的 `muzygisk/LICENSE` 与 `muzygisk/NOTICE`。
