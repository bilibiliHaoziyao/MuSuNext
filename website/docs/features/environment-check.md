# Environment Check

> Available since 0.0.7. Native UI, no WebView.

Environment Check reports **what a normal, unauthorized app can actually see** on your device — not what your root manager can see.

## Why the perspective matters

KernelSU hides root **per UID**. Your manager has root and sees the full filesystem; an unauthorized app sees a filtered view where module files are unmounted and root binaries are hidden.

Scanning from the manager's own perspective would only prove "this device is rooted" — which tells you nothing. Environment Check instead forks a probe that drops to an **unauthorized UID** and observes the device through that lens, so the result reflects the real exposure surface a detection target (banking app, anti-cheat) faces.

## Where to find it

**Safety tab → Environment Check**.

Press **Start check** to run the probe. Results appear as a list:

| State | Meaning |
|---|---|
| Exposed | The unauthorized UID can observe this trace |
| Clean | The trace is not visible from the unauthorized view |
| Not verified | The probe could not check this item (for example SELinux restrictions) |

Each item shows a short detail line explaining what was observed, so you can act on it — for example, if a module mount is exposed, open that app's profile in **Superuser** and enable "unmount modules" for it.

## What is checked

- `su` binaries (`/system/bin/su`, `/system/xbin/su`, `/data/adb/ksu/bin/su`)
- Access to `/data/adb` and `/data/adb/ksu`
- Module mounts visible in `/proc/self/mounts`
- Visibility of `ksud`, `zygiskd`, and `muz-*` processes
- SELinux enforce state, `ro.debuggable` / `ro.secure`
- Common detection paths such as `/sbin/magisk`, `/.magisk`

## Limits — read this

This is a **checkup report, not a certificate**:

- It only covers local, static traces. It cannot test Play Integrity, server-side risk control, or behavioural checks.
- The result is not a guarantee that any particular app will pass — apps use their own private detection logic.
- If the probe cannot drop privileges (rare, SELinux-dependent), the command fails explicitly and never returns a fake "all clean" result.

The value is actionable: it tells you which vectors are exposed and where to go next (usually an app profile setting).
