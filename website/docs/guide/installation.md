---
description: How to install MuSu Next on Android GKI devices. Check device support, back up the stock boot image, and install in LKM mode.
---

# Installation

## Check if your device is supported

Download MuSu Next manager from [GitHub Releases](https://github.com/bilibiliHaoziyao/MuSuNext/releases) and install it on your device:

- If the app shows `Unsupported`, it means that **you should compile the kernel yourself**, MuSu Next won't and will never provide a boot.img file for you to flash.
- If the app shows `Not installed`, then your device is officially supported by MuSu Next.

::: info
For devices showing `Unsupported`, you can check the list of [Unofficially supported devices](unofficially-support-devices.md). You can compile the kernel yourself.
:::

## Backup stock boot.img

Before flashing, it's essential that you back up your stock boot.img. If you encounter any bootloop, you can always restore the system by flashing back to the stock factory boot using fastboot.

::: warning
Flashing may cause data loss. Make sure to do this step well before proceeding to the next step! You can also back up all the data on your device if necessary.
:::

## Necessary knowledge

### ADB and fastboot

By default, you will use ADB and fastboot tools in this tutorial, so if you don't know them, we recommend using a search engine to learn about them first.

### KMI

Kernel Module Interface (KMI), kernel versions with the same KMI are **compatible**, this is what "general" means in GKI; conversely, if the KMI is different, then these kernels aren't compatible with each other, and flashing a kernel image with a different KMI than your device may cause a bootloop.

Specifically, for GKI devices, the kernel version format should be as follows:

```txt
KernelRelease :=
Version.PatchLevel.SubLevel-AndroidRelease-KmiGeneration-suffix
w      .x         .y       -zzz           -k            -something
```

`w.x-zzz-k` is the KMI version. For example, if a device kernel version is `5.10.101-android12-9-g30979850fc20`, then its KMI is `5.10-android12-9`. Theoretically, it can boot up normally with other KMI kernels.

::: tip
Note that the SubLevel in the kernel version isn't part of the KMI! This means that `5.10.101-android12-9-g30979850fc20` has the same KMI as `5.10.137-android12-9-g30979850fc20`!
:::

### Security patch level {#security-patch-level}

Newer Android devices may have anti-rollback mechanisms that prevent flashing a boot image with an old security patch level. For example, if your device kernel is `5.10.101-android12-9-g30979850fc20`, the security patch level is `2023-11`; even if you flash the kernel corresponding to the KMI, if the security patch level is older than `2023-11` (such as `2023-06`), it may cause a bootloop.

Therefore, kernels with latest security patch levels are preferred to maintain compatibility with the KMI.

### Kernel version vs Android version

Please note: **Kernel version and Android version aren't necessarily the same!**

If you find that your kernel version is `android12-5.10.101`, but your Android system version is Android 13 or other, don't be surprised, because the version number of the Android system isn't necessarily the same as the version number of the Linux kernel. The version number of the Linux kernel is generally correspondent to the version of the Android system that comes with the **device when it is shipped**. If the Android system is upgraded later, the kernel version will generally not change. So, before flashing anything, **always refer to the kernel version!**

## Introduction

MuSu Next runs on GKI devices as a **Loadable Kernel Module** (LKM): the module is loaded into the device kernel without replacing the original kernel.

### LKM mode {#lkm-mode}

In LKM mode, the original kernel of the device won't be replaced, but the loadable kernel module will be loaded into the device kernel. The advantages of LKM mode are:

1. Won't replace the original kernel of the device. If you have special requirements for the original kernel of the device, or you want to use MuSu Next while using a third-party kernel, you can use LKM mode.
2. It's more convenient to upgrade and OTA. When upgrading MuSu Next, you can directly install it in the manager without flashing manually. After the system OTA, you can directly install it to the second slot without manual flashing.
3. Suitable for some special scenarios. For example, LKM can also be loaded with temporary root permissions. Since it doesn't need to replace the boot partition, it won't trigger AVB and won't cause the device to be bricked.
4. LKM can be temporarily uninstalled. If you want to temporarily disable root access, you can uninstall LKM. This process doesn't require flashing partitions, or even rebooting the device. If you want to enable root again, just reboot the device.

## LKM installation

### Get the official firmware

To use LKM mode, you need to get the official firmware and patch it based on the official firmware. If you use a third-party kernel, you can use the `boot.img` of the third-party kernel as the official firmware.

Get the stock boot image from the official firmware for your device. If you already have root or another source of temporary root, the manager can use **Direct install** instead of requiring you to extract the image manually. Otherwise, extract the appropriate `boot.img` or `init_boot.img` from the firmware and use **Select a file** in the manager.

LKM mode patches the image that contains the generic ramdisk. On devices that use a separate `init_boot` partition (commonly devices launched with Android 13 or later), patch `init_boot.img`; otherwise patch `boot.img`. Do not choose the target only from the currently installed Android version—use the partition layout of the device and its stock firmware.

### Use the manager

Open the manager, click the installation icon in the upper right corner, and several options will appear:

1. Select a file. If your device doesn't have root privileges, you can choose this option and then select your official firmware. The manager will automatically patch it. After that, just flash this patched file to obtain root privileges permanently.
2. Direct install. If your device is already rooted, you can choose this option. The manager will automatically get your device information, patch the appropriate stock boot image, and flash it. This is also the main way to upgrade MuSu Next.
3. Install to inactive slot. If your device supports A/B partition, you can choose this option. The manager will automatically patch the official firmware and install it to another partition. This method is suitable for devices after OTA, you can directly install it to another partition after OTA, and then restart the device.

:::tip Back up the stock boot image
Using the manager's "Direct install" can automatically back up the stock boot (or init_boot) image for temporarily restoring it during incremental OTA updates. Note that this backup is created only if the current slot has not already been patched by MuSu Next.
The SHA1 of the backup image is stored in the patched boot image, and the backup file is saved at `/data/adb/ksu/ksu_backup_$SHA1`.
When you use the manager's "Uninstall → Restore stock image" feature, if there is a matching backup file for the SHA1 recorded in the currently patched image, it will be restored directly.
Since 3.3.0, if you are installing MuSu Next for the first time without root, after patching the boot image via "Select a file" you can choose "Backup as stock image". In that case, the backup image is stored in the manager's internal storage; after flashing and booting the patched image, opening the manager for the first time will automatically move that backup to `/data/adb/ksu`.
:::

### Use the command line

If you don't want to use the manager, you can also use the command line to install LKM. The `ksud` tool provided by MuSu Next can help you quickly patch the official firmware and then flash it.

This tool supports macOS, Linux, and Windows. You can download the corresponding version from [GitHub Release](https://github.com/bilibiliHaoziyao/MuSuNext/releases).

Usage: `ksud boot-patch` you can check the command line help for specific options.

```sh
oriole:/ # ksud boot-patch -h
Patch boot or init_boot images to apply MuSu Next

Usage: ksud boot-patch [OPTIONS]

Options:
  -b, --boot <BOOT>              Boot image path. If not specified, it will try to find the boot image automatically
  -k, --kernel <KERNEL>          Kernel image path to be replaced
  -m, --module <MODULE>          LKM module path to be replaced. If not specified, the built-in module will be used
  -i, --init <INIT>              init to be replaced
  -u, --ota                      Will use another slot if the boot image is not specified
  -f, --flash                    Flash it to boot partition after patch
  -o, --out <OUT>                Output path. If not specified, the current directory will be used
      --magiskboot <MAGISKBOOT>  magiskboot path. If not specified, the built-in version will be used
      --kmi <KMI>                KMI version. If specified, the indicated KMI will be used
  -h, --help                     Print help
```

A few options that need to be explained:

1. The `--magiskboot` option can specify the path of magiskboot. If not specified, ksud will look for it in the environment variables. If you don’t know how to get magiskboot, you can find it in the Magisk module repository.
2. The `--kmi` option can specify the `KMI` version. If the kernel name of your device doesn't follow the KMI specification, you can specify it using this option.

The most common usage is:

```sh
ksud boot-patch -b <boot.img> --kmi android13-5.10
```

## Post-Installation: Module Support

::: warning METAMODULE FOR SYSTEM FILE MODIFICATION
If you want to use modules that modify `/system` files, you need to install a **metamodule** after installing MuSu Next. Modules that only use scripts, sepolicy, or system.prop work without a metamodule.
:::

**For `/system` modification support**, please see the [Metamodule Guide](metamodule.md) to:
- Understand what metamodules are and why they're needed
- Install the official `meta-overlayfs` metamodule
- Learn about other metamodule options
