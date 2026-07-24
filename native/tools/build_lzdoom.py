#!/usr/bin/env python3
"""Build and package DoomCraft's patched LZDoom for the current platform."""
from __future__ import annotations

import hashlib
import os
import platform
import re
import shutil
import subprocess
import sys
from collections import deque
from pathlib import Path
from typing import Iterable, NoReturn

PROJECT = Path(__file__).resolve().parents[2]
PREPARE = PROJECT / "native/tools/prepare_lzdoom.py"
SOURCE = PROJECT / "native/work/lzdoom-l4.14.4"
DEPS = PROJECT / "native/deps"

ZMUSIC_VERSION = "1.1.14"
ZMUSIC_SOURCE = DEPS / f"zmusic-{ZMUSIC_VERSION}"
ZMUSIC_REPOSITORY = "https://github.com/ZDoom/ZMusic.git"

REQUIRED_RUNTIME_RESOURCES = (
    Path("soundfonts/lzdoom.sf2"),
    Path("fm_banks/GENMIDI.GS.wopl"),
    Path("fm_banks/gs-by-papiezak-and-sneakernets.wopn"),
)

SYSTEM_MAC_PREFIXES = ("/System/Library/", "/usr/lib/")


def fail(message: str) -> NoReturn:
    raise SystemExit(f"build_lzdoom: {message}")


def run(command: Iterable[str | Path], cwd: Path | None = None, capture: bool = False) -> str:
    args = [str(value) for value in command]
    print("+", subprocess.list2cmdline(args), flush=True)
    result = subprocess.run(
        args,
        cwd=cwd,
        check=True,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.STDOUT if capture else None,
    )
    return result.stdout if capture else ""


def host_platform_id() -> str:
    system = platform.system().lower()
    machine = platform.machine().lower()
    architecture = "arm64" if machine in {"arm64", "aarch64"} else "x86_64"
    if system == "windows":
        os_id = "windows"
    elif system == "darwin":
        os_id = "macos"
    elif system == "linux":
        os_id = "linux"
    else:
        fail(f"unsupported operating system: {platform.system()}")
    return f"{os_id}-{architecture}"


def expected_platform_id() -> str:
    actual = host_platform_id()
    expected = os.environ.get("DOOMCRAFT_PLATFORM_ID", actual).strip()
    if expected != actual:
        fail(f"runner mismatch: expected {expected}, detected {actual}")
    if expected not in {
        "linux-x86_64",
        "windows-x86_64",
        "macos-x86_64",
        "macos-arm64",
    }:
        fail(f"platform is not part of the DoomCraft 1.0 release matrix: {expected}")
    return expected


def split_cmake_arguments(variable: str) -> list[str]:
    return [item for item in os.environ.get(variable, "").split(";") if item]


def default_generator(pid: str) -> str:
    if pid.startswith("windows"):
        return "Visual Studio 17 2022"
    return "Ninja"


def git_exact_tag(path: Path) -> str:
    try:
        return run(
            ["git", "-C", path, "describe", "--tags", "--exact-match"],
            capture=True,
        ).strip()
    except subprocess.CalledProcessError:
        return ""


