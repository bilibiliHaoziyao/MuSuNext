---
title: 簽章校驗
description: 在瀏覽器裡驗證 APK 的簽章指紋，確認是否為 MuSu Next 官方發佈
---

# 簽章校驗

Root 工具最怕被人重新打包後冒充官方。把你要裝的 APK 拖進來，瀏覽器會讀出它的簽章憑證指紋，和官方指紋比對。

整個校驗在本機完成，檔案不會上傳到任何伺服器。

<SignatureCheck lang="zh_TW" />

## 官方指紋

```
eeed9504acee1543f84e57916ef50f88b69bc9abbc72fc80f803a42267a5ead0
```

這個值和內核裡的 `KSU_EXPECTED_HASH` 一致。管理器能被內核識別，靠的就是它。

## 對不上怎麼辦

說明這個包不是用官方金鑰簽的。可能來源被替換過，也可能只是別人自己建置的版本。前者別裝，後者你得自己確認來源可信。

## 命令列的等價做法

```sh
apksigner verify --print-certs your.apk
```

輸出的 `SHA-256 digest` 就是上面那個指紋。
