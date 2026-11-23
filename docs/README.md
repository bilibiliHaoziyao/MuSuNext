**English** | [简体中文](README_CN.md) | [繁體中文](README_TW.md) | [日本語](README_JP.md)

# MuSu Next

<img src="https://raw.githubusercontent.com/bilibiliHaoziyao/MuSuNext/main/website/docs/public/logo.png" style="width: 96px;" alt="logo">

A kernel-based root solution for Android devices.

[![Latest release](https://img.shields.io/github/v/release/bilibiliHaoziyao/MuSuNext?label=Release&logo=github)](https://github.com/bilibiliHaoziyao/MuSuNext/releases/latest)
[![Weblate](https://img.shields.io/badge/Localization-Weblate-teal?logo=weblate)](https://hosted.weblate.org/engage/kernelsu)
[![Bilibili](https://img.shields.io/badge/Follow-Bilibili-blue.svg)](https://b23.tv/6UtmCB0)
[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![GitHub License](https://img.shields.io/github/license/bilibiliHaoziyao/MuSuNext?logo=gnu)](/LICENSE)

## Features

1. Kernel-based `su` and root access management.
2. Module system based on [metamodules](https://github.com/bilibiliHaoziyao/MuSuNext/guide/metamodule.html): Pluggable infrastructure for systemless modifications.
3. [App Profile](https://github.com/bilibiliHaoziyao/MuSuNext/guide/app-profile.html): Lock up the root power in a cage.

## Compatibility state

MuSu Next officially supports Android GKI 2.0 devices (kernel 5.10+) via a loadable kernel module (LKM). Older kernels (4.14+) are also supported, but the module will need to be built manually. Ready-to-flash GKI boot images are no longer provided.

With this, WSA, ChromeOS, and container-based Android are all supported.

Currently, the `arm64-v8a` and `x86_64` architectures are supported.

> [!CAUTION]
> Recent kernel versions have implemented a breaking change causing MuSu Next to fail and potentially trigger a kernel panic on `x86_64`! Check the website for more info!

## Usage

- [Installation](https://github.com/bilibiliHaoziyao/MuSuNext/guide/installation.html)
- [How to build](https://github.com/bilibiliHaoziyao/MuSuNext/guide/how-to-build.html)
- [Official website](https://github.com/bilibiliHaoziyao/MuSuNext/)

## Translation

To help translate MuSu Next, we no longer accept translation contributions via Weblate. All translations are now handled using LLMs.

If you would like to add support for a new language, feel free to open a PR. Please note that modifications to existing English and Chinese translations are not accepted.

## Discussion

- Bilibili: [@MuSu Next](https://b23.tv/6UtmCB0)
- QQ Channel: [Join us](https://pd.qq.com/s/Na9xis6inx?b=9)

## Security

For information on reporting security vulnerabilities in MuSu Next, see [SECURITY.md](/SECURITY.md).

## License

- Files under the `kernel` directory are [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html).
- All other parts except the `kernel` directory are [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html).

## Credits

- [Kernel-Assisted Superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/): The MuSu Next idea.
- [Magisk](https://github.com/topjohnwu/Magisk): The powerful root tool.
- [genuine](https://github.com/brevent/genuine/): APK v2 signature validation.
- [Diamorphine](https://github.com/m0nad/Diamorphine): Some rootkit skills.
