#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${2:-/mnt/2TB/Repository/DoomCraft}"
BASE_JAR="${1:-}"

if [[ -z "$BASE_JAR" ]]; then
  echo "Uso:"
  echo "  $0 /caminho/doomcraft-1.0.0-multiplatform.jar [diretorio-do-projeto]"
  exit 2
fi

if [[ ! -f "$BASE_JAR" ]]; then
  echo "JAR base não encontrado: $BASE_JAR" >&2
  exit 3
fi

if [[ ! -d "$PROJECT_DIR" ]]; then
  echo "Projeto não encontrado: $PROJECT_DIR" >&2
  exit 4
fi

for required in \
  "natives/linux-x86_64/native-build-id.txt" \
  "natives/windows-x86_64/native-build-id.txt" \
  "natives/macos-x86_64/native-build-id.txt" \
  "natives/macos-arm64/native-build-id.txt"
do
  if ! unzip -Z1 "$BASE_JAR" | grep -Fxq "$required"; then
    echo "O JAR base não é o pacote multiplataforma completo." >&2
    echo "Arquivo ausente: $required" >&2
    exit 5
  fi
done

TMP_DIR="$(mktemp -d -t doomcraft-local-natives-XXXXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "Extraindo pacotes nativos do JAR base..."
(
  cd "$TMP_DIR"
  unzip -q "$BASE_JAR" 'natives/*'
)

TARGET="$PROJECT_DIR/src/main/resources/natives"

echo "Substituindo recursos nativos locais em:"
echo "  $TARGET"
rm -rf "$TARGET"
mkdir -p "$(dirname "$TARGET")"
cp -a "$TMP_DIR/natives" "$TARGET"

echo "Compilando JAR local completo..."
cd "$PROJECT_DIR"

./gradlew clean build \
  -x verifyDoomCraftSource \
  --no-configuration-cache \
  --stacktrace

LOCAL_JAR="$PROJECT_DIR/build/libs/doomcraft-1.0.0.jar"

if [[ ! -f "$LOCAL_JAR" ]]; then
  echo "Build terminou, mas o JAR esperado não foi encontrado:" >&2
  echo "  $LOCAL_JAR" >&2
  exit 6
fi

echo "Validando recursos nativos no JAR resultante..."
for required in \
  "natives/linux-x86_64/native-build-id.txt" \
  "natives/windows-x86_64/native-build-id.txt" \
  "natives/macos-x86_64/native-build-id.txt" \
  "natives/macos-arm64/native-build-id.txt"
do
  if ! unzip -Z1 "$LOCAL_JAR" | grep -Fxq "$required"; then
    echo "Validação falhou. Recurso ausente no JAR final: $required" >&2
    exit 7
  fi
done

echo
echo "JAR local completo gerado com sucesso:"
echo "  $LOCAL_JAR"
sha256sum "$LOCAL_JAR"
