---
description: 为 GKI 内核编译 MuSu Next 可加载内核模块（kernelsu.ko）。
---

# 如何编译 LKM

MuSu Next 以可加载内核模块（`kernelsu.ko`）的形式发布，而不是 boot 镜像。本页介绍如何自行编译该模块。

## 准备条件

- 与设备 KMI 匹配的 GKI 内核源码。MuSu Next 支持：`android12-5.10`、`android13-5.10`、`android13-5.15`、`android14-5.15`、`android14-6.1`、`android15-6.6`、`android16-6.12`、`android17-6.18`。
- LLVM/Clang 工具链（包含 `llvm-strip`）。
- Linux 环境（或 WSL2 / Linux 容器）。

## 使用 ddk 编译（推荐）

最简单的方式是使用 [ddk](https://github.com/5ec1cff/ddk) 编译环境；[Ylarod/ddk](https://github.com/Ylarod/ddk) 提供了开箱即用的 Docker 镜像。进入容器后，在仓库根目录执行：

```bash
cd kernel
./build-all.sh                # 编译全部支持的 KMI
./build-all.sh android14-6.1  # 或只编译单个 KMI
```

产物是 `kernel/` 目录下的 `kernelsu-<kmi>.ko`。

## 手动编译单个 KMI

如果你已经有匹配的 GKI 内核源码（已编译出 `vmlinux`）：

```bash
make -C kernel KDIR=/path/to/gki-kernel-source
```

Makefile 会基于 `KDIR` 编译模块，然后运行 `check_symbol` 校验 `kernelsu.ko` 对内核镜像没有未解析的符号依赖。

:::tip x86_64
模拟器与 WSA 请使用 `kernel/build-all-x64.sh`，并配合 `scripts/prepare-ddk-x64.sh` 准备环境。
:::

## 安装模块

通过管理器安装（在安装界面选择 `.ko` 文件），或用命令行 `ksud boot-patch -m kernelsu.ko` 安装。参见[安装](./installation.md)。

## 说明

- 管理器签名校验值已写入 `kernel/Kbuild`，编译时无需手动传参。
- CI 工作流（`build-lkm.yml` / `ddk-lkm.yml`）会在每次发布时自动编译相同的 KMI 列表。
