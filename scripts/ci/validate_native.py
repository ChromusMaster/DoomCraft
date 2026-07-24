#!/usr/bin/env python3
from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


def fail(message: str) -> None:
    raise SystemExit(f"validate_native: {message}")


def validate_linux_runtime(
    directory: Path,
    executable: Path,
    manifest_entries: set[str],
) -> None:
    bundled_vpx = sorted(
        path
        for path in directory.glob("libvpx.so.*")
        if path.is_file() and path.stat().st_size > 0
    )
    if not bundled_vpx:
        fail(
            "Linux package does not contain a versioned libvpx.so.* "
            "required by the LZDoom executable"
        )

    for library in bundled_vpx:
        relative = library.relative_to(directory).as_posix()
        if relative not in manifest_entries:
            fail(
                f"Bundled Linux dependency is absent from manifest: "
                f"{relative}"
            )

    env = os.environ.copy()
    existing = env.get("LD_LIBRARY_PATH", "").strip()
    local = str(directory)
    env["LD_LIBRARY_PATH"] = (
        local if not existing else local + os.pathsep + existing
    )

    result = subprocess.run(
        ["ldd", str(executable)],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        env=env,
    )
    output = result.stdout or ""
    if result.returncode != 0:
        fail(
            f"ldd failed with exit code {result.returncode}:\n{output}"
        )

    vpx_lines = [
        line.strip()
        for line in output.splitlines()
        if line.strip().startswith("libvpx.so.")
    ]
    if not vpx_lines:
        fail(
            "ldd did not report a libvpx dependency for the "
            f"packaged executable:\n{output}"
        )

    runtime_root = directory.resolve()
    for line in vpx_lines:
        if "=> not found" in line:
            fail(f"Packaged libvpx is unresolved: {line}")

        target_text = line.split("=>", 1)[1].split(" (", 1)[0].strip()
        target = Path(target_text).resolve()
        if target.parent != runtime_root:
            fail(
                "libvpx resolved outside the native package: "
                f"{target}"
            )


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

    entry_set = set(entries)
    for entry in entries:
        if ".." in Path(entry).parts or Path(entry).is_absolute():
            fail(f"unsafe manifest entry: {entry}")
        if not (directory / entry).is_file():
            fail(f"manifest entry is missing: {entry}")

    executables = {
        "linux-x86_64": Path("lzdoom"),
        "windows-x86_64": Path("lzdoom.exe"),
        "macos-x86_64": Path(
            "lzdoom.app/Contents/MacOS/lzdoom"
        ),
        "macos-arm64": Path(
            "lzdoom.app/Contents/MacOS/lzdoom"
        ),
    }

    if platform_id not in executables:
        fail(f"unsupported platform id: {platform_id}")

    executable = directory / executables[platform_id]
    if not executable.is_file() or executable.stat().st_size <= 0:
        fail(f"missing executable: {executable}")

    for relative in (
        "soundfonts/lzdoom.sf2",
        "fm_banks/GENMIDI.GS.wopl",
        "fm_banks/gs-by-papiezak-and-sneakernets.wopn",
    ):
        candidate = (
            directory /
            "lzdoom.app/Contents/MacOS" /
            relative
            if platform_id.startswith("macos")
            else directory / relative
        )
        if not candidate.is_file() or candidate.stat().st_size <= 0:
            fail(f"missing runtime resource: {candidate}")

    pk3_files = list(directory.rglob("*.pk3"))
    if not pk3_files:
        fail("no PK3 files were packaged")

    forbidden = [
        path
        for path in directory.rglob("*")
        if path.suffix.lower() == ".wad"
    ]
    if forbidden:
        fail(f"WAD files must not be packaged: {forbidden}")

    if platform_id == "linux-x86_64":
        validate_linux_runtime(
            directory,
            executable,
            entry_set,
        )

    print(
        f"{platform_id}: {len(entries)} manifest files, "
        f"executable={executable}"
    )


if __name__ == "__main__":
    main()
