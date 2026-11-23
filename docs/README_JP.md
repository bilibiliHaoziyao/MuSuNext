[English](README.md) | [简体中文](README_CN.md) | [繁體中文](README_TW.md) | **日本語**

# MuSu Next

<img src="https://raw.githubusercontent.com/bilibiliHaoziyao/MuSuNext/main/website/docs/public/logo.png" style="width: 96px;" alt="logo">

Android におけるカーネルベースの root ソリューションです。

[![Latest release](https://img.shields.io/github/v/release/bilibiliHaoziyao/MuSuNext?label=Release&logo=github)](https://github.com/bilibiliHaoziyao/MuSuNext/releases/latest)
[![Weblate](https://img.shields.io/badge/Localization-Weblate-teal?logo=weblate)](https://hosted.weblate.org/engage/kernelsu)
[![Bilibili](https://img.shields.io/badge/Follow-Bilibili-blue.svg)](https://b23.tv/6UtmCB0)
[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![GitHub License](https://img.shields.io/github/license/bilibiliHaoziyao/MuSuNext?logo=gnu)](/LICENSE)

## 特徴

1. カーネルベースの `su` と権限管理。
2. [metamodules](https://github.com/bilibiliHaoziyao/MuSuNext/ja_JP/guide/metamodule.html) に基づくモジュールシステム: プラグイン可能なシステムレス変更インフラストラクチャ。
3. [アプリのプロファイル](https://github.com/bilibiliHaoziyao/MuSuNext/guide/app-profile.html): root の権限をケージ内に閉じ込めます。

## 対応状況

MuSu Next はローダブルカーネルモジュール（LKM）によって GKI 2.0 デバイス（カーネルバージョン 5.10 以上）を公式にサポートしています。古いカーネル（4.14以上）とも互換性がありますが、モジュールを自分でビルドする必要があります。直接フラッシュできる GKI boot イメージは提供されません。

WSA 、ChromeOS とコンテナ上で動作する Android でも MuSu Next を統合して動かせます。

現在、`arm64-v8a` および `x86_64` アーキテクチャがサポートされています。

> [!CAUTION]
> 最近のカーネルバージョンでは、MuSu Next が機能しなくなり、`x86_64` 上でカーネルパニックを引き起こす可能性のある破壊的な変更が実装されています！詳細についてはウェブサイトを確認してください！

## 使用方法

- [インストール方法はこちら](https://github.com/bilibiliHaoziyao/MuSuNext/ja_JP/guide/installation.html)
- [ビルド方法はこちら](https://github.com/bilibiliHaoziyao/MuSuNext/guide/how-to-build.html)
- [公式サイト](https://github.com/bilibiliHaoziyao/MuSuNext/ja_JP/)

## 翻訳

MuSu Next をあなたの言語に翻訳するか、既存の翻訳を改善するには、[Weblate](https://hosted.weblate.org/engage/kernelsu/) を使用してください。Manager翻訳した PR は、Weblate と競合するため受け入れられなくなりました。

## ディスカッション

- Bilibili: [@MuSu Next](https://b23.tv/6UtmCB0)
- QQ チャンネル: [参加する](https://pd.qq.com/s/Na9xis6inx?b=9)

## ライセンス

- `kernel` ディレクトリの下にあるすべてのファイル： [GPL-2-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)。
- `kernel` ディレクトリ以外のすべてのファイル： [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html)。

## クレジット

- [kernel-assisted-superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/)：MuSu Next のアイデア元。
- [Magisk](https://github.com/topjohnwu/Magisk)：強力な root ツール。
- [genuine](https://github.com/brevent/genuine/)：apk v2 の署名検証。
- [Diamorphine](https://github.com/m0nad/Diamorphine): rootkit のスキル。
