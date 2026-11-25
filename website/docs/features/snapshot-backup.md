# Snapshot Backup & Restore

> Available since 0.0.7. Native UI, no WebView.

Snapshots back up your environment configuration so you can restore it after switching devices or flashing a ROM — without hunting down every module and re-granting every app.

## What is included

| Item | Detail |
|---|---|
| Modules | Each installed module's id and enabled/disabled state |
| Root authorizations | Every app profile (uid, allow_su, gid/groups, SELinux context) |
| Built-in components | MuZygisk / MuMount on/off state |

A snapshot does **not** contain module zip files, app data, or system images. It records configuration state only. Modules must already be installed for a restore to toggle their state.

## Where to find it

**Settings → Backup & Restore** (or the Safety tab entry, depending on your UI version).

- **Create**: writes `snapshot-<timestamp>.tar.gz` under `/data/adb/ksu/snapshot/`, with a manifest listing counts and size.
- **List**: shows each snapshot's time, module/profile/component counts, and size.
- **Restore**: applies the selected snapshot's states.
- **Delete**: removes a snapshot file.

## Restore is destructive — be careful

Before any restore, MuSu automatically creates a `.pre-restore` snapshot of the current state, so you can roll back if needed. The UI asks for a second confirmation and spells out what will be overwritten:

- Module enabled/disabled flags
- The entire root authorization list
- MuZygisk / MuMount switches

After a successful restore you will be asked to do a **full reboot** — module mount states and root profiles only fully apply at boot.

## Typical workflow

1. Before a ROM flash or device switch: **Create** a snapshot.
2. After setup: install the same modules, then **Restore** the snapshot.
3. Reboot fully. Verify module states and app authorizations.
4. If something is wrong, restore the `.pre-restore` snapshot.
