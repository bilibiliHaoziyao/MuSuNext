---
description: 為 GKI 核心編譯 MuSu Next 可載入核心模組（kernelsu.ko）。
---

# 如何編譯 LKM

MuSu Next 以可載入核心模組（`kernelsu.ko`）的形式發布，而不是 boot 映像。本頁介紹如何自行編譯該模組。

## 準備條件

- 與裝置 KMI 匹配的 GKI 核心原始碼。MuSu Next 支援：`android12-5.10`、`android13-5.10`、`android13-5.15`、`android14-5.15`、`android14-6.1`、`android15-6.6`、`android16-6.12`、`android17-6.18`。
- LLVM/Clang 工具鏈（包含 `llvm-strip`）。
- Linux 環境（或 WSL2 / Linux 容器）。

## 使用 ddk 編譯（推薦）

最簡單的方式是使用 [ddk](https://github.com/5ec1cff/ddk) 編譯環境；[Ylarod/ddk](https://github.com/Ylarod/ddk) 提供了開箱即用的 Docker 映像。進入容器後，在儲存庫根目錄執行：

```bash
cd kernel
./build-all.sh                # 編譯全部支援的 KMI
./build-all.sh android14-6.1  # 或只編譯單一 KMI
```

產物是 `kernel/` 目錄下的 `kernelsu-<kmi>.ko`。

## 手動編譯單一 KMI

如果你已經有匹配的 GKI 核心原始碼（已編譯出 `vmlinux`）：

```bash
make -C kernel KDIR=/path/to/gki-kernel-source
```

Makefile 會基於 `KDIR` 編譯模組，然後執行 `check_symbol` 驗證 `kernelsu.ko` 對核心映像沒有未解析的符號依賴。

:::tip x86_64
模擬器與 WSA 請使用 `kernel/build-all-x64.sh`，並配合 `scripts/prepare-ddk-x64.sh` 準備環境。
:::

## 安裝模組

透過管理器安裝（在安裝介面選擇 `.ko` 檔案），或用命令列 `ksud boot-patch -m kernelsu.ko` 安裝。參見[安裝](./installation.md)。

## 說明

- 管理器簽章驗證值已寫入 `kernel/Kbuild`，編譯時無需手動傳參。
- CI 工作流（`build-lkm.yml` / `ddk-lkm.yml`）會在每次發布時自動編譯相同的 KMI 清單。
