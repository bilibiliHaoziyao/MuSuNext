# MuSu Next

[![Latest release](https://img.shields.io/github/v/release/bilibiliHaoziyao/MuSuNext?label=Release&logo=github)](https://github.com/bilibiliHaoziyao/MuSuNext/releases/latest)
[![License](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg?logo=gnu)](/LICENSE)
[![Bilibili](https://img.shields.io/badge/Follow-Bilibili-blue.svg)](https://b23.tv/6UtmCB0)

**MuSu Next** 是一个基于内核的 Android root 方案，是上游 [KernelSU](https://github.com/tiann/KernelSU) 的独立发行版（fork）。

- 简体中文文档：[docs/README_CN.md](docs/README_CN.md)
- 品牌化与兼容性说明：[BRANDING.md](BRANDING.md) ← **使用前必读**
- 发布与构建指南：[RELEASE.md](RELEASE.md)

## 特性

- 基于内核的 `su` 与 root 权限管理
- 基于 metamodule 的 systemless 模块系统
- App Profile：把 Root 权限关进笼子
- 自带管理器（包名 `org.musu.next`），关于页提供 B 站主页入口

## 兼容状态

沿用上游兼容能力：官方支持 GKI 2.0 设备（内核 5.10+），旧内核（4.14+）需自行编译。
支持 `arm64-v8a` 与 `x86_64` 架构。

> [!IMPORTANT]
> MuSu Next 使用**独立的包名与签名**，因此**不能**直接搭配官方 KernelSU 内核。
> 必须使用本项目源码编译内核 / LKM（已内置新的签名校验值），详见 [BRANDING.md](BRANDING.md#4-内核必须配套编译重要)。

## 快速开始

1. 阅读 [BRANDING.md](BRANDING.md) 了解包名/签名/内核的配套关系。
2. 按 [RELEASE.md](RELEASE.md) 构建管理器 APK 与内核 LKM。
3. 用本项目内核模块刷入设备，再安装 MuSu Next 管理器。

## 开源协议

沿用上游双协议：
- `kernel/` 目录：**GPL-2.0-only**
- 其余部分：**GPL-3.0-or-later**

## 致谢

- [KernelSU](https://github.com/tiann/KernelSU) —— 上游项目，MuSu Next 的基础
- Magisk、kernel-assisted-superuser、genuine、Diamorphine 等上游致谢项