def ensure_zmusic(generator: str, pid: str) -> Path | None:
    # LZDoom 4.14.4 already vendors its matching static ZMusic library on MSVC.
    if pid.startswith("windows"):
        header = SOURCE / "bin/windows/zmusic/include/zmusic.h"
        if not header.is_file() or "zmusic_mod_preferredplayer" not in header.read_text(
            encoding="utf-8", errors="replace"
        ):
            fail("vendored Windows ZMusic API is missing or incompatible")
        print("Using LZDoom's vendored static ZMusic library for Windows")
        return None

    build = DEPS / "build-zmusic" / pid
    prefix = DEPS / "install-zmusic" / pid
    DEPS.mkdir(parents=True, exist_ok=True)

    if ZMUSIC_SOURCE.is_dir() and git_exact_tag(ZMUSIC_SOURCE) != ZMUSIC_VERSION:
        print(f"Removing incompatible ZMusic source: {ZMUSIC_SOURCE}")
        shutil.rmtree(ZMUSIC_SOURCE)

    if not ZMUSIC_SOURCE.is_dir():
        run([
            "git", "clone", "--depth", "1", "--branch", ZMUSIC_VERSION,
            ZMUSIC_REPOSITORY, ZMUSIC_SOURCE,
        ])

    header = ZMUSIC_SOURCE / "include/zmusic.h"
    if not header.is_file() or "zmusic_mod_preferredplayer" not in header.read_text(
        encoding="utf-8", errors="replace"
    ):
        fail(f"ZMusic {ZMUSIC_VERSION} does not expose zmusic_mod_preferredplayer")

    build.mkdir(parents=True, exist_ok=True)
    prefix.mkdir(parents=True, exist_ok=True)

    configure = [
        "cmake", "-S", ZMUSIC_SOURCE, "-B", build,
        "-G", generator,
        "-DCMAKE_BUILD_TYPE=Release",
        f"-DCMAKE_INSTALL_PREFIX={prefix}",
        "-DBUILD_SHARED_LIBS=ON",
        "-DZMUSIC_INSTALL=ON",
        "-DVCPKG_TOOLCHAIN=OFF",
    ]

    if pid.startswith("macos"):
        architecture = "arm64" if pid.endswith("arm64") else "x86_64"
        deployment = "11.0" if architecture == "arm64" else "10.15"
        configure.extend([
            f"-DCMAKE_OSX_ARCHITECTURES={architecture}",
            f"-DCMAKE_OSX_DEPLOYMENT_TARGET={deployment}",
            "-DCMAKE_INSTALL_RPATH=@loader_path",
            "-DCMAKE_BUILD_WITH_INSTALL_RPATH=ON",
        ])

    prefix_path = os.environ.get("DOOMCRAFT_CMAKE_PREFIX_PATH", "").strip()
    if prefix_path:
        configure.append(f"-DCMAKE_PREFIX_PATH={prefix_path}")

    configure.extend(split_cmake_arguments("DOOMCRAFT_ZMUSIC_CMAKE_ARGS"))
    run(configure)
    run(["cmake", "--build", build, "--config", "Release", "--parallel", "3"])
    run(["cmake", "--install", build, "--config", "Release"])

    installed_header = prefix / "include/zmusic.h"
    if not installed_header.is_file() or "zmusic_mod_preferredplayer" not in installed_header.read_text(
        encoding="utf-8", errors="replace"
    ):
        fail(f"installed ZMusic header is invalid: {installed_header}")
    return prefix


def find_zmusic_library(prefix: Path, pid: str) -> Path:
    patterns = ["libzmusic*.dylib"] if pid.startswith("macos") else ["libzmusic.so", "libzmusic.so.*"]
    candidates: list[Path] = []
    for pattern in patterns:
        candidates.extend(path for path in prefix.rglob(pattern) if path.is_file())
    if not candidates:
        fail(f"could not locate installed ZMusic library below {prefix}")
    # Prefer the unversioned linker name where present.
    candidates.sort(key=lambda path: (path.name not in {"libzmusic.so", "libzmusic.dylib"}, len(path.name)))
    return candidates[0]


def windows_vcpkg_toolchain() -> Path | None:
    root = os.environ.get("VCPKG_INSTALLATION_ROOT", "").strip()
    if not root:
        return None
    toolchain = Path(root) / "scripts/buildsystems/vcpkg.cmake"
    return toolchain if toolchain.is_file() else None


