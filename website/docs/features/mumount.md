---
description: MuMount is MuSu Next's built-in metamodule, based on Hybrid Mount, disabled by default and managed natively in Settings.
---

# MuMount

MuMount is MuSu Next's built-in metamodule, based on [Hybrid Mount](https://github.com/Hybrid-Mount/meta-hybrid_mount). A metamodule takes over how all other modules' files are mounted into the system, offering more flexible mount strategies (OverlayFS, Magic Mount, and VFS) than the default.

> MuMount is **disabled by default**. Turning it on requires a **full reboot**.

## Where to find it

Like MuZygisk, MuMount does **not** appear in the module list. Open **Settings → MuMount**. The management screen is fully native (no WebUI), with one master switch at the top.

## Statuses

| Status | Meaning | Switch |
|---|---|---|
| Off | Not enabled; the default mount method is used | Available |
| Enabled · reboot required | Switched on, waiting for the next boot to take over mounting | Available |
| Running | Mounting is active; the page shows how many modules mounted and how many failed | Available |
| Blocked | Another metamodule is already installed | Disabled (grey) |
| Unsupported | The kernel does not support the required overlay mount | Disabled (grey) |

## Mutual exclusion

MuMount cannot coexist with another metamodule.

- If another metamodule is installed, the MuMount switch is **greyed out**, the page names the conflicting metamodule, and it offers a path to disable it.
- If MuMount is already enabled, other metamodules cannot be enabled — they are blocked the same way.

## What it controls

When active, MuMount mounts module files over their system counterparts without modifying the original system files. Its status page shows the mount scope (such as `/system`, `/vendor`, `/product`), the number of modules mounted and any failures. Advanced mount behaviour is driven by a config file, which MuSu Next seeds on first enable and never overwrites afterwards.

MuMount can be enabled at the same time as MuZygisk — the two are independent and only produce a notice, not a conflict.

## Enable or disable

1. Open **Settings → MuMount**.
2. Check for a conflict and resolve it if needed.
3. Toggle the switch and perform a **full reboot**.

When disabling, already-mounted module files remain until the next reboot.

## Relationship to Hybrid Mount

MuMount is based on Hybrid Mount by the Hybrid Mount Developers, licensed under **GPL-3.0-only**. MuSu Next bundles the upstream binary, scripts and kernel modules unchanged, renaming only the user-facing name and the internal module id. See `SOURCE.txt` in the payload and the upstream project for details.
