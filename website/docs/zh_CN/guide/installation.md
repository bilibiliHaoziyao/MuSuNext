# 安装 {#title}

## 检查您的设备是否被支持 {#check-if-supported}

从 [GitHub Releases](https://github.com/bilibiliHaoziyao/MuSuNext/releases) 下载 MuSu Next 管理器应用，然后将应用程序安装到设备并打开：

- 如果应用程序显示 “不支持”，则表示您的设备不支持 MuSu Next，你需要自己编译设备的内核才能使用，MuSu Next 官方不会也永远不会为你提供一个可以刷写的 boot 镜像。
- 如果应用程序显示 “未安装”，那么 MuSu Next 支持您的设备；可以进行下一步操作。

:::info
对于显示“不支持”的设备，这里有一个[非官方支持设备列表](unofficially-support-devices.md)，你可以用这个列表里面的内核自行编译。
:::

## 备份你的 boot.img {#backup-boot-image}

在进行刷机操作之前，你必须先备份好自己的原厂 boot.img。如果你后续刷机出现了任何问题，你都可以通过使用 fastboot 刷回原厂 boot 来恢复系统。

::: warning
任何刷机操作都是有风险的，请务必做好这一步再进行下一步操作！！必要时你还可以备份你手机的所有数据。
:::

## 必备知识 {#acknowage}

### ADB 和 fastboot {#adb-and-fastboot}

此教程默认你会使用 ADB 和 fastboot 工具，如果你没有了解过，建议使用搜索引擎先学习相关知识。

### KMI

KMI 全称 Kernel Module Interface，相同 KMI 的内核版本是**兼容的** 这也是 GKI 中“通用”的含义所在；反之，如果 KMI 不同，那么这些内核之间无法互相兼容，刷入与你设备 KMI 不同的内核镜像可能会导致死机。

具体来说，对 GKI 的设备，其内核版本格式应该如下：

```txt
KernelRelease :=
Version.PatchLevel.SubLevel-AndroidRelease-KmiGeneration-suffix
w      .x         .y       -zzz           -k            -something
```

其中，`w.x-zzz-k` 为 KMI 版本。例如，一个设备内核版本为`5.10.101-android12-9-g30979850fc20`，那么它的 KMI 为 `5.10-android12-9`；理论上刷入其他这个 KMI 的内核也能正常开机。

::: tip
请注意，内核版本中的 SubLevel 不属于 KMI 的范畴！也就是说 `5.10.101-android12-9-g30979850fc20` 与 `5.10.137-android12-9-g30979850fc20` 的 KMI 相同！
:::

### 安全补丁级别 {#security-patch-level}

新的 Android 设备上可能采取了防回滚机制，它不允许刷入一个安全补丁更旧的内核。比如，如果你的设备内核是 `5.10.101-android12-9-g30979850fc20`，它的安全补丁为 `2023-11`；即使你刷入与内核 KMI 一致的内核，如果安全补丁级别比 `2023-11` 要老（例如`2023-06`），那么很可能会无法开机。

因此，在保持 KMI 一致的情况下，优先采用安全补丁级别更新的内核。

### 内核版本与 Android 版本 {#kernel-version-vs-android-version}

请注意：**内核版本与 Android 版本并不一定相同！**

如果您发现您的内核版本是 `android12-5.10.101`，然而你 Android 系统的版本为 Android 13 或者其他；请不要觉得奇怪，因为 Android 系统的版本与 Linux 内核的版本号不一定是一致的；Linux 内核的版本号一般与**设备出厂的时候自带的 Android 系统的版本一致**，如果后续 Android 系统升级，内核版本一般不会发生变化。如果你需要刷机，**请以内核版本为准！！**

## 安装介绍 {#installationintroduction}

MuSu Next 以**可加载内核模块**（LKM）的方式运行在 GKI 设备上：模块被加载进设备内核，不会替换掉设备原有的内核。

### LKM 模式 {#lkm-mode}

LKM 模式不会替换掉设备原有的内核，而是使用可加载内核模块的方式加载到设备内核中。LKM 模式的优点是：

1. 不会替换掉设备原有的内核；如果你对设备原有的内核有特殊需求，或者你希望在使用第三方内核的同时使用 MuSu Next，可以使用 LKM 模式；
2. 升级和 OTA 较为方便；升级 MuSu Next 时，可以直接在管理器里面安装，无需再手动刷写；系统 OTA 后，可以直接安装到第二个槽位，也无需再手动刷写；
3. 适用于一些特殊场景；比如使用临时 ROOT 权限也可以加载 LKM，由于不需要替换 boot 分区，因此不会触发 avb，不会使设备意外变砖；
4. LKM 可以被临时卸载；如果你临时想取消 root，可以卸载 LKM，这个过程不需要刷写分区，甚至也不用重启设备；如果你想再次 root，只需要重启设备即可；

## LKM 安装

### 获取官方固件

使用 LKM 的模式，需要获取官方固件，然后在官方固件的基础上修补；如果你使用的是第三方内核，可以把第三方内核的 boot.img 作为官方固件。

获取官方固件的方法有很多，通常你可以从设备的官方固件包中提取 boot 镜像。如果你已经拥有 root 权限或其他临时 root 来源，管理器也可以使用「直接安装」功能自动获取镜像，无需手动提取。

LKM 模式会修改 `ramdisk`，因此在出厂 Android 13 的设备上，它需要修补的是 `init_boot` 分区而非 `boot` 分区。

### 使用管理器

打开管理器，点击右上角的安装图标，会出现若干个选项：

1. 选择并修补一个文件；如果你手机目前没有 root 权限，你可以选择这个选项，然后选择你的官方固件，管理器会自动修补它；你只需要刷入这个修补后的文件，即可永久获取 root 权限；
2. 直接安装；如果你手机已经 root，你可以选择这个选项，管理器会自动获取你的设备信息，然后自动修补官方固件，然后刷入；这种方式也是 MuSu Next 升级最主要的方式；
3. 安装到另一个分区；如果你的设备支持 A/B 分区，你可以选择这个选项，管理器会自动修补官方固件，然后安装到另一个分区；这种方式适用于 OTA 后的设备，你可以在 OTA 后直接安装到另一个分区，然后重启设备即可；

:::tip 备份原厂 boot 镜像
使用管理器的「直接安装」可自动备份原厂 boot （或 init_boot）镜像，以备增量 OTA 时临时还原。请注意，这种情况下只有当前分区没有被 MuSu Next 修补过，才会产生备份。
备份镜像的 SHA1 值会保存到修补的 boot 镜像中，备份的文件存储在 `/data/adb/ksu/ksu_backup_$SHA1` 。
当使用管理器的「卸载 → 还原原厂镜像」功能时，如果当前修补的镜像记录的 SHA1 存在对应的备份文件，则会直接恢复。
自 3.3.0 起，如果是在没有 Root 的情况下首次安装 MuSu Next ，使用「选择并修补一个文件」方式修补 boot 镜像后可选择「备份为原厂镜像」，此时备份镜像会保存到管理器的内部存储中，这样刷入修补后的镜像后启动，首次打开管理器就会自动将备份移动到 `/data/adb/ksu` 。
:::

### 使用命令行

如果你不想使用管理器，你也可以使用命令行来安装 LKM；MuSu Next 提供的 `ksud` 工具可以帮助你快速修补官方固件，然后刷入。

这个工具支持 macOS、Linux 和 Windows，你可以在 [GitHub Release](https://github.com/bilibiliHaoziyao/MuSuNext/releases) 下载对应的版本。

使用方法：`ksud boot-patch` 具体的使用方法你可以查看命令行帮助。

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

需要说明的几个选项：

1. `--magiskboot` 选项可以指定 magiskboot 的路径，如果不指定，ksud 会在环境变量中查找；如果你不知道如何获取 magiskboot，可以查阅[这里](#patch-boot-image)；
2. `--kmi` 选项可以指定 `KMI` 版本，如果你的设备内核名字没有遵循 KMI 规范，你可以通过这个选项来指定；

最常见的使用方法为：

```sh
ksud boot-patch -b <boot.img> --kmi android13-5.10
```

## 安装后：模块支持 {#post-installation-module-support}

::: warning 修改系统文件需要 METAMODULE
如果您想使用修改 `/system` 文件的模块，可在「设置 → MuMount」中开启 MuSu Next 内建的 [MuMount](../features/mumount.md)（默认禁用），或安装第三方元模块（如 `meta-overlayfs`），二者互斥。仅使用脚本、sepolicy 或 system.prop 的模块无需元模块。
:::

**要获得 `/system` 修改支持**，请参阅 [Metamodule 指南](metamodule.md) 了解：
- 什么是 metamodule 以及为什么需要它们
- 如何安装官方的 `meta-overlayfs` metamodule
- 其他 metamodule 选项
