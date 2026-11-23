# MuSu Next とは?

MuSu Next は Android GKI デバイスのための root ソリューションです。カーネルモードで動作し、カーネル空間で直接ユーザー空間アプリに root 権限を付与します。

## 機能

MuSu Next の最大の特徴は、**カーネルベース**であることです。MuSu Next はカーネルモードで動作するため、今までにないカーネルインターフェイスを提供できます。例えば、カーネルモードで任意のプロセスにハードウェアブレークポイントを追加できる、誰にも気づかれずに任意のプロセスの物理メモリにアクセスできる、カーネル空間で任意のシステムコールを傍受できる、などです。

さらに、MuSu Next は [metamodule システム](metamodule.md) を提供しています。これはモジュール管理のためのプラグ可能なアーキテクチャです。従来の root ソリューションがマウントロジックをコアに組み込むのとは異なり、MuSu Next はこの作業を metamodule に委任します。これにより、metamodule ([meta-overlayfs](https://github.com/bilibiliHaoziyao/MuSuNext/tree/main/userspace/meta-overlayfs)など) をインストールして、`/system`パーティションや他のパーティションへのsystemless変更を提供できます。

## 使用方法

こちらをご覧ください: [インストール方法](installation)

## ビルド方法

[ビルドするには](../../guide/how-to-build)

## ディスカッション

- Bilibili: [@MuSu Next](https://b23.tv/6UtmCB0)
- QQ チャンネル: [参加する](https://pd.qq.com/s/Na9xis6inx?b=9)
