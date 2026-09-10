#!/usr/bin/env bash
# compile-windows.sh — Build a Windows portable desktop package on Ubuntu 22.04
#
# Cross-packages a runnable Windows distribution (ZIP) containing:
#   - 2x2-wallet desktop fat JAR
#   - JavaFX Windows native jars
#   - Portable Windows x64 JRE (Temurin 17)
#   - 2x2-Wallet.bat launcher
#
# Usage:  bash compile-windows.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WALLET_DIR="${SCRIPT_DIR}/2x2-wallet"
DIST_DIR="${SCRIPT_DIR}/dist/windows"
CACHE_DIR="${SCRIPT_DIR}/.cache"
LOG_DIR="${SCRIPT_DIR}/build-logs"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BUILD_LOG="${LOG_DIR}/compile-windows-${TIMESTAMP}.log"
ERROR_LOG="${LOG_DIR}/compile-windows-error-${TIMESTAMP}.log"
JAVA_FX_VERSION="21.0.2"
# Eclipse Temurin 17 Windows x64 JRE (portable .zip)
JRE_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jre_x64_windows_hotspot_17.0.13_11.zip"
JRE_ZIP_NAME="OpenJDK17U-jre_x64_windows_hotspot_17.0.13_11.zip"

info()  { printf '\033[1;34m[INFO]\033[0m  %s\n' "$*" >&2; }
ok()    { printf '\033[1;32m[OK]\033[0m    %s\n' "$*" >&2; }
warn()  { printf '\033[1;33m[WARN]\033[0m  %s\n' "$*" >&2; }
fail()  { printf '\033[1;31m[ERROR]\033[0m %s\n' "$*" >&2; exit 1; }
log()   { echo "$*" | tee -a "${BUILD_LOG}" >/dev/null; }

on_error() {
  local exit_code=$?
  mkdir -p "${LOG_DIR}"
  {
    echo "===== compile-windows.sh FAILED ====="
    echo "timestamp : $(date -Is)"
    echo "exit_code : ${exit_code}"
    echo "----- last build log (tail) -----"
    [[ -f "${BUILD_LOG}" ]] && tail -n 200 "${BUILD_LOG}" || echo "(no build log)"
  } > "${ERROR_LOG}" 2>&1 || true
  printf '\033[1;31m[ERROR]\033[0m Compilation failed. Error log:\n  %s\n' "${ERROR_LOG}" >&2
  exit "${exit_code}"
}
trap on_error ERR

mkdir -p "${LOG_DIR}" "${DIST_DIR}" "${CACHE_DIR}"
require_ubuntu_2204() {
  if [[ -f /etc/os-release ]]; then
    # shellcheck source=/dev/null
    . /etc/os-release
    if [[ "${ID:-}" == "ubuntu" && "${VERSION_ID:-}" == "22.04" ]]; then
      ok "Ubuntu 22.04 detected."
    else
      warn "Intended for Ubuntu 22.04 (detected: ${ID:-unknown} ${VERSION_ID:-}). Continuing."
    fi
  fi
}

sudo_if_needed() {
  if [[ "$(id -u)" -eq 0 ]]; then "$@"
  else sudo "$@"
  fi
}

ensure_tools() {
  if ! command -v java >/dev/null 2>&1; then
    info "Installing OpenJDK 17..."
    sudo_if_needed apt-get update -y
    sudo_if_needed apt-get install -y openjdk-17-jdk wget unzip zip
  else
    sudo_if_needed apt-get install -y wget unzip zip >/dev/null 2>&1 || true
  fi
  java -version
}

build_jar() {
  info "Building desktop JAR..."
  chmod +x "${WALLET_DIR}/gradlew"
  (cd "${WALLET_DIR}" && ./gradlew --no-daemon :x2x-core:test :x2x-desktop:desktopJar)
  JAR="$(find "${WALLET_DIR}/x2x-desktop/build/libs" -name '2x2-wallet-desktop*.jar' | head -n1)"
  [[ -n "${JAR}" && -f "${JAR}" ]] || fail "desktop JAR not found"
  ok "JAR: ${JAR}"
}

