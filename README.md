# MuSu Next

[![Latest release](https://img.shields.io/github/v/release/bilibiliHaoziyao/MuSuNext?label=Release&logo=github)](https://github.com/bilibiliHaoziyao/MuSuNext/releases/latest)
[![License](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg?logo=gnu)](/LICENSE)
[![Bilibili](https://img.shields.io/badge/Follow-Bilibili-blue.svg)](https://b23.tv/6UtmCB0)

MuSu Next 是一个在内核里运行的 Android root 方案，从 [KernelSU](https://github.com/tiann/KernelSU) 衍生而来，主攻可加载内核模块（LKM）路线。

- 简体中文文档：[docs/README_CN.md](docs/README_CN.md)
- 官方网站：[su.novasync.cn](https://su.novasync.cn)

## 工作原理

root 权限由内核直接授予，放行的判断也发生在内核里。工作在这一层，能做用户态做不到的事，比如给任意进程下硬件断点、无痕读取别的进程的物理内存、在系统调用这一层拦下任意 syscall。

系统修改走 systemless 路线，不碰系统分区。挂载这件事核心自己不做，交给可插拔的元模块。要用 overlayfs 就装 `meta-overlayfs`，想换别的挂载策略就换一个元模块，核心保持不变。

## 技术特色

- **内核态授权**。su 的权限范围由用户逐项设定，内核强制执行。
- **元模块架构**。安装和挂载逻辑从核心剥离，交给独立模块，核心更稳，检测面更小。
- **App Profile**。为每个应用单独设置 su 的 uid、gid、groups、capabilities 和 SELinux 规则。防火墙应用只给网络权限，冻结类应用只给 shell，按最小权限收窄。
- **LKM-first**。以模块方式支持 GKI 2.0 设备（内核 5.10+），不再提供可直接刷写的 GKI boot 镜像。
- **独立身份**。有自己的包名和签名证书，内核与管理器靠签名互相认。
- **四语言**。管理器内置简体中文、繁體中文、English、日本語。

## 兼容情况

`arm64-v8a` 与 `x86_64` 架构。GKI 2.0（内核 5.10+）通过 LKM 覆盖，旧内核最低支持 4.14。WSA、ChromeOS 和容器里的 Android 同样可以运行。

## 开源协议

沿用上游双协议

- `kernel/` 目录为 GPL-2.0-only
- 其余部分为 GPL-3.0-or-later

## 致谢

- [KernelSU](https://github.com/tiann/KernelSU)，上游项目
- Magisk、kernel-assisted-superuser、genuine、Diamorphine
