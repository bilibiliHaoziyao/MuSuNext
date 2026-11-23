# MuSu Next

[![Latest release](https://img.shields.io/github/v/release/bilibiliHaoziyao/MuSuNext?label=Release&logo=github)](https://github.com/bilibiliHaoziyao/MuSuNext/releases/latest)
[![License](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg?logo=gnu)](/LICENSE)
[![Bilibili](https://img.shields.io/badge/Follow-Bilibili-blue.svg)](https://b23.tv/6UtmCB0)

**MuSu Next** 是一个基于内核的 Android root 方案，为上游 [KernelSU](https://github.com/tiann/KernelSU) 的衍生版本，主攻 LKM（可加载内核模块）路线。

- 简体中文文档：[docs/README_CN.md](docs/README_CN.md)
- 构建与发布指南：[RELEASE.md](RELEASE.md)
- 官方网站：[su.novasync.cn](https://su.novasync.cn)

## 特性

- 基于内核的 `su` 与 root 权限管理
- 基于 metamodule 的 systemless 模块系统
- App Profile：把 Root 权限关进笼子
- 内置管理器，支持简体中文 / 繁體中文 / English / 日本語

## 兼容状态

以 LKM（可加载内核模块）方式支持 GKI 2.0 设备（内核 5.10+），旧内核（4.14+）需自行编译模块；不再提供 GKI boot 镜像。
支持 `arm64-v8a` 与 `x86_64` 架构。

> 与上游KernelSU不通用，直接使用官方 KernelSU 内核无法识别 MuSu Next 管理器；内核与 LKM 需用本项目源码构建，校验参数见 [RELEASE.md](RELEASE.md)。

## 快速开始

1. 按 [RELEASE.md](RELEASE.md) 构建管理器 APK 与内核 LKM。
2. 刷入本项目构建的内核模块，再安装 MuSu Next 管理器。
3. 安装与配置文档见 [su.novasync.cn](https://su.novasync.cn)。

## 开源协议

沿用上游双协议：
- `kernel/` 目录：**GPL-2.0-only**
- 其余部分：**GPL-3.0-or-later**

## 致谢

- [KernelSU](https://github.com/tiann/KernelSU) —— 上游项目，MuSu Next 的基础
- Magisk、kernel-assisted-superuser、genuine、Diamorphine 等上游致谢项
