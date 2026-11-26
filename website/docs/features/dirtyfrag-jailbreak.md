---
description: The built-in one-tap DirtyFrag jailbreak grants temporary root that is lost on reboot, with compatibility detection and a risk confirmation step.
---

# DirtyFrag Jailbreak

MuSu Next 0.0.8 adds a built-in **one-tap DirtyFrag jailbreak**. It uses CVE-2026-43284 (a page-cache write vulnerability) to load `dfroot.ko` and obtain **temporary root that is gone after a reboot**. It does not unlock the bootloader and does not flash any partition.

This is temporary root for research, not a permanent root setup.

## Where to find it

A **Jailbreak** button is always present on the Home page. Tap it to open the native jailbreak screen — no WebView, no extra manager to install. The screen walks you through:

1. **Compatibility detection** — checks whether your kernel is supported.
2. **Risk explanation** — lists what changes on your device.
3. **Risk confirmation** — a second, explicit confirmation before anything runs.
4. **One-tap jailbreak** — runs the whole flow; no manager selection, no external manager.

MuSu Next is the only manager this payload starts. You never pick a manager or sideload a separate one.

## Compatibility

DirtyFrag ships a payload for a fixed set of kernel module interfaces (KMI):

- `android12-5.10`
- `android13-5.10`
- `android13-5.15`
- `android14-5.15`
- `android14-6.1`
- `android15-6.6`
- `android16-6.12`
- `android17-6.18`

KMI matching is only a first filter. A vendor may backport the fix while keeping the same KMI string, so a matching KMI can still mean the device is not exploitable. The built-in detection gives a clear verdict; on an unsupported device the jailbreak button is disabled.

## Risks — read before you tap

- **Root disappears on reboot.** The grant lasts only for the current boot. After a reboot there is no root until you run the flow again.
- **SELinux is set to permissive.** The payload calls `setenforce permissive`, so the device runs in a relaxed SELinux state for the whole boot.
- **Never tap twice within 70 seconds.** The exploit has a roughly 70-second window. Repeated taps — or a reboot during it — can cause a kernel conflict, leading to a freeze or restart. The button stays locked while a run is in progress.
- **If it fails, use safe mode.** Boot into safe mode to disable or uninstall the payload.

## Licence and source

The DirtyFrag payload is distributed as a **separate APK with no launcher icon**, under its own **GPL-2.0-only** licence, independent of MuSu Next's GPL-3.0-or-later code. Source: [MuSuNext-DirtyFrag](https://github.com/bilibiliHaoziyao/MuSuNext-DirtyFrag).

## Limits

- This is **temporary root for research**, not a permanent root solution.
- It does not unlock the bootloader, flash partitions, or survive a reboot.
- It cannot guarantee passing Play Integrity or any server-side risk control — apps use their own private detection logic.

## How it fits with other features

- **Environment Check** reports what an unauthorized app can actually see. It is an independent diagnostic and does not depend on jailbreak state.
- **Root Access Audit** records Root usage per app. Jailbreak grants root for the current boot only; the audit records the accesses that actually happen during it.