download_javafx_win() {
  local cache="${CACHE_DIR}/javafx"
  mkdir -p "${cache}"
  local mods=(base controls graphics)
  for m in "${mods[@]}"; do
    local jar="${cache}/javafx-${m}-${JAVA_FX_VERSION}-win.jar"
    if [[ ! -f "${jar}" ]]; then
      info "Downloading JavaFX ${m} (win)..."
      wget -q -O "${jar}" \
        "https://repo1.maven.org/maven2/org/openjfx/javafx-${m}/${JAVA_FX_VERSION}/javafx-${m}-${JAVA_FX_VERSION}-win.jar"
    fi
  done
}

download_windows_jre() {
  local zip_path="${CACHE_DIR}/${JRE_ZIP_NAME}"
  local extract_dir="${CACHE_DIR}/windows-jre"
  if [[ ! -d "${extract_dir}/bin" ]]; then
    if [[ ! -f "${zip_path}" ]]; then
      info "Downloading portable Windows JRE (Temurin 17)..."
      wget -q -O "${zip_path}" "${JRE_URL}" || fail "Failed to download Windows JRE"
    fi
    rm -rf "${extract_dir}"
    mkdir -p "${extract_dir}"
    unzip -q "${zip_path}" -d "${CACHE_DIR}/windows-jre-unpack"
    local inner
    inner="$(find "${CACHE_DIR}/windows-jre-unpack" -maxdepth 1 -type d -name 'jdk-*-jre' | head -n1)"
    [[ -n "${inner}" ]] || fail "Unexpected Windows JRE zip layout"
    mv "${inner}"/* "${extract_dir}/"
    rm -rf "${CACHE_DIR}/windows-jre-unpack"
  fi
  ok "Windows JRE ready: ${extract_dir}"
}

package_windows() {
  local jar="$1"
  local fx_dir="$2"
  local jre_dir="$3"
  local out="${DIST_DIR}/2x2-Wallet-windows"
  rm -rf "${out}"
  mkdir -p "${out}/lib" "${out}/javafx" "${out}/jre"
  cp "${jar}" "${out}/lib/2x2-wallet-desktop.jar"
  cp "${fx_dir}/javafx-"*"-win.jar" "${out}/javafx/"
  cp -a "${jre_dir}/." "${out}/jre/"

  cat > "${out}/2x2-Wallet.bat" << 'EOF'
@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp02x2-Wallet.ps1" %*
endlocal
EOF

  # Also provide a PowerShell launcher that joins module-path more reliably.
  cat > "${out}/2x2-Wallet.ps1" << 'EOF'
$ErrorActionPreference = "Stop"
$Dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Java = Join-Path $Dir "jre\bin\java.exe"
$FxDir = Join-Path $Dir "javafx"
$Jars = (Get-ChildItem -Path $FxDir -Filter "*.jar" | ForEach-Object { $_.FullName }) -join ";"
$AppJar = Join-Path $Dir "lib\2x2-wallet-desktop.jar"
& $Java --module-path $Jars --add-modules javafx.controls,javafx.graphics -jar $AppJar @args
EOF

  cat > "${out}/README.txt" << 'EOF'
2X2 Wallet — Windows portable build
1. Unzip this folder anywhere.
2. Double-click 2x2-Wallet.bat (or run 2x2-Wallet.ps1 in PowerShell).
A private Windows JRE is bundled — no system Java install is required.
EOF

  (cd "${DIST_DIR}" && zip -qr "2x2-wallet-desktop-windows.zip" "2x2-Wallet-windows")
  ok "Windows zip: ${DIST_DIR}/2x2-wallet-desktop-windows.zip"
}

main() {
  require_ubuntu_2204
  ensure_tools
  build_jar
  JAR="$(find "${WALLET_DIR}/x2x-desktop/build/libs" -name '2x2-wallet-desktop*.jar' | head -n1)"
  [[ -n "${JAR}" && -f "${JAR}" ]] || fail "desktop JAR not found after build"
  download_javafx_win
  FX_DIR="${CACHE_DIR}/javafx"
  download_windows_jre
  JRE_DIR="${CACHE_DIR}/windows-jre"
  package_windows "${JAR}" "${FX_DIR}" "${JRE_DIR}"
  ok "Windows desktop build complete."
}

main "$@"
