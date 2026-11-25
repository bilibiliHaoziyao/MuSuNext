# インストール

## デバイスが対応しているか確認する

[GitHub Releases](https://github.com/bilibiliHaoziyao/MuSuNext/releases) から MuSu Next Manager アプリをダウンロードし、お使いのデバイスにインストールしてください。

- アプリが「非対応」と表示した場合は、**自分でカーネルをコンパイルする必要がある**という意味です。MuSu Next は書き込むためのブートイメージを提供しません。
- アプリが「未インストール」と表示した場合、お使いのデバイスは MuSu Next に対応しています。

::: info ヒント
非対応と表示されているデバイスについては、[非公式の対応デバイス](unofficially-support-devices.md)であればご自身でカーネルをビルドできます。
:::

## 純正の boot.img をバックアップ

書き込む前に、まず純正の boot.img をバックアップする必要があります。ブートループが発生した場合は、fastboot を使用して純正のブートイメージを書き込むことでいつでもシステムを復旧できます。

::: warning 警告
書き込みによりデータ損失を引き起こす可能性があります。次のステップに進む前に、このステップを必ず行うようにしてください！また、可能であればすべてのデータをバックアップしてください。
:::

## 必要な知識

### ADB と fastboot

このチュートリアルでは、デフォルトで ADB と fastboot のツールを使用します。ご存じない方は、まず検索エンジンを使って勉強されることをおすすめします。

### KMI

同じ Kernel Module Interface (KMI) のカーネルバージョンは**互換性があります**。これが GKI の「汎用」という意味です。逆に言えば KMI が異なればカーネルには互換性がなく、お使いのデバイスと異なる KMI のカーネルイメージを書き込むと、ブートループが発生する場合があります。

具体的には GKI デバイスの場合、カーネルバージョンの形式は以下のようになります：

```txt
KernelRelease :=
Version.PatchLevel.SubLevel-AndroidRelease-KmiGeneration-suffix
w      .x         .y       -zzz           -k            -something
```

`w.x-zzz-k` は KMI のバージョンです。例えば、デバイスのカーネルバージョンが `5.10.101-android12-9-g30979850fc20` である場合、その KMIは `5.10-android12-9` であり、理論的には他の KMI カーネルでも正常に起動できます。

::: tip ヒント
カーネルバージョンの SubLevel は、KMI の一部ではないことに注意してください。`5.10.101-android12-9-g30979850fc20` は `5.10.137-android12-9-g30979850fc20` と同じ KMI を持っているということになります。
:::

### セキュリティパッチレベル {#security-patch-level}

新しめの Android 端末には、セキュリティパッチレベルが古い boot イメージのフラッシュを許可しないロールバック防止機構が導入されていることがあります。例えば、もしあなたの端末のカーネルが `5.10.101-android12-9-g30979850fc20` であれば、セキュリティパッチレベルは `2023-11` です。たとえ KMI に対応するカーネルをフラッシュしても、セキュリティパッチレベルが (`2023-06` のように) `2023-11` より低い場合、ブートループを起こす可能性があります。

### Kernel バージョンと Android バージョンの違い

注意： **カーネルバージョンと Android バージョンは必ずしも同じではありません**。

カーネルのバージョンは「android12-5.10.101」なのに、Android システムのバージョンは「Android 13」などとなっていても、驚かないでください。Android システムのバージョン番号は、必ずしも Linux カーネルのバージョン番号と同じではありません。Linux カーネルのバージョン番号は、通常、**デバイスの出荷時**にプリインストールされている Android システムのバージョンに一致しています。Android システムが後でアップグレードされた場合、一般的にはカーネルのバージョンは変更されません。フラッシュをする際は、**必ずカーネルバージョンを参照してください**!!!

## はじめに

MuSu Next は **ローダブルカーネルモジュール** (LKM) として GKI 端末で動作します。モジュールは端末のカーネルに読み込まれ、端末本来のカーネルを置き換えることはありません。

### LKM モード {#lkm-mode}

LKM モードでは、端末本来のカーネルを置き換えずに、カーネルにローダブルカーネルモジュールを読み込みます。LKM モードの利点は:

1. 端末本来のカーネルを置き換えません。端末本来のカーネルに特別な要件がある場合や、MuSu Next をサードパーティのカーネルと両用したい場合、LKM モードを利用できます。
2. アップグレードと OTA がより便利です。MuSu Next をアップデートする時は、手動でフラッシュすることなくマネージャーから直接インストールできます。システム OTA の後は、手動でフラッシュすることなく非アクティブなスロットに直接インストールすることができます。
3. いくつかの特殊な状況に適しています。例えば、LKM は一時的な Root 権限を用いて読み込むことができます。boot パーティションを置き換えないので、AVB を動作させず、端末を文鎮化しません。
4. LKM は一時的にアンインストールすることができます。一時的に root アクセスを無効化したい場合、LKM をアンインストールすることができます。この過程でパーティションをフラッシュすることも、また再起動することさえも必要ありません。root アクセスを再度有効化したい場合は、再起動するだけです。

## LKM モードのインストール

### 公式ファームウェアの入手

LKM モードを使うためには、公式ファームウェアを入手し、それを基にパッチを当てる必要があります。サードパーティのカーネルを使う場合、その `boot.img` を公式ファームウェアとして利用できます。

公式ファームウェアを入手する方法はたくさんあります。通常は、端末の公式ファームウェアパッケージから boot イメージを抽出できます。すでに root 権限または一時的な root を持っている場合は、マネージャーの「直接インストール」がイメージを自動的に取得するため、手動での抽出は不要です。

LKM モードは `ramdisk` を展開します。そのため、Android 13 の端末では `boot` パーティションの代わりに `init_boot` パーティションにパッチを当てる必要があります。

### マネージャーを使う

マネージャーを開き、右上のインストールアイコンをタップすると、いくつかのオプションが現れます:

1. ファイルを選択しパッチを当てる。端末が Root 権限を持っていない場合、このオプションを選択し、公式ファームウェアを選択すると、マネージャーが自動的にパッチを当てます。恒久的に Root 権限を獲得するには、パッチが当てられたファイルをフラッシュするだけです。
2. 直接インストール。端末が既に Root 化されている場合、このオプションを選択すると、マネージャーが自動的に端末の情報を取得し、自動的に公式ファームウェアにパッチを当て、それをフラッシュします。これは MuSu Next をアップグレードする主な方法でもあります。
3. 非アクティブなスロットにインストール。端末が A/B パーティションに対応している場合、このオプションを選択すると、マネージャーが自動的に公式ファームウェアにパッチを当て、もう一方のパーティションにインストールします。この方法は OTA 後の端末に適しており、OTA 後にもう一方のパーティションに直接 MuSu Next をインストールし、端末を再起動することができます。

:::tip 純正 boot イメージをバックアップ
マネージャーの「直接インストール」を使うと、増分 OTA の際に一時的に復元できるよう、純正の boot（または init_boot）イメージを自動的にバックアップできます。このバックアップが作成されるのは、現在のスロットがまだ MuSu Next によってパッチされていない場合のみです。
バックアップイメージの SHA1 はパッチ済みの boot イメージに保存され、バックアップファイルは `/data/adb/ksu/ksu_backup_$SHA1` に保存されます。
マネージャーの「アンインストール → ストックイメージを復元」を使うと、現在パッチされているイメージに記録された SHA1 に対応するバックアップファイルが存在する場合、そのバックアップが直接復元されます。
3.3.0 以降、root なしで初めて MuSu Next をインストールする場合は、「ファイルを選択しパッチを当てる」で boot イメージをパッチしたあとに「純正イメージとしてバックアップ」を選択できます。この場合、バックアップイメージはマネージャーの内部ストレージに保存され、パッチ済みイメージを書き込んで起動したあと、初回起動時にそのバックアップが自動的に `/data/adb/ksu` へ移動されます。
:::

### コマンドラインを使う

マネージャーを使いたくない場合、コマンドラインを使って LKM をインストールできます。MuSu Next の提供する `ksud` ツールを使うと、迅速に公式ファームウェアにパッチを当てフラッシュすることができます。

このツールは macOS、Linux および Windows に対応しています。[GitHub Release](https://github.com/bilibiliHaoziyao/MuSuNext/releases) から対応するバージョンをダウンロードしてください。

使用方法: `ksud boot-patch`。各オプションの詳細はコマンドラインのヘルプで確認できます。
```sh
oriole:/ # ksud boot-patch -h
Patch boot or init_boot images to apply MuSu Next

Usage: ksud boot-patch [OPTIONS]

Options:
  -b, --boot <BOOT>              boot image path, if not specified, will try to find the boot image automatically
  -k, --kernel <KERNEL>          kernel image path to replace
  -m, --module <MODULE>          LKM module path to replace, if not specified, will use the builtin one
  -i, --init <INIT>              init to be replaced
  -u, --ota                      will use another slot when boot image is not specified
  -f, --flash                    Flash it to boot partition after patch
  -o, --out <OUT>                output path, if not specified, will use current directory
      --magiskboot <MAGISKBOOT>  magiskboot path, if not specified, will use builtin one
      --kmi <KMI>                KMI version, if specified, will use the specified KMI
  -h, --help                     Print help
```

説明が必要ないくつかのオプション:

1. `--magiskboot` オプションは magiskboot のパスを指定します。指定されていない場合、ksud は環境変数を参照します。magiskboot の入手方法がわからない場合、[こちら](#patch-boot-image) を参照してください。
2. `--kmi` オプションは `KMI` バージョンを指定します。端末のカーネル名が KMI の仕様に準拠していない場合は、このオプションで指定することができます。

最も一般的な使い方: 

```sh
ksud boot-patch -b <boot.img> --kmi android13-5.10
```

## インストール後：モジュールサポート {#post-installation}

::: warning システムファイル変更用の METAMODULE
`/system` ファイルを変更するモジュールを使う場合は、「設定 → MuMount」で MuSu Next 内蔵の [MuMount](../features/mumount.md)（既定では無効）を有効にするか、`meta-overlayfs` などのサードパーティ製 metamodule をインストールします。両者は排他関係です。スクリプト、sepolicy、system.prop のみを使うモジュールは metamodule なしで動作します。
:::

**`/system` 変更サポートが必要な場合**、[Metamodule ガイド](metamodule.md)を参照して：
- metamodule とは何か、なぜ必要なのかを理解する
- 公式の `meta-overlayfs` metamodule をインストールする
- 他の metamodule オプションについて学ぶ

1. まず Magisk をインストールし、Magisk を通じて root 権限を取得し、カーネル管理アプリで MuSu Next の AnyKernel ZIPをインストールする
2. PC 上で何らかの書き込みツールを使用し、MuSu Next が提供するカーネルを書き込む

しかし、これらの方法でうまく行かない場合、`magiskboot` を使う方法を試してみてください。