def configure_lzdoom(generator: str, pid: str, build: Path, zmusic_prefix: Path | None) -> None:
    configure: list[str | Path] = [
        "cmake", "-S", SOURCE, "-B", build,
        "-G", generator,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DPK3_QUIET_ZIPDIR=ON",
        "-DHAVE_VULKAN=OFF",
        "-DHAVE_GLES2=OFF",
        "-DNO_OPENAL=OFF",
    ]

    if pid.startswith("windows"):
        configure.extend(["-A", "x64"])
        toolchain = windows_vcpkg_toolchain()
        if toolchain is not None:
            # Static OpenAL avoids requiring users to install OpenAL separately.
            configure.extend([
                f"-DCMAKE_TOOLCHAIN_FILE={toolchain}",
                "-DVCPKG_TARGET_TRIPLET=x64-windows-static",
                "-DOPENAL_SOFT_VCPKG=ON",
                "-DDYN_OPENAL=OFF",
                "-DLIBVPX_VCPKG=OFF",
            ])
        else:
            print("WARNING: VCPKG_INSTALLATION_ROOT not found; building with dynamically loaded OpenAL")
            configure.append("-DDYN_OPENAL=ON")
    else:
        if zmusic_prefix is None:
            fail("ZMusic prefix is required on Linux and macOS")
        library = find_zmusic_library(zmusic_prefix, pid)
        configure.extend([
            f"-DCMAKE_PREFIX_PATH={zmusic_prefix}",
            f"-DZMUSIC_INCLUDE_DIR={zmusic_prefix / 'include'}",
            f"-DZMUSIC_LIBRARIES={library}",
            "-DDYN_OPENAL=OFF",
            "-DLIBVPX_VCPKG=OFF",
            "-DOPENAL_SOFT_VCPKG=OFF",
            "-DVCPKG_TOOLCHAIN=OFF",
        ])

        external_prefix = os.environ.get("DOOMCRAFT_CMAKE_PREFIX_PATH", "").strip()
        if external_prefix:
            configure[-7] = f"-DCMAKE_PREFIX_PATH={zmusic_prefix};{external_prefix}"

        if pid.startswith("macos"):
            architecture = "arm64" if pid.endswith("arm64") else "x86_64"
            deployment = "11.0" if architecture == "arm64" else "10.15"
            configure.extend([
                f"-DCMAKE_OSX_ARCHITECTURES={architecture}",
                f"-DCMAKE_OSX_DEPLOYMENT_TARGET={deployment}",
            ])

    configure.extend(split_cmake_arguments("DOOMCRAFT_CMAKE_ARGS"))
    run(configure)


def find_executable(build: Path, pid: str) -> Path:
    if pid.startswith("windows"):
        preferred = build / "Release/lzdoom.exe"
        if preferred.is_file():
            return preferred
        names = ("lzdoom.exe", "zdoom.exe")
    elif pid.startswith("macos"):
        preferred_paths = (
            build / "lzdoom.app/Contents/MacOS/lzdoom",
            build / "Release/lzdoom.app/Contents/MacOS/lzdoom",
        )
        for preferred in preferred_paths:
            if preferred.is_file():
                return preferred
        names = ("lzdoom", "zdoom")
    else:
        preferred = build / "lzdoom"
        if preferred.is_file():
            return preferred
        names = ("lzdoom", "zdoom")

    candidates: list[Path] = []
    for name in names:
        candidates.extend(path for path in build.rglob(name) if path.is_file())
    if not candidates:
        fail(f"no LZDoom executable found below {build}")
    return max(candidates, key=lambda path: path.stat().st_size)


def clean_destination(destination: Path) -> None:
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True, exist_ok=True)


def copy_regular(source: Path, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target, follow_symlinks=True)


def copy_engine_assets(build: Path, runtime_directory: Path) -> None:
    seen: set[str] = set()
    for suffix in ("*.pk3", "*.dat"):
        for asset in sorted(build.rglob(suffix)):
            if not asset.is_file() or asset.stat().st_size <= 0 or asset.name in seen:
                continue
            copy_regular(asset, runtime_directory / asset.name)
            seen.add(asset.name)

    resource_sources = (
        build,
        SOURCE,
    )
    for relative in REQUIRED_RUNTIME_RESOURCES:
        candidates = [base / relative for base in resource_sources]
        # LZDoom's source directory is named soundfont, while the runtime is soundfonts.
        if relative == Path("soundfonts/lzdoom.sf2"):
            candidates.append(SOURCE / "soundfont/lzdoom.sf2")
        source = next((path for path in candidates if path.is_file() and path.stat().st_size > 0), None)
        if source is None:
            fail(f"missing required runtime resource: {relative}")
        copy_regular(source, runtime_directory / relative)


