#!/usr/bin/env bash
# compile-windows.sh — Build Windows desktop package + Setup.exe on Ubuntu 22.04
#
# Produces:
#   dist/windows/2x2-Wallet-windows/           (portable folder)
#   dist/windows/2x2-wallet-desktop-windows.zip
#   dist/windows/2x2-Wallet-Setup.exe          (optional NSIS installer)
#
# Portable use (no install):
#   1. Unzip the zip
#   2. Prefer 2x2-Wallet.cmd  (most antivirus-friendly)
#      or 2x2-Wallet.exe       (NSIS stub launcher)
#   If launch fails: run 2x2-Wallet-Debug.cmd and open 2x2-Wallet-error.log
#
# Bundles BellSoft Liberica JRE 17 Full (JavaFX included) — avoids separate
# OpenJFX jars that often trigger "A JNI error has occurred" on Windows.
#
# Usage:  bash compile-windows.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WALLET_DIR="${SCRIPT_DIR}/2x2-wallet"
DIST_DIR="${SCRIPT_DIR}/dist/windows"
CACHE_DIR="${SCRIPT_DIR}/.cache"
LOG_DIR="${SCRIPT_DIR}/build-logs"
PACKAGING_DIR="${SCRIPT_DIR}/packaging/windows"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BUILD_LOG="${LOG_DIR}/compile-windows-${TIMESTAMP}.log"
ERROR_LOG="${LOG_DIR}/compile-windows-error-${TIMESTAMP}.log"
APP_VERSION="1.3.1"

# Liberica JRE Full = Temurin-class runtime + LibericaFX (JavaFX) in one tree.
JRE_URL="https://download.bell-sw.com/java/17.0.13+12/bellsoft-jre17.0.13+12-windows-amd64-full.zip"
JRE_ZIP_NAME="bellsoft-jre17.0.13+12-windows-amd64-full.zip"

info()  { printf '\033[1;34m[INFO]\033[0m  %s\n' "$*" >&2; }
ok()    { printf '\033[1;32m[OK]\033[0m    %s\n' "$*" >&2; }
warn()  { printf '\033[1;33m[WARN]\033[0m  %s\n' "$*" >&2; }
fail()  { printf '\033[1;31m[ERROR]\033[0m %s\n' "$*" >&2; exit 1; }

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
  info "Ensuring build tools (JDK, wget, unzip, zip, nsis)..."
  sudo_if_needed apt-get update -y
  sudo_if_needed apt-get install -y openjdk-17-jdk wget unzip zip nsis python3 python3-pil \
    || sudo_if_needed apt-get install -y openjdk-17-jdk wget unzip zip nsis python3
  command -v makensis >/dev/null 2>&1 || fail "makensis (NSIS) is required"
  java -version
  makensis -VERSION || true
}

build_jar() {
  info "Building desktop JAR..."
  chmod +x "${WALLET_DIR}/gradlew"
  (cd "${WALLET_DIR}" && ./gradlew --no-daemon :x2x-core:test :x2x-desktop:desktopJar)
  JAR="$(find "${WALLET_DIR}/x2x-desktop/build/libs" -name '2x2-wallet-desktop*.jar' | head -n1)"
  [[ -n "${JAR}" && -f "${JAR}" ]] || fail "desktop JAR not found"
  ok "JAR: ${JAR}"
}

