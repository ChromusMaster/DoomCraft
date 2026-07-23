#!/usr/bin/env python3
"""Extract the vendored LZDoom source and apply DoomCraft's bridge patch."""
from __future__ import annotations

import shutil
import tempfile
import zipfile
from pathlib import Path
from typing import NoReturn

PROJECT = Path(__file__).resolve().parents[2]
ARCHIVE = PROJECT / "native/vendor/lzdoom-l4.14.4.zip"
WORK_PARENT = PROJECT / "native/work"
SOURCE = WORK_PARENT / "lzdoom-l4.14.4"
BRIDGE = PROJECT / "native/bridge"

REQUIRED_SOURCE_FILES = (
    Path("CMakeLists.txt"),
    Path("src/CMakeLists.txt"),
    Path("src/d_main.cpp"),
)

BRIDGE_FILES = (
    "doomcraft_bridge.h",
    "doomcraft_bridge.cpp",
)


def fail(message: str) -> NoReturn:
    raise SystemExit(f"prepare_lzdoom: {message}")


def source_is_complete(path: Path) -> bool:
    return path.is_dir() and all(
        (path / relative).is_file()
        for relative in REQUIRED_SOURCE_FILES
    )


def remove_path(path: Path) -> None:
    if path.is_symlink() or path.is_file():
        path.unlink()
    elif path.is_dir():
        shutil.rmtree(path)


def validate_archive_entry(info: zipfile.ZipInfo, extraction_root: Path) -> None:
    member = Path(info.filename)

    if member.is_absolute() or ".." in member.parts:
        fail(f"unsafe path in LZDoom archive: {info.filename}")

    destination = (extraction_root / member).resolve()
    if not destination.is_relative_to(extraction_root.resolve()):
        fail(f"unsafe path in LZDoom archive: {info.filename}")


def locate_source_root(extraction_root: Path) -> Path:
    candidates: list[Path] = []

    for d_main in extraction_root.rglob("src/d_main.cpp"):
        candidate = d_main.parent.parent

        if source_is_complete(candidate):
            candidates.append(candidate)

    unique_candidates = sorted(
        {candidate.resolve() for candidate in candidates},
        key=lambda candidate: (len(candidate.parts), str(candidate)),
    )

    if not unique_candidates:
        listing = sorted(
            path.relative_to(extraction_root).as_posix()
            for path in extraction_root.iterdir()
        )
        fail(
            "could not locate a complete LZDoom source root after extraction. "
            f"Top-level entries: {listing}"
        )

    if len(unique_candidates) > 1:
        fail(
            "multiple possible LZDoom source roots were found: "
            + ", ".join(str(candidate) for candidate in unique_candidates)
        )

    return unique_candidates[0]


def extract() -> None:
    # Gradle may pre-create an outputs.dir before this script starts.
    # Therefore, directory existence alone does not mean extraction succeeded.
    if source_is_complete(SOURCE):
        print(f"LZDoom source already prepared for patching: {SOURCE}")
        return

    if SOURCE.exists() or SOURCE.is_symlink():
        print(f"Removing incomplete LZDoom work directory: {SOURCE}")
        remove_path(SOURCE)

    if not ARCHIVE.is_file():
        fail(f"missing archive: {ARCHIVE}")

    WORK_PARENT.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(
        prefix=".lzdoom-extract-",
        dir=WORK_PARENT,
    ) as temporary:
        extraction_root = Path(temporary).resolve()

        with zipfile.ZipFile(ARCHIVE) as archive:
            bad_member = archive.testzip()
            if bad_member is not None:
                fail(f"corrupted archive member: {bad_member}")

            for info in archive.infolist():
                validate_archive_entry(info, extraction_root)

            archive.extractall(extraction_root)

        extracted_source = locate_source_root(extraction_root)
        SOURCE.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(extracted_source), str(SOURCE))

    if not source_is_complete(SOURCE):
        fail(f"extraction completed but source tree is incomplete: {SOURCE}")

    print(f"Extracted LZDoom source: {SOURCE}")


def validate_bridge_sources() -> None:
    missing = [
        str(BRIDGE / name)
        for name in BRIDGE_FILES
        if not (BRIDGE / name).is_file()
    ]
    if missing:
        fail("missing DoomCraft bridge source file(s): " + ", ".join(missing))


def copy_bridge() -> None:
    if not source_is_complete(SOURCE):
        fail(f"cannot copy bridge into incomplete source tree: {SOURCE}")

    validate_bridge_sources()
    destination = SOURCE / "src"
    destination.mkdir(parents=True, exist_ok=True)

    for name in BRIDGE_FILES:
        shutil.copy2(BRIDGE / name, destination / name)


def patch_cmake() -> None:
    path = SOURCE / "src/CMakeLists.txt"
    text = path.read_text(encoding="utf-8")

    marker = "\tdoomcraft_bridge.cpp\n"
    if marker not in text:
        anchor = "\td_main.cpp\n"
        if anchor not in text:
            fail("could not find d_main.cpp in src/CMakeLists.txt")

        text = text.replace(anchor, anchor + marker, 1)
        path.write_text(text, encoding="utf-8")


def patch_display() -> None:
    path = SOURCE / "src/d_main.cpp"
    text = path.read_text(encoding="utf-8")

    include = '#include "doomcraft_bridge.h"\n'
    if include not in text:
        anchor = '#include "v_palette.h"\n'
        if anchor not in text:
            fail("could not find include patch point in d_main.cpp")

        text = text.replace(anchor, anchor + include, 1)

    call = "\tDoomCraftBridge::OnFrame(screen);\n"
    if call not in text:
        anchor = "\tscreen->Update();\n"
        if anchor not in text:
            fail("could not find screen->Update patch point in d_main.cpp")

        text = text.replace(anchor, anchor + call, 1)

    path.write_text(text, encoding="utf-8")


def verify() -> None:
    required_files = [
        SOURCE / "src/doomcraft_bridge.h",
        SOURCE / "src/doomcraft_bridge.cpp",
        SOURCE / "src/CMakeLists.txt",
        SOURCE / "src/d_main.cpp",
    ]

    missing_files = [
        str(path)
        for path in required_files
        if not path.is_file()
    ]
    if missing_files:
        fail("missing patched source file(s): " + ", ".join(missing_files))

    cmake = (SOURCE / "src/CMakeLists.txt").read_text(encoding="utf-8")
    main = (SOURCE / "src/d_main.cpp").read_text(encoding="utf-8")

    required_content = [
        ("doomcraft_bridge.cpp" in cmake, "CMake source registration"),
        ('#include "doomcraft_bridge.h"' in main, "bridge include"),
        ("DoomCraftBridge::OnFrame(screen);" in main, "frame hook"),
    ]

    missing_content = [
        name
        for present, name in required_content
        if not present
    ]
    if missing_content:
        fail("patch verification failed: " + ", ".join(missing_content))


def main() -> None:
    extract()
    copy_bridge()
    patch_cmake()
    patch_display()
    verify()
    print(f"Prepared patched LZDoom source: {SOURCE}")


if __name__ == "__main__":
    main()
