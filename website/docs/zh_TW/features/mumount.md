---
description: MuMount 是 MuSu Next 內建的元模組，基於 Hybrid Mount，預設停用，在設定中以原生介面管理。
---

# MuMount

MuMount 是 MuSu Next 內建的元模組，基於 [Hybrid Mount](https://github.com/Hybrid-Mount/meta-hybrid_mount)。元模組接管其他所有模組檔案掛載進系統的方式，相較預設方式提供更靈活的掛載策略（OverlayFS、Magic Mount 與 VFS）。

> MuMount **預設停用**。開啟後需要**完整重新啟動**才會生效。

## 在哪裡找到它

與 MuZygisk 一樣，MuMount **不會**出現在模組清單中。請開啟「設定 → MuMount」。管理介面為純原生介面（非 WebUI），頂部有唯一的主開關。

## 狀態說明

| 狀態 | 含義 | 開關 |
|---|---|---|
| 未啟用 | 未開啟，使用預設掛載方式 | 可操作 |
| 已啟用 · 待重新啟動 | 已打開，等待下次開機接管掛載 | 可操作 |
| 運行中 | 掛載已生效，頁面顯示成功與失敗的模組數 | 可操作 |
| 被佔用 | 裝置上已安裝其他元模組 | 置灰不可開 |
| 不支援 | 當前核心不支援所需的 overlay 掛載 | 置灰不可開 |

## 互斥規則

MuMount 不能與其他元模組共存。

- 若裝置上已有其他元模組，MuMount 開關會**置灰**，頁面點名衝突的元模組，並提供停用入口。
- 若 MuMount 已開啟，其他元模組也無法啟用——它們會被同樣攔截。

## 接管範圍

啟用後，MuMount 會將模組檔案覆蓋到同名系統檔案之上，而不修改原始系統檔案。狀態頁顯示掛載範圍（如 `/system`、`/vendor`、`/product`）、成功掛載的模組數與失敗數。進階掛載行為由設定檔驅動：MuSu Next 會在首次啟用時產生該檔，此後不再覆蓋使用者的修改。

MuMount 可以與 MuZygisk 同時開啟——兩者相互獨立，只給出提示，不算衝突。

## 啟用與停用

1. 開啟「設定 → MuMount」。
2. 檢查衝突，如有則先處理。
3. 切換開關並**完整重新啟動**。

停用後，已掛載的模組檔案會保留到下次重新啟動。

## 與 Hybrid Mount 的關係

MuMount 基於 Hybrid Mount Developers 的 Hybrid Mount，採用 **GPL-3.0-only** 授權。MuSu Next 原樣打包上游的二進位、腳本與核心模組，僅更改使用者可見名稱與內部模組 id。詳見載荷中的 `SOURCE.txt` 與上游專案。