def otool_dependencies(path: Path) -> list[str]:
    try:
        output = run(["otool", "-L", path], capture=True)
    except subprocess.CalledProcessError:
        return []
    dependencies = []
    for line in output.splitlines()[1:]:
        stripped = line.strip()
        if not stripped:
            continue
        dependencies.append(stripped.split(" (compatibility version", 1)[0])
    return dependencies


def resolve_macos_dependency(
    dependency: str,
    owner: Path,
    executable: Path,
    search_directories: list[Path],
) -> Path | None:
    if dependency.startswith(SYSTEM_MAC_PREFIXES):
        return None
    if dependency.startswith("@loader_path/"):
        candidate = owner.parent / dependency.removeprefix("@loader_path/")
        if candidate.is_file():
            return candidate.resolve()
    elif dependency.startswith("@executable_path/"):
        candidate = executable.parent / dependency.removeprefix("@executable_path/")
        if candidate.is_file():
            return candidate.resolve()
    elif dependency.startswith("@rpath/"):
        name = Path(dependency).name
        for directory in search_directories:
            candidate = directory / name
            if candidate.is_file():
                return candidate.resolve()
    else:
        candidate = Path(dependency)
        if candidate.is_file():
            return candidate.resolve()
    return None


def bundle_macos_dependencies(executable: Path, runtime_directory: Path, zmusic_prefix: Path | None) -> None:
    search_directories = [runtime_directory]
    if zmusic_prefix is not None:
        search_directories.extend([zmusic_prefix / "lib", zmusic_prefix / "lib64"])
    for value in os.environ.get("DOOMCRAFT_LIBRARY_PATHS", "").split(os.pathsep):
        if value:
            search_directories.append(Path(value))
    search_directories.extend([Path("/opt/homebrew/lib"), Path("/usr/local/lib")])
    search_directories = [path for path in search_directories if path.is_dir()]

    copied_by_name: dict[str, Path] = {}
    queue: deque[Path] = deque([executable])
    processed: set[Path] = set()

    # Seed the package with ZMusic's own dylibs.
    if zmusic_prefix is not None:
        for library in sorted(zmusic_prefix.rglob("*.dylib")):
            if library.is_file() and library.stat().st_size > 0:
                target = runtime_directory / library.name
                copy_regular(library, target)
                copied_by_name[target.name] = target
                queue.append(target)

    while queue:
        owner = queue.popleft()
        owner = owner.resolve()
        if owner in processed:
            continue
        processed.add(owner)
        for dependency in otool_dependencies(owner):
            resolved = resolve_macos_dependency(dependency, owner, executable, search_directories)
            if resolved is None:
                continue
            target = runtime_directory / resolved.name
            if target.name not in copied_by_name:
                copy_regular(resolved, target)
                copied_by_name[target.name] = target
                queue.append(target)

    macho_files = [executable] + sorted(copied_by_name.values())
    copied_names = set(copied_by_name)
    for macho in macho_files:
        for dependency in otool_dependencies(macho):
            name = Path(dependency).name
            if name in copied_names and dependency != f"@loader_path/{name}":
                run(["install_name_tool", "-change", dependency, f"@loader_path/{name}", macho])
        if macho.suffix == ".dylib":
            run(["install_name_tool", "-id", f"@loader_path/{macho.name}", macho])

    # Changing Mach-O load commands invalidates signatures. Ad-hoc signing is enough for local loading.
    for macho in sorted(macho_files, key=lambda path: path == executable):
        run(["codesign", "--force", "--sign", "-", "--timestamp=none", macho])


