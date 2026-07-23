#!/usr/bin/env python3
"""Offline structural verification for DoomCraft source distributions."""
from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []
NOTES: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


def require_text(path: Path, *fragments: str) -> None:
    require(path.is_file(), f"Arquivo ausente: {path.relative_to(ROOT)}")
    if not path.is_file():
        return
    text = path.read_text(encoding="utf-8")
    for fragment in fragments:
        require(fragment in text, f"Trecho ausente em {path.relative_to(ROOT)}: {fragment!r}")


def validate_json() -> int:
    count = 0
    for path in sorted(ROOT.rglob("*.json")):
        if any(part in {"native", "build", ".gradle"} for part in path.parts):
            continue
        try:
            json.loads(path.read_text(encoding="utf-8"))
            count += 1
        except Exception as exc:  # noqa: BLE001 - report all malformed resource files
            ERRORS.append(f"JSON inválido em {path.relative_to(ROOT)}: {exc}")
    return count


def validate_versions() -> None:
    properties = {}
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()

    expected = {
        "minecraft_version": "26.2",
        "loader_version": "0.19.3",
        "loom_version": "1.17.16",
        "fabric_version": "0.155.2+26.2",
        "mod_version": "1.0.0",
    }
    for key, value in expected.items():
        require(properties.get(key) == value, f"{key} deve ser {value}, encontrado {properties.get(key)!r}")

    require(not any(token in v.lower() for v in properties.values() for token in ("snapshot", "alpha", "beta", "-rc")),
            "Uma versão não estável foi encontrada em gradle.properties")


def validate_recipe_and_variants() -> None:
    recipe = json.loads((ROOT / "src/main/resources/data/doomcraft/recipe/television.json").read_text())
    require(recipe == {"type": "doomcraft:television"}, "A receita deve usar exclusivamente o serializer doomcraft:television")

    variant_names = {
        "television", "spruce_television", "birch_television", "jungle_television",
        "acacia_television", "dark_oak_television", "mangrove_television",
        "cherry_television", "bamboo_television", "crimson_television",
        "warped_television", "pale_oak_television",
    }
    for name in variant_names:
        for relative in (
            f"src/main/resources/assets/doomcraft/blockstates/{name}.json",
            f"src/main/resources/assets/doomcraft/models/block/{name}.json",
            f"src/main/resources/assets/doomcraft/models/item/{name}.json",
            f"src/main/resources/assets/doomcraft/items/{name}.json",
            f"src/main/resources/data/doomcraft/loot_table/blocks/{name}.json",
        ):
            require((ROOT / relative).is_file(), f"Recurso de variante ausente: {relative}")


def validate_no_wads() -> None:
    found = [p.relative_to(ROOT) for p in ROOT.rglob("*") if p.is_file() and p.suffix.lower() in {".wad", ".iwad", ".pwad"}]
    require(not found, f"A distribuição não pode incluir WADs: {found}")


def validate_native_archive() -> None:
    archive = ROOT / "native/vendor/lzdoom-l4.14.4.zip"
    require(archive.is_file(), "Arquivo fonte LZDoom 4.14.4 ausente")
    if not archive.is_file():
        return
    with zipfile.ZipFile(archive) as zf:
        names = zf.namelist()
        roots = {Path(name).parts[0] for name in names if Path(name).parts}
        require(roots == {"lzdoom-l4.14.4"}, f"Raiz inesperada do ZIP LZDoom: {roots}")
        require("lzdoom-l4.14.4/LICENSE" in names, "Licença do LZDoom ausente no arquivo original")
        require("lzdoom-l4.14.4/src/d_main.cpp" in names, "Ponto de patch d_main.cpp ausente no LZDoom")
        require("lzdoom-l4.14.4/src/CMakeLists.txt" in names, "Ponto de patch CMakeLists.txt ausente no LZDoom")
        for info in zf.infolist():
            parts = Path(info.filename).parts
            require(".." not in parts and not Path(info.filename).is_absolute(),
                    f"Entrada insegura no ZIP LZDoom: {info.filename}")
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    NOTES.append(f"LZDoom SHA-256: {digest}")


