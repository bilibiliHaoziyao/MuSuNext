---
description: MuMount 是 MuSu Next 内建的元模块，基于 Hybrid Mount，默认禁用，在设置中用原生界面管理。
---

# MuMount

MuMount 是 MuSu Next 内建的元模块，基于 [Hybrid Mount](https://github.com/Hybrid-Mount/meta-hybrid_mount)。元模块接管所有其他模块文件挂载进系统的方式，相比默认方式提供更灵活的挂载策略（OverlayFS、Magic Mount 与 VFS）。

> MuMount **默认禁用**。开启后需要**完整重启**才能生效。

## 在哪里找到它

与 MuZygisk 一样，MuMount **不会**出现在模块列表中。请打开「设置 → MuMount」。管理界面为纯原生界面（非 WebUI），顶部有唯一的总开关。

## 状态说明

| 状态 | 含义 | 开关 |
|---|---|---|
| 未启用 | 未开启，使用默认挂载方式 | 可操作 |
| 已启用 · 待重启 | 已打开，等待下次开机接管挂载 | 可操作 |
| 运行中 | 挂载已生效，页面显示成功与失败的模块数 | 可操作 |
| 被占用 | 设备上已安装其他元模块 | 置灰不可开 |
| 不支持 | 当前内核不支持所需的 overlay 挂载 | 置灰不可开 |

## 互斥规则

MuMount 不能与其他元模块共存。

- 如果设备上已有其他元模块，MuMount 开关会**置灰**，页面点名冲突的元模块，并提供停用入口。
- 如果 MuMount 已开启，其他元模块也无法启用——它们会被同样拦截。

## 接管范围

启用后，MuMount 会将模块文件覆盖到同名系统文件之上，而不修改原始系统文件。状态页显示挂载范围（如 `/system`、`/vendor`、`/product`）、成功挂载的模块数与失败数。高级挂载行为由配置文件驱动：MuSu Next 会在首次启用时生成该文件，此后不再覆盖用户的修改。

MuMount 可以与 MuZygisk 同时开启——两者相互独立，只给出提示，不算冲突。

## 启用与禁用

1. 打开「设置 → MuMount」。
2. 检查冲突，如有则先处理。
3. 拨动开关并**完整重启**。

禁用后，已挂载的模块文件会保留到下次重启。

## 与 Hybrid Mount 的关系

MuMount 基于 Hybrid Mount Developers 的 Hybrid Mount，采用 **GPL-3.0-only** 许可。MuSu Next 原样打包上游的二进制、脚本与内核模块，仅更改用户可见名称与内部模块 id。详见载荷中的 `SOURCE.txt` 与上游项目。
