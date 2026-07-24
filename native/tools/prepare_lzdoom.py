#!/usr/bin/env python3
"""Extract the vendored LZDoom source and apply DoomCraft's bridge patches."""
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
    Path("src/common/platform/posix/cocoa/i_video.mm"),
)
BRIDGE_FILES = ("doomcraft_bridge.h", "doomcraft_bridge.cpp")


def fail(message: str) -> NoReturn:
    raise SystemExit(f"prepare_lzdoom: {message}")


def source_is_complete(path: Path) -> bool:
    return path.is_dir() and all((path / item).is_file() for item in REQUIRED_SOURCE_FILES)


def remove_path(path: Path) -> None:
    if path.is_symlink() or path.is_file():
        path.unlink()
    elif path.is_dir():
        shutil.rmtree(path)


def validate_archive_entry(info: zipfile.ZipInfo, root: Path) -> None:
    member = Path(info.filename)
    if member.is_absolute() or ".." in member.parts:
        fail(f"unsafe path in archive: {info.filename}")
    destination = (root / member).resolve()
    if not destination.is_relative_to(root.resolve()):
        fail(f"unsafe path in archive: {info.filename}")


def locate_source_root(root: Path) -> Path:
    candidates = []
    for main_source in root.rglob("src/d_main.cpp"):
        candidate = main_source.parent.parent
        if source_is_complete(candidate):
            candidates.append(candidate.resolve())
    candidates = sorted(set(candidates), key=lambda path: (len(path.parts), str(path)))
    if len(candidates) != 1:
        fail("expected exactly one complete LZDoom source root, found: " + repr(candidates))
    return candidates[0]


def extract() -> None:
    # Gradle may pre-create outputs.dir. Existence alone is not a valid extraction.
    if source_is_complete(SOURCE):
        print(f"LZDoom source already extracted: {SOURCE}")
        return

    if SOURCE.exists() or SOURCE.is_symlink():
        print(f"Removing incomplete source directory: {SOURCE}")
        remove_path(SOURCE)

    if not ARCHIVE.is_file():
        fail(f"missing archive: {ARCHIVE}")

    WORK_PARENT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".lzdoom-extract-", dir=WORK_PARENT) as temp:
        extraction_root = Path(temp).resolve()
        with zipfile.ZipFile(ARCHIVE) as archive:
            bad_member = archive.testzip()
            if bad_member is not None:
                fail(f"corrupt archive member: {bad_member}")
            for info in archive.infolist():
                validate_archive_entry(info, extraction_root)
            archive.extractall(extraction_root)
        extracted = locate_source_root(extraction_root)
        shutil.move(str(extracted), str(SOURCE))

    if not source_is_complete(SOURCE):
        fail(f"extracted source is incomplete: {SOURCE}")
    print(f"Extracted LZDoom source: {SOURCE}")


def copy_bridge() -> None:
    destination = SOURCE / "src"
    destination.mkdir(parents=True, exist_ok=True)
    for name in BRIDGE_FILES:
        source = BRIDGE / name
        if not source.is_file():
            fail(f"missing bridge source: {source}")
        shutil.copy2(source, destination / name)


def patch_cmake() -> None:
    path = SOURCE / "src/CMakeLists.txt"
    text = path.read_text(encoding="utf-8")
    marker = "\tdoomcraft_bridge.cpp\n"
    if marker not in text:
        anchor = "\td_main.cpp\n"
        if anchor not in text:
            fail("could not locate d_main.cpp in src/CMakeLists.txt")
        path.write_text(text.replace(anchor, anchor + marker, 1), encoding="utf-8")


def patch_display() -> None:
    path = SOURCE / "src/d_main.cpp"
    text = path.read_text(encoding="utf-8")
    include = '#include "doomcraft_bridge.h"\n'
    if include not in text:
        anchor = '#include "v_palette.h"\n'
        if anchor not in text:
            fail("could not locate include patch point in d_main.cpp")
        text = text.replace(anchor, anchor + include, 1)
    call = "\tDoomCraftBridge::OnFrame(screen);\n"
    if call not in text:
        anchor = "\tscreen->Update();\n"
        if anchor not in text:
            fail("could not locate screen->Update patch point in d_main.cpp")
        text = text.replace(anchor, anchor + call, 1)
    path.write_text(text, encoding="utf-8")


