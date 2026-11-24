---
description: The Security tab offers a Root access audit panel, aggregating every Root request by app with time, count, and type.
---

# Root Access Audit

MuSu Next adds a **Security** tab in the middle of the manager's bottom bar, dedicated to one question: **who on my device is using Root, when, and how often?**

Instead of scrolling through raw SU log lines one by one, the Security tab aggregates scattered events by app, so you can see at a glance which apps call Root frequently.

## Enable auditing

Open the Security tab. The status card at the top shows whether auditing is on:

- **Audit enabled**: the kernel is writing Root-related events into the `sulog` log files, and the statistics below keep updating.
- **Audit disabled**: tap **Enable audit** to start recording.

Auditing relies on the kernel's `sulog` event channel. Once enabled, no reboot is needed — new Root requests are recorded immediately.

## View by app

Below the status card is the **By app** list. Each entry represents an app that has made a Root request and shows:

- **App identity**: the process `comm` (or executable name `exe`)
- **Call count**: total Root events triggered by this app
- **Last call time**: timestamp of the most recent request
- **Event type tags**, broken down by category:
  - `root_execve`: command executed in a Root context
  - `sucompat`: `su` compatibility calls
  - `ioctl_grant_root`: Root granted through ioctl

The list is sorted by call count, highest first, so your most-used Root apps appear at the top. The page refreshes automatically when opened, and you can pull down to refresh manually.

## View detailed records

To inspect the original text of each call (full command line, timestamps, etc.), tap **View full SU log** below the list. The log page supports filtering by type and searching.

## How it fits with other features

- **App Profile** restricts the Root privileges of each app; the Security tab records the accesses that actually happened. Together they let you both grant and audit.
- **SU Log**: the Security tab aggregates from the same `sulog` data. The log page shows raw records and lets you clean them.

## Want to temporarily disable Root?

The Security tab is *retrospective auditing* — it records accesses that already happened. If you need to block Root in certain scenarios (banking apps, game anti-cheat), use App Profile to revoke permission for specific apps instead of uninstalling modules.
