#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${1:-$PWD}"
cd "$PROJECT_DIR"

TARGET="native/tools/build_lzdoom.py"

if [[ ! -f "$TARGET" ]]; then
    echo "ERRO: arquivo não encontrado: $TARGET" >&2
    exit 1
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="${TARGET}.bak-${STAMP}"
cp -a "$TARGET" "$BACKUP"

python3 - <<'PY'
from pathlib import Path

path = Path("native/tools/build_lzdoom.py")
text = path.read_text(encoding="utf-8")

marker = "    # Runtime libraries, especially ZMusic.\n"

resource_block = '''    # LZDoom requires its default SoundFont and FM banks at runtime.
    # CMake generates these beside the executable, but they must also be
    # preserved inside the mod JAR using their relative directories.
    resource_sources = (
        (executable.parent / "soundfonts", "soundfonts"),
        (build / "soundfonts", "soundfonts"),
        (SOURCE / "soundfont", "soundfonts"),
        (executable.parent / "fm_banks", "fm_banks"),
        (build / "fm_banks", "fm_banks"),
        (SOURCE / "fm_banks", "fm_banks"),
    )

    copied_resources: set[str] = set()

    for source_directory, destination_directory in resource_sources:
        if not source_directory.is_dir():
            continue

        for resource in sorted(source_directory.rglob("*")):
            if not resource.is_file() or resource.stat().st_size <= 0:
                continue

            relative = (
                Path(destination_directory)
                / resource.relative_to(source_directory)
            ).as_posix()

            if relative in copied_resources:
                continue

            copy_file(resource, relative)
            copied_resources.add(relative)

    required_runtime_resources = (
        destination / "soundfonts/lzdoom.sf2",
        destination / "fm_banks/GENMIDI.GS.wopl",
        destination / "fm_banks/gs-by-papiezak-and-sneakernets.wopn",
    )

    missing_runtime_resources = [
        str(resource)
        for resource in required_runtime_resources
        if not resource.is_file() or resource.stat().st_size <= 0
    ]

    if missing_runtime_resources:
        raise SystemExit(
            "Missing required LZDoom runtime resource(s): "
            + ", ".join(missing_runtime_resources)
        )

'''

if resource_block not in text:
    if marker not in text:
        raise SystemExit(
            "ERRO: ponto de inserção não encontrado em build_lzdoom.py."
        )
    text = text.replace(marker, resource_block + marker, 1)

path.write_text(text, encoding="utf-8")

updated = path.read_text(encoding="utf-8")
required_tokens = (
    'destination / "soundfonts/lzdoom.sf2"',
    'destination / "fm_banks/GENMIDI.GS.wopl"',
    'destination / "fm_banks/gs-by-papiezak-and-sneakernets.wopn"',
    'copy_file(resource, relative)',
)

missing = [token for token in required_tokens if token not in updated]
if missing:
    raise SystemExit(
        "Patch incompleto; trechos ausentes:\n- " + "\n- ".join(missing)
    )

print(f"Empacotador corrigido: {path}")
PY

python3 -m py_compile "$TARGET"

echo "Backup criado:"
echo "  $BACKUP"

echo
echo "Trechos inseridos:"
grep -n -A55 -B4 \
  'LZDoom requires its default SoundFont' \
  "$TARGET"

echo
echo "Removendo somente o pacote nativo anteriormente gerado..."
rm -rf src/main/resources/natives/linux-x86_64

echo
echo "Patch concluído."
echo
echo "Reempacote os recursos nativos com:"
echo "  ./gradlew buildNative \\"
echo "    --rerun-tasks \\"
echo "    --no-configuration-cache \\"
echo "    --stacktrace"
