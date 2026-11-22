---
description: Answers to common MuSu Next questions about device support, bootloader unlocking, modules, Zygisk and Xposed, Magisk compatibility, and older kernels.
---

# FAQ

## Does MuSu Next support my device?

MuSu Next supports devices running Android with an unlocked bootloader. However, official support is only for GKI Linux Kernels 5.10+ (in practice, this means your device needs to have Android 12 out-of-the-box to be supported).

You can easily check the support for your device through the MuSu Next manager, which is available [here](https://github.com/bilibiliHaoziyao/MuSuNext/releases). 

If the app shows `Not installed`, it means your device is officially supported by MuSu Next.

If the app shows `Unsupported`, it means your device isn't officially supported at present. However, you can build kernel source code and integrate MuSu Next to make it work, or use [Unofficially supported devices](unofficially-support-devices).

## Do I need to unlock the bootloader to use MuSu Next?

Yes. MuSu Next requires an unlocked bootloader.

## Does MuSu Next support modules?

Yes, most Magisk modules work on MuSu Next. However, if your module needs to modify `/system` files, you need to install a [metamodule](metamodule.md) (such as `meta-overlayfs`). Other module features work without a metamodule. Check [Module guide](module.md) for more info.

## Does MuSu Next support Xposed?

Yes, you can use LSPosed (or other modern Xposed derivative) with [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext).

## Does MuSu Next support Zygisk?

MuSu Next has no built-in Zygisk support, but you can use a module like [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext) to support it.

## Is MuSu Next compatible with Magisk?

MuSu Next's module system conflicts with Magisk's magic mount. If any module is enabled in MuSu Next, Magisk will stop working entirely.

However, if you only use the `su` of MuSu Next, it will work well with Magisk. MuSu Next modifies the `kernel`, while Magisk modifies the `ramdisk`, allowing both to work together.

## Will MuSu Next substitute Magisk?

No. Replacing Magisk isn't our goal. Magisk is already an excellent userspace root solution. MuSu Next focuses on exposing kernel interfaces to users instead of supplanting Magisk.

## Can MuSu Next support non-GKI devices?

It's possible. But you should download the kernel source, integrate MuSu Next into the source tree, and compile the kernel yourself.

## Can MuSu Next support devices below Android 12?

It's the device's kernel that affects MuSu Next's compatibility, and it has nothing to do with the Android version. The only restriction is that devices launched with Android 12 must have a kernel version of 5.10+ (GKI devices). So:

1. Devices launched with Android 12 must be supported.
2. Devices with an older kernel (some devices with Android 12 also have the older kernel) are compatible (you should build kernel yourself).

## Can MuSu Next support old kernel?

It's possible. MuSu Next is backported to kernel 4.14 now. For older kernels, you need to backport it manually, and PRs are always welcome!


## Why my Android version is 13, and the kernel shows "android12-5.10"?

The kernel version has nothing to do with the Android version. If you need to flash kernel, always use the kernel version; the Android version isn't as important.

## I'm GKI 1.0, can I use this?

GKI 1.0 is completely different from GKI 2.0, you must compile kernel by yourself.

## How can I make `/system` RW?

We don't recommend that you modify the system partition directly. Please check [Module guide](module.md) to modify it systemlessly. If you insist on doing this, check [magisk_overlayfs](https://github.com/HuskyDG/magic_overlayfs).

## Can MuSu Next modify hosts? How can I use AdAway？

Of course. But MuSu Next doesn't have built-in hosts support, you can install a module like [systemless-hosts](https://github.com/symbuzzer/systemless-hosts-KernelSU-module) to do it.

## Why aren't my modules working after fresh install?

If your modules need to modify `/system` files, you need to install a [metamodule](metamodule.md) to mount the `system` directory. Other module features (scripts, sepolicy, system.prop) work without a metamodule.

**Solution**: See the [Metamodule Guide](metamodule.md) for installation instructions.

## What is a metamodule and why do I need one?

A metamodule is a special module that provides infrastructure for mounting regular modules. See the [Metamodule Guide](metamodule.md) for a complete explanation.