def validate_glb() -> None:
    path = ROOT / "reference/minedoom_lowpoly_crt_tv.glb"
    require(path.is_file(), "GLB de referência ausente")
    if not path.is_file():
        return
    with path.open("rb") as stream:
        header = stream.read(12)
    require(len(header) == 12, "GLB truncado")
    if len(header) == 12:
        magic, version, length = struct.unpack("<4sII", header)
        require(magic == b"glTF", f"Assinatura GLB inválida: {magic!r}")
        require(version == 2, f"Versão GLB não suportada: {version}")
        require(length == path.stat().st_size, f"Tamanho GLB declarado {length} difere do arquivo {path.stat().st_size}")


def validate_source_contracts() -> None:
    require_text(ROOT / "src/main/java/br/com/chromus/doomcraft/recipe/DoomTelevisionRecipe.java",
                 "Items.IRON_INGOT", "Items.GLASS_PANE", "Items.REDSTONE", "ItemTags.PLANKS",
                 "first == second && second == third", "VanillaWoodVariant.isRecognized(first)")
    require_text(ROOT / "src/main/java/br/com/chromus/doomcraft/block/DoomTelevisionBlockEntity.java",
                 "distanceSquared <= 16.0", "distanceSquared <= 64.0", "TelevisionMode.OFF")
    require_text(ROOT / "src/client/java/br/com/chromus/doomcraft/client/DoomClientRuntime.java",
                 "20 * 30", "pauseAndSave()", "STOP_DELAY_TICKS", "DoomCraftPaths.WADS")
    require_text(ROOT / "src/client/java/br/com/chromus/doomcraft/client/nativebridge/DoomSession.java",
                 "NATIVE_RSS_LIMIT_BYTES", "LINUX_ADDRESS_SPACE_LIMIT_BYTES", "-nosound", "-nojoy",
                 "+vid_preferbackend", "SDL_VIDEODRIVER")
    require_text(ROOT / "native/bridge/doomcraft_bridge.cpp",
                 "OUTPUT_WIDTH = 320", "OUTPUT_HEIGHT = 200", "SAVE", "LOAD", "PAUSE", "RESUME", "QUIT")
    require_text(ROOT / "native/tools/prepare_lzdoom.py",
                 "doomcraft_bridge.cpp", "DoomCraftBridge::OnFrame(screen)",
                 "destination.is_relative_to(extraction_root)")


def validate_packaging_hygiene() -> None:
    forbidden_roots = [ROOT / "build", ROOT / ".gradle", ROOT / "run"]
    for path in forbidden_roots:
        require(not path.exists(), f"Diretório de build não deve estar na distribuição: {path.relative_to(ROOT)}")
    require(not (ROOT / "native/build").exists(), "native/build não deve estar na distribuição")
    require(not (ROOT / "native/deps").exists(), "native/deps não deve estar na distribuição")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validação estrutural offline do código-fonte DoomCraft."
    )
    parser.add_argument(
        "--distribution",
        action="store_true",
        help=(
            "Também valida a higiene de uma distribuição limpa. "
            "Este modo rejeita build/, .gradle/, run/ e diretórios nativos gerados."
        ),
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    validate_versions()
    json_count = validate_json()
    validate_recipe_and_variants()
    validate_no_wads()
    validate_native_archive()
    validate_glb()
    validate_source_contracts()

    # build/ e .gradle/ são normais em uma árvore de trabalho Gradle.
    # A checagem de empacotamento só deve ser executada explicitamente
    # sobre uma cópia/extração limpa destinada à distribuição.
    if args.distribution:
        validate_packaging_hygiene()

    print(f"JSONs validados: {json_count}")
    for note in NOTES:
        print(note)
    if ERRORS:
        print("\nFalhas de verificação:", file=sys.stderr)
        for error in ERRORS:
            print(f"- {error}", file=sys.stderr)
        return 1

    if args.distribution:
        print("DoomCraft: distribuição limpa validada sem falhas.")
    else:
        print("DoomCraft: código-fonte validado sem falhas.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
