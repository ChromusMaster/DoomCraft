#!/usr/bin/env python3
from __future__ import annotations

import sys
import zipfile
from pathlib import Path

PLATFORMS = (
    "linux-x86_64",
    "windows-x86_64",
    "macos-x86_64",
    "macos-arm64",
)


def fail(message: str) -> None:
    raise SystemExit(f"validate_jar: {message}")


def main() -> None:
    if len(sys.argv) != 2:
        fail("usage: validate_jar.py <jar>")
    jar = Path(sys.argv[1])
    if not jar.is_file():
        fail(f"JAR not found: {jar}")
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
    for platform_id in PLATFORMS:
        required = {
            f"natives/{platform_id}/native-manifest.txt",
            f"natives/{platform_id}/native-build-id.txt",
        }
        if platform_id.startswith("windows"):
            required.add(f"natives/{platform_id}/lzdoom.exe")
        elif platform_id.startswith("macos"):
            required.add(f"natives/{platform_id}/lzdoom.app/Contents/MacOS/lzdoom")
        else:
            required.add(f"natives/{platform_id}/lzdoom")
        missing = sorted(required - names)
        if missing:
            fail(f"{platform_id} entries missing: {missing}")
    print(f"Validated multiplatform JAR: {jar} ({jar.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