download_windows_jre() {
  local zip_path="${CACHE_DIR}/${JRE_ZIP_NAME}"
  local extract_dir="${CACHE_DIR}/windows-jre-full"
  if [[ ! -f "${extract_dir}/bin/javaw.exe" ]] || ! grep -q 'javafx.controls' "${extract_dir}/release" 2>/dev/null; then
    if [[ ! -f "${zip_path}" ]]; then
      info "Downloading Liberica JRE 17 Full (includes JavaFX)..."
      wget -q -O "${zip_path}" "${JRE_URL}" || fail "Failed to download Liberica JRE Full"
    fi
    rm -rf "${extract_dir}" "${CACHE_DIR}/liberica-full-unpack"
    mkdir -p "${CACHE_DIR}/liberica-full-unpack"
    unzip -q "${zip_path}" -d "${CACHE_DIR}/liberica-full-unpack"
    local inner
    inner="$(find "${CACHE_DIR}/liberica-full-unpack" -maxdepth 1 -type d \( -name 'jre-*-full' -o -name 'jdk-*-full' \) | head -n1)"
    [[ -n "${inner}" ]] || fail "Unexpected Liberica zip layout"
    mkdir -p "${extract_dir}"
    mv "${inner}"/* "${extract_dir}/"
    rm -rf "${CACHE_DIR}/liberica-full-unpack"
  fi
  grep -q 'javafx.controls' "${extract_dir}/release" \
    || fail "Bundled JRE is not Liberica Full (javafx.controls missing from release MODULES)"
  ok "Windows Liberica Full JRE ready: ${extract_dir}"
}

make_icon() {
  local png="${WALLET_DIR}/x2x-android/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
  local ico="${DIST_DIR}/2x2-Wallet.ico"
  [[ -f "${png}" ]] || fail "Icon PNG not found: ${png}"
  info "Generating Windows .ico..."
  if ! python3 -c "from PIL import Image" >/dev/null 2>&1; then
    info "Installing Pillow for icon conversion..."
    pip3 install --user Pillow >/dev/null 2>&1 || fail "Pillow is required to create the installer icon"
  fi
  python3 - << PY
from PIL import Image
src = Image.open("${png}").convert("RGBA")
sizes = [(16,16),(32,32),(48,48),(64,64),(128,128),(256,256)]
imgs = [src.resize(s, Image.Resampling.LANCZOS) for s in sizes]
imgs[0].save("${ico}", format="ICO", sizes=[(im.width, im.height) for im in imgs], append_images=imgs[1:])
print("wrote ${ico}")
PY
  [[ -f "${ico}" ]] || fail "Failed to create ${ico}"
  ok "Icon: ${ico}"
}

build_nsis_launcher() {
  local out_exe="$1"
  info "Building NSIS portable launcher (2x2-Wallet.exe)..."
  [[ -f "${PACKAGING_DIR}/2x2-Wallet-launcher.nsi" ]] || fail "Missing launcher.nsi"
  cp "${PACKAGING_DIR}/2x2-Wallet-launcher.nsi" "${DIST_DIR}/2x2-Wallet-launcher.nsi"
  local tmp_exe="${DIST_DIR}/2x2-Wallet-launcher-built.exe"
  rm -f "${tmp_exe}" "${DIST_DIR}/2x2-Wallet.exe"
  (
    cd "${DIST_DIR}"
    makensis \
      -DAPP_VERSION="${APP_VERSION}" \
      -DOUT_FILE="2x2-Wallet-launcher-built.exe" \
      -DICON_FILE="2x2-Wallet.ico" \
      "2x2-Wallet-launcher.nsi"
  )
  [[ -f "${tmp_exe}" ]] || fail "NSIS launcher was not produced"
  mkdir -p "$(dirname "${out_exe}")"
  mv -f "${tmp_exe}" "${out_exe}"
  ok "Launcher: ${out_exe} ($(du -h "${out_exe}" | awk '{print $1}'))"
}

package_windows() {
  local jar="$1"
  local jre_dir="$2"
  local out="${DIST_DIR}/2x2-Wallet-windows"
  rm -rf "${out}"
  mkdir -p "${out}/lib" "${out}/jre"
  cp "${jar}" "${out}/lib/2x2-wallet-desktop.jar"
  cp -a "${jre_dir}/." "${out}/jre/"
  cp "${DIST_DIR}/2x2-Wallet.ico" "${out}/2x2-Wallet.ico"

  # No separate javafx\ folder — Liberica Full already has JavaFX + natives in jre\bin.
  build_nsis_launcher "${out}/2x2-Wallet.exe"

  # Primary antivirus-friendly entry (no custom PE).
  cat > "${out}/2x2-Wallet.cmd" << 'EOF'
@echo off
setlocal
cd /d "%~dp0"
if not exist "jre\bin\javaw.exe" (
  echo Missing jre\bin\javaw.exe — unzip the full portable folder.
  pause
  exit /b 1
)
if not exist "lib\2x2-wallet-desktop.jar" (
  echo Missing lib\2x2-wallet-desktop.jar
  pause
  exit /b 1
)
start "" "jre\bin\javaw.exe" --add-modules javafx.controls,javafx.graphics -jar "lib\2x2-wallet-desktop.jar" %*
endlocal
EOF

  # Debug launcher: console + error log (use when you see the JNI dialog).
  cat > "${out}/2x2-Wallet-Debug.cmd" << 'EOF'
@echo off
setlocal
cd /d "%~dp0"
set LOG=%~dp02x2-Wallet-error.log
echo ===== 2X2 Wallet debug launch ===== > "%LOG%"
echo time: %DATE% %TIME%>> "%LOG%"
echo cwd: %CD%>> "%LOG%"
echo.>> "%LOG%"
if exist "jre\bin\java.exe" (
  "jre\bin\java.exe" -version >> "%LOG%" 2>&1
) else (
  echo MISSING jre\bin\java.exe >> "%LOG%"
)
echo.>> "%LOG%"
echo ----- application output ----- >> "%LOG%"
"jre\bin\java.exe" --add-modules javafx.controls,javafx.graphics -jar "lib\2x2-wallet-desktop.jar" %* >> "%LOG%" 2>&1
set EC=%ERRORLEVEL%
echo.>> "%LOG%"
echo Exit code: %EC%>> "%LOG%"
echo.
echo Wrote log: %LOG%
echo Exit code: %EC%
echo.
type "%LOG%"
echo.
pause
endlocal
EOF

  # Backward-compatible names
  cp "${out}/2x2-Wallet.cmd" "${out}/2x2-Wallet.bat"
  cp "${out}/2x2-Wallet-Debug.cmd" "${out}/2x2-Wallet-Console.bat"

  cat > "${out}/README.txt" << 'EOF'
2X2 Wallet — Windows portable (no installation)

HOW TO RUN
  1. Unzip this whole folder anywhere (Desktop, USB, Documents…)
  2. Double-click:  2x2-Wallet.cmd     ← preferred (least antivirus false positives)
     or:            2x2-Wallet.exe     ← NSIS stub (same JVM launch)

Do NOT move the .exe/.cmd alone. Keep jre\ and lib\ beside them.

This build uses BellSoft Liberica JRE 17 Full with JavaFX built in
(no separate javafx\ folder). System Java is NOT required.

IF YOU SEE "A JNI error has occurred"
  1. Run 2x2-Wallet-Debug.cmd
  2. Open 2x2-Wallet-error.log in this folder
  3. Send that log for support

KASPERSKY / ANTIVIRUS
  Unsigned launchers are often quarantined by mistake.
  Add this whole folder to trusted / exclusions, then restore
  any quarantined file, or just use 2x2-Wallet.cmd.
EOF

  (cd "${DIST_DIR}" && zip -qr "2x2-wallet-desktop-windows.zip" "2x2-Wallet-windows")
  ok "Windows portable zip: ${DIST_DIR}/2x2-wallet-desktop-windows.zip"
}

build_setup_exe() {
  info "Building Setup.exe with NSIS..."
  [[ -f "${PACKAGING_DIR}/2x2-Wallet.nsi" ]] || fail "Missing ${PACKAGING_DIR}/2x2-Wallet.nsi"
  [[ -d "${DIST_DIR}/2x2-Wallet-windows" ]] || fail "Payload folder missing"
  [[ -f "${DIST_DIR}/2x2-Wallet.ico" ]] || fail "Icon missing"

  cp "${PACKAGING_DIR}/2x2-Wallet.nsi" "${DIST_DIR}/2x2-Wallet.nsi"
  (
    cd "${DIST_DIR}"
    makensis \
      -DAPP_VERSION="${APP_VERSION}" \
      -DPAYLOAD_DIR="2x2-Wallet-windows" \
      -DOUT_FILE="2x2-Wallet-Setup.exe" \
      -DICON_FILE="2x2-Wallet.ico" \
      "2x2-Wallet.nsi"
  )
  [[ -f "${DIST_DIR}/2x2-Wallet-Setup.exe" ]] || fail "Setup.exe was not produced"
  ok "Installer: ${DIST_DIR}/2x2-Wallet-Setup.exe"
  ls -lh "${DIST_DIR}/2x2-Wallet-Setup.exe"
}

main() {
  require_ubuntu_2204
  ensure_tools
  build_jar
  JAR="$(find "${WALLET_DIR}/x2x-desktop/build/libs" -name '2x2-wallet-desktop*.jar' | head -n1)"
  [[ -n "${JAR}" && -f "${JAR}" ]] || fail "desktop JAR not found after build"
  download_windows_jre
  JRE_DIR="${CACHE_DIR}/windows-jre-full"
  make_icon
  package_windows "${JAR}" "${JRE_DIR}"
  build_setup_exe
  ok "Windows desktop build complete."
  info "Artifacts (portable — no install):"
  info "  ${DIST_DIR}/2x2-Wallet-windows/2x2-Wallet.cmd"
  info "  ${DIST_DIR}/2x2-Wallet-windows/2x2-Wallet.exe"
  info "  ${DIST_DIR}/2x2-wallet-desktop-windows.zip"
  info "Optional installer:"
  info "  ${DIST_DIR}/2x2-Wallet-Setup.exe"
}

main "$@"
