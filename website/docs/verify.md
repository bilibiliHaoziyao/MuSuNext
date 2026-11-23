---
title: Signature check
description: Verify an APK's signing fingerprint in the browser to confirm it is an official MuSu Next build
---

# Signature check

Root tools are a common target for repackaging. Drop an APK below and the browser reads its signing certificate fingerprint, then compares it against the official one.

Everything runs locally. Your file is never uploaded anywhere.

<SignatureCheck lang="en" />

## Official fingerprint

```
eeed9504acee1543f84e57916ef50f88b69bc9abbc72fc80f803a42267a5ead0
```

This is the same value the kernel checks in `KSU_EXPECTED_HASH`. It is how the kernel recognises the manager.

## If it does not match

The file was not signed with the release key. It may have been swapped for something else, or it may be a build someone made themselves. Don't install the first; for the second, make sure you trust the source.

## Doing it from the command line

```sh
apksigner verify --print-certs your.apk
```

The `SHA-256 digest` in the output is the fingerprint above.