def patch_cocoa_no_vulkan() -> None:
    """
    Fix LZDoom 4.14.4's Cocoa backend when configured with HAVE_VULKAN=OFF.

    Upstream guards the Vulkan includes and the m_vulkanSurface member, but
    leaves the I_CreateVulkanSurface declaration and destructor reset
    unguarded. That makes both macOS x86_64 and ARM64 fail to compile.
    """
    path = SOURCE / "src/common/platform/posix/cocoa/i_video.mm"
    text = path.read_text(encoding="utf-8")

    declaration = (
        "bool I_CreateVulkanSurface(VkInstance instance, VkSurfaceKHR *surface);\n"
    )
    guarded_declaration = (
        "#ifdef HAVE_VULKAN\n"
        "bool I_CreateVulkanSurface(VkInstance instance, VkSurfaceKHR *surface);\n"
        "#endif\n"
    )

    if guarded_declaration not in text:
        if declaration not in text:
            fail("could not locate Cocoa Vulkan surface declaration")
        text = text.replace(declaration, guarded_declaration, 1)

    reset = "\t\tm_vulkanSurface.reset();\n"
    guarded_reset = (
        "#ifdef HAVE_VULKAN\n"
        "\t\tm_vulkanSurface.reset();\n"
        "#endif\n"
    )

    if guarded_reset not in text:
        if reset not in text:
            fail("could not locate Cocoa Vulkan surface reset")
        text = text.replace(reset, guarded_reset, 1)

    path.write_text(text, encoding="utf-8")



def patch_macos_deployment_target() -> None:
    """
    Make LZDoom respect the deployment target supplied by DoomCraft.

    LZDoom 4.14.4 hardcodes macOS 10.13 after project configuration. That
    overrides -DCMAKE_OSX_DEPLOYMENT_TARGET and makes std::filesystem
    unavailable. DoomCraft requires macOS 10.15 on Intel and macOS 11.0 on
    Apple Silicon.
    """
    path = SOURCE / "CMakeLists.txt"
    text = path.read_text(encoding="utf-8")

    original = '\t\tset(CMAKE_OSX_DEPLOYMENT_TARGET "10.13")\n'
    replacement = (
        "\t\tif(NOT CMAKE_OSX_DEPLOYMENT_TARGET)\n"
        '\t\t\tset(CMAKE_OSX_DEPLOYMENT_TARGET "10.15" CACHE STRING '
        '"Minimum supported macOS version")\n'
        "\t\tendif()\n"
    )

    if replacement not in text:
        if original not in text:
            fail("could not locate LZDoom macOS deployment target override")
        text = text.replace(original, replacement, 1)

    path.write_text(text, encoding="utf-8")

def verify() -> None:
    required_files = [
        SOURCE / "src/doomcraft_bridge.h",
        SOURCE / "src/doomcraft_bridge.cpp",
        SOURCE / "src/CMakeLists.txt",
        SOURCE / "src/d_main.cpp",
        SOURCE / "src/common/platform/posix/cocoa/i_video.mm",
        SOURCE / "CMakeLists.txt",
    ]
    missing = [str(path) for path in required_files if not path.is_file()]
    if missing:
        fail("missing patched files: " + ", ".join(missing))

    cmake = (SOURCE / "src/CMakeLists.txt").read_text(encoding="utf-8")
    main = (SOURCE / "src/d_main.cpp").read_text(encoding="utf-8")
    cocoa = (
        SOURCE / "src/common/platform/posix/cocoa/i_video.mm"
    ).read_text(encoding="utf-8")
    root_cmake = (SOURCE / "CMakeLists.txt").read_text(encoding="utf-8")

    checks = (
        ("doomcraft_bridge.cpp" in cmake, "CMake source registration"),
        ('#include "doomcraft_bridge.h"' in main, "bridge include"),
        ("DoomCraftBridge::OnFrame(screen);" in main, "frame hook"),
        (
            "#ifdef HAVE_VULKAN\n"
            "bool I_CreateVulkanSurface(VkInstance instance, VkSurfaceKHR *surface);\n"
            "#endif\n" in cocoa,
            "Cocoa Vulkan declaration guard",
        ),
        (
            "#ifdef HAVE_VULKAN\n"
            "\t\tm_vulkanSurface.reset();\n"
            "#endif\n" in cocoa,
            "Cocoa Vulkan destructor guard",
        ),
        (
            'if(NOT CMAKE_OSX_DEPLOYMENT_TARGET)' in root_cmake
            and '"10.15" CACHE STRING' in root_cmake,
            "macOS deployment target override guard",
        ),
    )
    failed = [name for ok, name in checks if not ok]
    if failed:
        fail("patch verification failed: " + ", ".join(failed))


def main() -> None:
    extract()
    copy_bridge()
    patch_cmake()
    patch_display()
    patch_cocoa_no_vulkan()
    patch_macos_deployment_target()
    verify()
    print(f"Prepared patched LZDoom source: {SOURCE}")


if __name__ == "__main__":
    main()
