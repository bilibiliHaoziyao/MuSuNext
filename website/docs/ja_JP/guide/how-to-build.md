---
description: GKI カーネル向けに MuSu Next のローダブルカーネルモジュール（musu.ko）をビルドします。
---

# LKM のビルド方法

MuSu Next は boot イメージではなく、ローダブルカーネルモジュール（`musu.ko`）として提供されます。このページではモジュールの自前ビルド方法を説明します。

## 必要なもの

- 端末の KMI に一致する GKI カーネルソース。MuSu Next が対応する KMI：`android12-5.10`、`android13-5.10`、`android13-5.15`、`android14-5.15`、`android14-6.1`、`android15-6.6`、`android16-6.12`、`android17-6.18`。
- LLVM/Clang ツールチェーン（`llvm-strip` を含む）。
- Linux 環境（または WSL2 / Linux コンテナ）。

## ddk でのビルド（推奨）

最も簡単な方法は [ddk](https://github.com/5ec1cff/ddk) ビルド環境の利用です。[Ylarod/ddk](https://github.com/Ylarod/ddk) がすぐに使える Docker イメージを提供しています。コンテナ内でリポジトリのルートから実行します：

```bash
cd kernel
./build-all.sh                # 対応するすべての KMI をビルド
./build-all.sh android14-6.1  # 単一の KMI のみをビルド
```

出力物は `kernel/` ディレクトリ内の `musu-<kmi>.ko` です。

## 単一 KMI の手動ビルド

対応する GKI カーネルソース（`vmlinux` までビルド済み）が既にある場合：

```bash
make -C kernel KDIR=/path/to/gki-kernel-source
```

Makefile は `KDIR` を基にモジュールをコンパイルし、その後 `check_symbol` を実行して、`musu.ko` にカーネルイメージに対する未解決のシンボル依存がないことを検証します。

:::tip x86_64
エミュレーターや WSA 向けには `kernel/build-all-x64.sh` と `scripts/prepare-ddk-x64.sh` を使用してください。
:::

## モジュールのインストール

ビルドしたモジュールはマネージャーからインストールする（インストール画面で `.ko` を選択）か、コマンドラインの `ksud boot-patch -m musu.ko` でインストールします。詳しくは[インストール](./installation.md)を参照してください。

## 補足

- マネージャーの署名検証値は `kernel/Kbuild` に組み込まれているため、手動で渡す必要はありません。
- CI ワークフロー（`build-lkm.yml` / `ddk-lkm.yml`）はリリースごとに同じ KMI リストを自動ビルドします。
