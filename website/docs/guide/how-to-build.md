---
description: Build the MuSu Next loadable kernel module (musu.ko) for GKI kernels.
---

# How to build the LKM

MuSu Next ships as a loadable kernel module (`musu.ko`) instead of a boot image. This page describes how to build the module yourself.

## Prerequisites

- A GKI kernel source tree matching your device's KMI. MuSu Next supports: `android12-5.10`, `android13-5.10`, `android13-5.15`, `android14-5.15`, `android14-6.1`, `android15-6.6`, `android16-6.12`, `android17-6.18`.
- LLVM/Clang toolchain (including `llvm-strip`).
- A Linux host (or WSL2 / a Linux container).

## Build with ddk (recommended)

The easiest way is the [ddk](https://github.com/5ec1cff/ddk) build environment; a ready-to-use Docker image is provided by [Ylarod/ddk](https://github.com/Ylarod/ddk). Inside the container, from the repository root:

```bash
cd kernel
./build-all.sh                # build all supported KMIs
./build-all.sh android14-6.1  # or build a single KMI
```

Output: `musu-<kmi>.ko` files in the `kernel/` directory.

## Build a single KMI manually

If you already have a matching GKI kernel source tree (with `vmlinux` built):

```bash
make -C kernel KDIR=/path/to/gki-kernel-source
```

The Makefile compiles the module against `KDIR` and then runs `check_symbol` to verify that `musu.ko` has no unresolved symbol dependencies against the kernel image.

:::tip x86_64
For emulators and WSA, use `kernel/build-all-x64.sh` together with `scripts/prepare-ddk-x64.sh`.
:::

## Install the module

Install the built module through the manager (pick the `.ko` in the install screen) or from the command line with `ksud boot-patch -m musu.ko`. See [Installation](./installation.md).

## Notes

- The manager signature verification values are already baked into `kernel/Kbuild`; you don't need to pass them manually.
- The CI workflows (`build-lkm.yml` / `ddk-lkm.yml`) build the same KMIs automatically on every release.
