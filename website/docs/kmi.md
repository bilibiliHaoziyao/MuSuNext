---
title: Kernel module match
description: Find your device's KMI and pick the matching built-in module
---

# Find your kernel module

MuSu Next runs as an LKM, so the module has to match your kernel's KMI before it will load. Work out which one your device uses, then pick the same entry in the manager.

<KmiLookup lang="en" />

## Getting the kernel version

Run this in adb or a terminal emulator:

```sh
uname -r
```

The output looks like `5.10.198-android12-9-g9a1b2c3d`. Paste the whole line above; you don't need to pick anything out yourself.

## After you find it

The manager ships these modules built in, so there is nothing extra to download. Pick your boot image, tap Next, and choose the KMI you found above in the dialog. The list marks "This device KMI"; if you are patching a boot image from another device, go by that image's version instead.

Modules are built against the kernel ABI, so devices sharing a KMI generally work with the same build. If you run a custom kernel, the version string may fall outside the list — in that case build from source.

See [installation](./guide/installation) for the flashing steps.
