#!/usr/bin/env python3
from __future__ import annotations

import shutil
import sys
from pathlib import Path

PLATFORMS = (
    "linux-x86_64",
    "windows-x86_64",
    "macos-x86_64",
    "macos-arm64",
)


def fail(message: str) -> None:
    raise SystemExit(f"assemble_natives: {message}")


def main() -> None:
    if len(sys.argv) != 3:
        fail("usage: assemble_natives.py <artifact-root> <destination>")
    artifact_root = Path(sys.argv[1]).resolve()
    destination = Path(sys.argv[2]).resolve()
    destination.mkdir(parents=True, exist_ok=True)

    for platform_id in PLATFORMS:
        source = artifact_root / f"native-{platform_id}"
        if not source.is_dir():
            fail(f"artifact directory missing: {source}")
        target = destination / platform_id
        if target.exists():
            shutil.rmtree(target)
        shutil.copytree(source, target)
        print(f"Assembled {platform_id}: {target}")


if __name__ == "__main__":
    main()
