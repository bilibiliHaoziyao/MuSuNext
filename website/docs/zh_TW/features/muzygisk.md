---
description: MuZygisk 是 MuSu Next 內建的 Zygisk 實作，源自 ReZygisk，預設停用，在設定中以原生介面管理。
---

# MuZygisk

MuZygisk 是 MuSu Next 內建的 [Zygisk](https://github.com/topjohnwu/Magisk) 實作，源自 [ReZygisk](https://github.com/PerformanC/ReZygisk)，針對 MuSu Next 重新編譯並更名。啟用後，無需刷入獨立 Zygisk 實作即可使用 Zygisk 模組。

> MuZygisk **預設停用**。開啟後需要**完整重新啟動**才會生效。

## 在哪裡找到它

MuZygisk **不會**出現在模組清單中。請開啟管理器的「設定」頁面，從中進入「MuZygisk」。管理介面為純原生介面（非 WebUI），頂部有唯一的主開關。

## 狀態說明

| 狀態 | 含義 | 開關 |
|---|---|---|
| 未啟用 | 未開啟，當前沒有 Zygisk 注入 | 可操作 |
| 已啟用 · 待重新啟動 | 已打開，等待完整重新啟動後生效 | 可操作 |
| 運行中 | Zygisk 守護行程已啟動並正在注入 | 可操作（關閉以停用） |
| 被佔用 | 裝置上已安裝其他 Zygisk 實作 | 置灰不可開 |
| 無可用載荷 | 當前架構沒有 MuZygisk 建置（如 riscv64） | 置灰不可開 |

## 互斥規則

MuZygisk 不能與其他 Zygisk 實作（如 Zygisk Next，或核心內建 Zygisk）共存。

- 若裝置上已有其他實作，MuZygisk 開關會**置灰**，頁面會點名衝突的模組，並提供一鍵前往模組清單停用的入口。
- 若 MuZygisk 已開啟，那些實作也無法從模組清單啟用——它們的開關同樣會被置灰。

這是硬性互斥：同時執行兩個 Zygisk 實作會導致雙重注入與開機失敗。

## 啟用與停用

1. 開啟「設定 → MuZygisk」。
2. 閱讀頂部狀態卡，如有衝突請先處理。
3. 切換開關。
4. 執行**完整重新啟動**——軟重新啟動不會重啟 Zygisk 守護行程。

關閉 MuZygisk 後，守護行程與執行檔會在下一次完整重新啟動時清理。

## 與 ReZygisk 的關係

MuZygisk 源自 PerformanC 的 ReZygisk，採用 **AGPL-3.0-or-later** 授權。本 fork 將儲存路徑改為 MuSu Next 自有目錄，移除模組安裝腳本與 WebUI，並加入對 MuSu Next 管理器的識別。MuSu Next 使用者空間仍為 GPL-3.0-or-later；組合分發由 GPLv3 第 13 節允許，AGPL 的網路互動條款對組合作品生效。詳見原始碼樹的 `muzygisk/LICENSE` 與 `muzygisk/NOTICE`。
