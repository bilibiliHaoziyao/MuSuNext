#!/usr/bin/env python3
"""校验 APK 内是否真的内置了 musu.ko。

管理器修补 boot 时不联网：ksud 通过 rust-embed 把 `{kmi}_musu.ko` 编进
`libksud.so`，运行时按 KMI 取出来塞进 ramdisk。所以只要 libksud.so 里
找不到这些名字，弹窗就会是空的，用户点了修补也一定失败。

本脚本直接读 APK（zip）里的 lib/<abi>/libksud.so，在二进制里找
`android12-5.10_musu.ko` 这类完整文件名，逐个 ABI 报告命中情况。

退出码：
  0  必需 ABI 全部命中
  1  有缺失
  2  输入文件不存在或不是合法 APK

用法：
  python3 scripts/verify_apk_lkm.py dist/MuSuNext-v0.0.4-release.apk
  python3 scripts/verify_apk_lkm.py --so libksud.so --abi arm64-v8a
"""

from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path

# 与 .github/workflows/build-lkm.yml 的 matrix 保持一致
SUPPORTED_KMIS = [
    "android12-5.10",
    "android13-5.10",
    "android13-5.15",
    "android14-5.15",
    "android14-6.1",
    "android15-6.6",
    "android16-6.12",
    "android17-6.18",
]

# 哪些 ABI 必须内置全部模块。riscv64 目前没有 LKM 构建，允许为空。
REQUIRED_ABIS = ("arm64-v8a", "x86_64")
OPTIONAL_ABIS = ("riscv64",)

MAGIC = b"_musu.ko"


def scan_so(data: bytes) -> tuple[list[str], bool]:
    """返回 (命中的 KMI 列表, 是否看到任何 _musu.ko 痕迹)。"""
    if MAGIC not in data:
        return [], False
    hits = [kmi for kmi in SUPPORTED_KMIS if f"{kmi}_musu.ko".encode() in data]
    return hits, True


def check_apk(path: Path) -> int:
    if not path.is_file():
        print(f"错误：找不到 {path}", file=sys.stderr)
        return 2
    try:
        with zipfile.ZipFile(path) as zf:
            names = zf.namelist()
            targets: dict[str, str] = {}
            for name in names:
                if name.startswith("lib/") and name.endswith("/libksud.so"):
                    abi = name.split("/")[1]
                    targets[abi] = name
            if not targets:
                print("错误：APK 里没有 lib/*/libksud.so", file=sys.stderr)
                return 2

            print(f"APK: {path}")
            print(f"     {path.stat().st_size / 1024 / 1024:.1f} MB")
            print()

            failures: list[str] = []
            for abi in REQUIRED_ABIS + OPTIONAL_ABIS:
                entry = targets.get(abi)
                required = abi in REQUIRED_ABIS
                if entry is None:
                    if required:
                        failures.append(f"{abi}: 缺少 libksud.so")
                        print(f"  {abi:<10} 缺失 libksud.so")
                    continue

                hits, any_magic = scan_so(zf.read(entry))
                total = len(SUPPORTED_KMIS)
                mark = "OK" if len(hits) == total else ("部分" if hits else "空")
                suffix = "" if required or len(hits) == total else "（该 ABI 不要求内置）"
                print(f"  {abi:<10} {len(hits)}/{total} {mark}{suffix}")
                if hits and len(hits) < total:
                    missing = [k for k in SUPPORTED_KMIS if k not in hits]
                    print(f"             缺少：{', '.join(missing)}")
                if not hits and not any_magic:
                    print("             未发现 _musu.ko 痕迹，libksud.so 可能不是带 LKM 的构建")
                if required and len(hits) < total:
                    failures.append(f"{abi}: 只内置 {len(hits)}/{total} 个 KMI")

            extra = [a for a in targets if a not in REQUIRED_ABIS + OPTIONAL_ABIS]
            if extra:
                print(f"  其他 ABI：{', '.join(extra)}")

            print()
            if failures:
                print("结论：内置不完整 —— 用户选完 KMI 修补会失败。", file=sys.stderr)
                for f in failures:
                    print(f"  - {f}", file=sys.stderr)
                return 1

            print(f"结论：内置完整（{len(SUPPORTED_KMIS)} 个 KMI × {len(REQUIRED_ABIS)} 个 ABI）。")
            return 0
    except zipfile.BadZipFile:
        print(f"错误：{path} 不是合法 APK/zip", file=sys.stderr)
        return 2


def check_so(path: Path, abi: str) -> int:
    if not path.is_file():
        print(f"错误：找不到 {path}", file=sys.stderr)
        return 2
    hits, any_magic = scan_so(path.read_bytes())
    print(f"{abi}: {len(hits)}/{len(SUPPORTED_KMIS)}")
    for kmi in hits:
        print(f"  + {kmi}_musu.ko")
    if not any_magic:
        print("  未发现 _musu.ko 痕迹")
    return 0 if len(hits) == len(SUPPORTED_KMIS) else 1


def main() -> int:
    parser = argparse.ArgumentParser(description="校验 APK 内置的 musu.ko")
    parser.add_argument("apk", nargs="?", help="APK 路径")
    parser.add_argument("--so", help="直接检查一个 libksud.so（调试用）")
    parser.add_argument("--abi", default="unknown", help="配合 --so 使用的 ABI 名称")
    args = parser.parse_args()

    if args.so:
        return check_so(Path(args.so), args.abi)
    if not args.apk:
        parser.print_help()
        return 2
    return check_apk(Path(args.apk))


if __name__ == "__main__":
    sys.exit(main())
