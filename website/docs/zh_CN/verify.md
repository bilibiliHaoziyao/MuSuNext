---
title: 签名校验
description: 在浏览器里验证 APK 的签名指纹，确认是否为 MuSu Next 官方发布
---

# 签名校验

Root 工具最怕被人重新打包后冒充官方。把你要装的 APK 拖进来，浏览器会读出它的签名证书指纹，和官方指纹比对。

整个校验在本地完成，文件不会上传到任何服务器。

<SignatureCheck lang="zh_CN" />

## 官方指纹

```
eeed9504acee1543f84e57916ef50f88b69bc9abbc72fc80f803a42267a5ead0
```

这个值和内核里的 `KSU_EXPECTED_HASH` 一致。管理器能被内核识别，靠的就是它。

## 对不上怎么办

说明这个包不是用官方密钥签的。可能来源被替换过，也可能只是别人自己构建的版本。前者别装，后者你得自己确认来源可信。

## 命令行的等价做法

如果你习惯用命令行：

```sh
apksigner verify --print-certs your.apk
```

输出的 `SHA-256 digest` 就是上面那个指纹。
