# MuZygisk

MuZygisk is a fork of [ReZygisk](https://github.com/PerformanC/ReZygisk), tailored to run as a built-in component of MuSu Next instead of an installed Magisk/KernelSU module.

## 1. Upstream and fork provenance

- Upstream: https://github.com/PerformanC/ReZygisk
- Forked from commit: `bfa54ef5bb55a3dfd43c6309db950ef1327a6553`
- Fork date: 2026-11-24
- Upstream documentation (unmodified): `READMEs/`

## 2. Rename scope

| Surface | Treatment |
|---|---|
| Filesystem paths | Renamed to `/data/adb/musu/**` (functional hard-codes) |
| Executable names | `zygiskd64` -> `muz-zygiskd64`, `zygisk-ptrace64` -> `muz-zygisk-ptrace64` |
| User-visible strings | `ReZygisk` -> `MuZygisk` (logs, CLI, state text) |
| Internal C symbols | Kept as upstream (`rezygiskd_*`, `struct rezygisk_info`) to keep rebases cheap |

`PATH_MODULES_DIR` remains `/data/adb/modules` because it is part of the Zygisk module ABI.

## 3. Change list (maps to `docs/specs/builtin-components.md` section 13)

| # | Change |
|---|---|
| M1 | `zygiskd.c`: `TMP_PATH` -> `/data/adb/musu/run/zygisk`; daemon path -> `/data/adb/musu/zygisk/bin/muz-zygiskd{32,64}`; `PATH_MODULES_DIR` unchanged |
| M2 | Module scan excludes `muzygisk` / `musu` instead of `rezygisk` |
| M3 | `daemon.h` + `monitor.c`: socket dir and `state.json` -> `/data/adb/musu/run/zygisk/` |
| M4 | `entry.c`, `hook.c`, `ptracer.c`: self-library path -> `/data/adb/musu/zygisk/lib{64,}/libzygisk.so` |
| M5 | `monitor.c`: probe `./bin/muz-zygiskd32` / `./bin/muz-zygisk-ptrace32`; skip and mark 32-bit ABI uncovered when absent |
| M6 | `monitor.c`: removed `module.prop` status writes; `state.json` is the only status channel; emoji status markers replaced with plain text |
| M7 | `kernelsu.c`: added `org.musu.next` to `ksu_manager_pkgs` |
| M8 | Module install scripts are not part of the built-in payload (source kept in-tree) |
| M9 | `webroot/` is not packaged into the payload (source kept in-tree) |
| M10 | `LOGI`/`LOGE`/user-visible text prefix: `ReZygisk` -> `MuZygisk` |

## 4. License

AGPL-3.0-or-later. Full text: [`LICENSE`](LICENSE). Upstream and modification notices: [`NOTICE`](NOTICE).

## 5. AGPL section 13 consequence

> MuZygisk is a fork of ReZygisk, licensed under AGPL-3.0-or-later. It is distributed as part of MuSu Next, whose userspace is GPL-3.0-or-later. Combined distribution is permitted by GPLv3 section 13; the AGPL's section 13 (network interaction) applies to the combined work.

## 6. Build and artifact checksums

```
make release NDK_PATH=/opt/android-sdk/ndk/29.0.14206865 \
  ARCHS="arm64-v8a x86_64" VER_NAME=v1.0.0 VER_CODE=1 \
  COMMIT_HASH=bfa54ef5 MODULE_ID=muzygisk MODULE_NAME=MuZygisk
```

Payload files are installed under `userspace/ksud/bin/builtin/<arch>/zygisk/`:

| File (relative to `bin/builtin/`) | SHA256 |
|---|---|
| `aarch64/zygisk/bin/muz-zygiskd64` | `61642abd1f03da3f52c62788595b9178ec22d2b7054bfe3ae45a8784048ec424` |
| `aarch64/zygisk/bin/muz-zygisk-ptrace64` | `6b4935c89645edd2dd017bec16eb8e50b8bca0d0b8f9fcd305dc4d877cf342db` |
| `aarch64/zygisk/lib64/libzygisk.so` | `bcc646bcf053f3d37b005259be382c711c19ac8d60cc773746bdab8da13de263` |
| `aarch64/zygisk/lib64/libzygisk_ptrace.so` | `6b4935c89645edd2dd017bec16eb8e50b8bca0d0b8f9fcd305dc4d877cf342db` |
| `x86_64/zygisk/bin/muz-zygiskd64` | `908f23a29081c96c813fe3b50019ef3ef325370a624f88336d63846cde029911` |
| `x86_64/zygisk/bin/muz-zygisk-ptrace64` | `ef3a8d4738e5ce436afd2a94df130e6d0a3be47436d0960a01fb1de28c001aa8` |
| `x86_64/zygisk/lib64/libzygisk.so` | `1c3fc7f2784ef4ff789a9cab8ba4126a8914057e0336c6c7fa6d8932c2dc3af9` |
| `x86_64/zygisk/lib64/libzygisk_ptrace.so` | `ef3a8d4738e5ce436afd2a94df130e6d0a3be47436d0960a01fb1de28c001aa8` |
