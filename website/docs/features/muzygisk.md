---
description: MuZygisk is MuSu Next's built-in Zygisk implementation, forked from ReZygisk, disabled by default and managed natively in Settings.
---

# MuZygisk

MuZygisk is MuSu Next's built-in [Zygisk](https://github.com/topjohnwu/Magisk) implementation. It is a fork of [ReZygisk](https://github.com/PerformanC/ReZygisk), rebuilt and renamed for MuSu Next. It lets you use Zygisk modules without flashing a separate Zygisk provider.

> MuZygisk is **disabled by default**. Turning it on requires a **full reboot**.

## Where to find it

MuZygisk does **not** appear in the module list. Open the manager's **Settings** page and enter **MuZygisk** from there. The management screen is fully native (no WebUI), with one master switch at the top.

## Statuses

| Status | Meaning | Switch |
|---|---|---|
| Off | Not enabled; no Zygisk injection is active | Available |
| Enabled · reboot required | Switched on, waiting for a full reboot to take effect | Available |
| Running | The Zygisk daemon is up and injecting | Available (turn off to disable) |
| Blocked | Another Zygisk implementation is already installed | Disabled (grey) |
| No payload | This device's architecture has no MuZygisk build (e.g. riscv64) | Disabled (grey) |

## Mutual exclusion

MuZygisk cannot coexist with another Zygisk provider (such as Zygisk Next or a kernel with built-in Zygisk).

- If another provider is installed, the MuZygisk switch is **greyed out** and the page names the conflicting module, with a one-tap path to disable it in the module list.
- If MuZygisk is already enabled, those other providers cannot be enabled from the module list — their switches are greyed out too.

This is a hard mutual exclusion: running two Zygisk implementations at once would cause double injection and boot failures.

## Enable or disable

1. Open **Settings → MuZygisk**.
2. Read the status card at the top; resolve any conflict first.
3. Toggle the switch.
4. Perform a **full reboot** — a soft reboot does not restart the Zygisk daemon.

When you turn MuZygisk off, the daemon and its runtime files are cleaned up on the next full reboot.

## Relationship to ReZygisk

MuZygisk is derived from ReZygisk by PerformanC, licensed under **AGPL-3.0-or-later**. The fork changes storage paths to MuSu Next's own directories, drops the module-install scripts and WebUI, and adds MuSu Next manager detection. MuSu Next's userspace remains GPL-3.0-or-later; the combined distribution is permitted by GPLv3 §13, and the AGPL's network-interaction terms apply to the combined work. See `muzygisk/LICENSE` and `muzygisk/NOTICE` in the source tree.