def copy_runtime(executable: Path, build: Path, destination: Path, pid: str, zmusic_prefix: Path | None) -> None:
    clean_destination(destination)

    if pid.startswith("macos"):
        app_root = next((parent for parent in executable.parents if parent.suffix == ".app"), None)
        if app_root is None:
            fail(f"macOS build did not produce an app bundle: {executable}")
        packaged_app = destination / "lzdoom.app"
        shutil.copytree(app_root, packaged_app, symlinks=False)
        runtime_directory = packaged_app / "Contents/MacOS"
        packaged_executable = runtime_directory / "lzdoom"
        if not packaged_executable.is_file():
            # Preserve compatibility if the inner executable is still called zdoom.
            alternate = runtime_directory / "zdoom"
            if not alternate.is_file():
                fail("packaged macOS app has no executable")
            alternate.rename(packaged_executable)
        copy_engine_assets(build, runtime_directory)
        bundle_macos_dependencies(packaged_executable, runtime_directory, zmusic_prefix)
        packaged_executable.chmod(0o755)
    else:
        executable_name = "lzdoom.exe" if pid.startswith("windows") else "lzdoom"
        packaged_executable = destination / executable_name
        copy_regular(executable, packaged_executable)
        runtime_directory = destination
        copy_engine_assets(build, runtime_directory)

        if zmusic_prefix is not None:
            patterns = ("*.so", "*.so.*", "*.dylib", "*.dll")
            for pattern in patterns:
                for library in sorted(zmusic_prefix.rglob(pattern)):
                    if library.is_file() and library.stat().st_size > 0:
                        copy_regular(library, runtime_directory / library.name)

        # Windows builds are mostly static, but copy any generated runtime DLLs defensively.
        if pid.startswith("windows"):
            for library in sorted(executable.parent.glob("*.dll")):
                copy_regular(library, runtime_directory / library.name)
        else:
            packaged_executable.chmod(0o755)

    required_root = runtime_directory
    for relative in REQUIRED_RUNTIME_RESOURCES:
        path = required_root / relative
        if not path.is_file() or path.stat().st_size <= 0:
            fail(f"runtime package is missing {relative}")

    files_to_hash = sorted(
        path for path in destination.rglob("*")
        if path.is_file() and path.name not in {"native-manifest.txt", "native-build-id.txt"}
    )
    digest = hashlib.sha256()
    for path in files_to_hash:
        relative = path.relative_to(destination).as_posix()
        digest.update(relative.encode("utf-8"))
        digest.update(b"\0")
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    build_id = digest.hexdigest()
    (destination / "native-build-id.txt").write_text(build_id + "\n", encoding="utf-8")

    manifest_files = sorted(
        path.relative_to(destination).as_posix()
        for path in destination.rglob("*")
        if path.is_file() and path.name != "native-manifest.txt"
    )
    (destination / "native-manifest.txt").write_text(
        "\n".join(manifest_files) + "\n", encoding="utf-8"
    )
    print(f"Bundled {len(manifest_files)} files for {pid} in {destination}")


def main() -> None:
    pid = expected_platform_id()
    run([sys.executable, PREPARE])

    generator = os.environ.get("DOOMCRAFT_CMAKE_GENERATOR", default_generator(pid))
    build = PROJECT / "native/build" / pid
    destination = PROJECT / "src/main/resources/natives" / pid
    build.mkdir(parents=True, exist_ok=True)

    zmusic_prefix = ensure_zmusic(generator, pid)
    configure_lzdoom(generator, pid, build, zmusic_prefix)
    run(["cmake", "--build", build, "--config", "Release", "--parallel", "3"])

    executable = find_executable(build, pid)
    print(f"Selected executable: {executable}")
    copy_runtime(executable, build, destination, pid, zmusic_prefix)


if __name__ == "__main__":
    main()
