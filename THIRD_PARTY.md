# Third-party components

## DirtyFrag (MuSu Next locked fork)

| 项 | 值 |
|---|---|
| 项目 | DirtyFrag |
| 上游 URL | https://github.com/mitschud/DirtyFrag |
| 上游 tag / commit / 日期 | v1.18 / `3bad3cd6f7a9ce0a5536d900af264be2645d1512` / 2026-11-17 |
| fork 仓库 | https://github.com/bilibiliHaoziyao/MuSuNext-DirtyFrag |
| 许可证 | GPL-2.0-only（`dirtyfrag/LICENSE` 逐字原文，`dirtyfrag/NOTICE` 修改声明） |
| 修改源码位置 | `dirtyfrag/upstream/` + `dirtyfrag/patches/`（0001/0002/0003） |
| 分发文件 | `userspace/ksud/bin/dirtyfrag/arm64/`（独立 RustEmbed 载荷，非主 Asset） |
| 隔离方式 | 独立 APK / 独立进程 / 独立 UID；仅进程边界交互；不参与 ksud/manager 编译单元（C1/C2） |

分发文件清单与 sha256（与 `userspace/ksud/bin/dirtyfrag/arm64/SHA256SUMS` 一致）：

| 文件 | 字节 | sha256 |
|---|---|---|
| `dirtyfrag-locked.apk` | 310661 | `2ba8f9be4ccd6652d03ef0420be52695e33fc196d89b9cd367899f77c679c42e` |
| `ko/dfroot-android12-5.10.ko` | 14496 | `c4721299599b39552d3241624ce19e657f0f2e98654392974d47f027d3a3a39f` |
| `ko/dfroot-android13-5.10.ko` | 13032 | `edfb34ffb2addc2a85a8a3d6d164ed279636afc72526185b01939e1a5f703a19` |
| `ko/dfroot-android13-5.15.ko` | 13504 | `027a2c6e68b8fae38ee2f6f7ed0cbccb0838d78187e086e1c62af0c4e1a0be79` |
| `ko/dfroot-android14-5.15.ko` | 13504 | `2e70c398fddc69f511f1772db78c7e3508c2b4d87c5905bd8967eb35fb1a62ac` |
| `ko/dfroot-android14-6.1.ko` | 10112 | `5af4e7c94fee4e8e30fc5519d8382573b34c7eed5fdf5ffd13d4e2b786aa5752` |
| `ko/dfroot-android15-6.6.ko` | 10560 | `80ea9cc65be4e68d9c9a7c3ef8ae18119089d73ba233ed1003a61cfeec32ab3c` |
| `ko/dfroot-android16-6.12.ko` | 11032 | `a51e2af7595fb2cb35e2e2741f0c351ba68c3380ae7d06586861d49ccf965986` |
| `ko/dfroot-android17-6.18.ko` | 11040 | `e55766318e9821ad567686c931d396e22acfc12ef23ee31bf51e2692ca99948c` |
| `VERSION` | 10 | `6d44fabc7a24c9edaba30afa14f1c29614036ed8258d84c64dfa5191609922b7` |
| `SOURCE.txt` | 344 | `2e7156512fa9d10c6b622cb5546904fe7c8b36e320cace7d99b8904cf331c3df` |
| `LICENSE` | 17981 | `81d2e2140695d3a3c76c1fa48b8fc34abfee067f6197812c3e842612be5c3ff6` |
| `NOTICE` | 1841 | `d919d8c4a709089a0e0f415b01c7e41686f3952f6a32213f94a79cfe06e4a80b` |

APK 为本机应用三个 patch 后真实构建产物（`BUILD SUCCESSFUL`，v2 签名，证书 SHA-256 `338d89472d8038836b6623298e92e6eb9dd736f159a70eb2812a0f520acb1253`，本地验证签名）；8 个 ko 为上游固定 commit 的真实 AArch64 ELF 文件（`\x7fELF`，`e_machine=183`），未重编译、未替换。构建命令与复现步骤见 `dirtyfrag/README.md`。
