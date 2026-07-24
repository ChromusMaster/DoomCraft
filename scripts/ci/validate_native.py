#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path


def fail(message: str) -> None:
    raise SystemExit(f"validate_native: {message}")


def main() -> None:
    if len(sys.argv) != 3:
        fail("usage: validate_native.py <platform> <directory>")
    platform_id = sys.argv[1]
    directory = Path(sys.argv[2]).resolve()
    manifest = directory / "native-manifest.txt"
    build_id = directory / "native-build-id.txt"
    if not manifest.is_file() or not build_id.is_file():
        fail(f"manifest/build id missing in {directory}")

    entries = [
        line.strip()
        for line in manifest.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    if not entries:
        fail("empty native manifest")
    for entry in entries:
        if ".." in Path(entry).parts or Path(entry).is_absolute():
            fail(f"unsafe manifest entry: {entry}")
        if not (directory / entry).is_file():
            fail(f"manifest entry is missing: {entry}")

    executables = {
        "linux-x86_64": Path("lzdoom"),
        "windows-x86_64": Path("lzdoom.exe"),
        "macos-x86_64": Path("lzdoom.app/Contents/MacOS/lzdoom"),
        "macos-arm64": Path("lzdoom.app/Contents/MacOS/lzdoom"),
    }
    executable = directory / executables[platform_id]
    if not executable.is_file() or executable.stat().st_size <= 0:
        fail(f"missing executable: {executable}")

    for relative in (
        "soundfonts/lzdoom.sf2",
        "fm_banks/GENMIDI.GS.wopl",
        "fm_banks/gs-by-papiezak-and-sneakernets.wopn",
    ):
        # macOS runtime resources live beside the app executable.
        candidate = (
            directory / "lzdoom.app/Contents/MacOS" / relative
            if platform_id.startswith("macos")
            else directory / relative
        )
        if not candidate.is_file() or candidate.stat().st_size <= 0:
            fail(f"missing runtime resource: {candidate}")

    pk3_files = list(directory.rglob("*.pk3"))
    if not pk3_files:
        fail("no PK3 files were packaged")
    forbidden = [path for path in directory.rglob("*") if path.suffix.lower() == ".wad"]
    if forbidden:
        fail(f"WAD files must not be packaged: {forbidden}")

    print(f"{platform_id}: {len(entries)} manifest files, executable={executable}")


if __name__ == "__main__":
    main()
