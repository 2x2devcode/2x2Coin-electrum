#!/usr/bin/env bash
# Cross-compile 2x2-Wallet.exe (GUI) on Linux with mingw-w64.
# Usage: bash packaging/windows/build-launcher.sh <icon.ico> <output.exe>
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ICON="${1:?icon.ico path required}"
OUT="${2:?output .exe path required}"
CC="${MINGW_CC:-x86_64-w64-mingw32-gcc}"
WINDRES="${MINGW_WINDRES:-x86_64-w64-mingw32-windres}"

command -v "${CC}" >/dev/null 2>&1 || {
  echo "mingw-w64 required: sudo apt-get install -y mingw-w64" >&2
  exit 1
}

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

cp "${ICON}" "${TMP}/2x2-Wallet.ico"
cp "${SCRIPT_DIR}/launcher.c" "${TMP}/launcher.c"
cp "${SCRIPT_DIR}/launcher.rc" "${TMP}/launcher.rc"

(
  cd "${TMP}"
  "${WINDRES}" -O coff -i launcher.rc -o launcher-res.o
  "${CC}" -O2 -municode -mwindows \
    -o "${OUT}" launcher.c launcher-res.o \
    -luser32 -lshell32
)

echo "Built ${OUT}"
