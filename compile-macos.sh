#!/usr/bin/env bash
# compile-macos.sh — Build native macOS desktop package for 2X2 Wallet
#
# Run this script ON macOS (Apple Silicon or Intel). jpackage requires macOS
# to produce .app / .dmg artifacts.
#
# Produces:
#   dist/macos/2x2-Wallet.app          (when jpackage succeeds)
#   dist/macos/2x2-wallet-desktop-macos.zip  (portable launcher + jars)
#
# Usage:  bash compile-macos.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WALLET_DIR="${SCRIPT_DIR}/2x2-wallet"
DIST_DIR="${SCRIPT_DIR}/dist/macos"
CACHE_DIR="${SCRIPT_DIR}/.cache"
LOG_DIR="${SCRIPT_DIR}/build-logs"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BUILD_LOG="${LOG_DIR}/compile-macos-${TIMESTAMP}.log"
ERROR_LOG="${LOG_DIR}/compile-macos-error-${TIMESTAMP}.log"
JAVA_FX_VERSION="21.0.2"

info()  { printf '\033[1;34m[INFO]\033[0m  %s\n' "$*" >&2; }
ok()    { printf '\033[1;32m[OK]\033[0m    %s\n' "$*" >&2; }
warn()  { printf '\033[1;33m[WARN]\033[0m  %s\n' "$*" >&2; }
fail()  { printf '\033[1;31m[ERROR]\033[0m %s\n' "$*" >&2; exit 1; }
log()   { echo "$*" | tee -a "${BUILD_LOG}" >/dev/null; }

on_error() {
  local exit_code=$?
  mkdir -p "${LOG_DIR}"
  {
    echo "===== compile-macos.sh FAILED ====="
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
require_macos() {
  if [[ "$(uname -s)" != "Darwin" ]]; then
    fail "compile-macos.sh must be run on macOS (detected: $(uname -s))."
  fi
  ok "macOS detected ($(uname -m))."
}

ensure_jdk() {
  if ! command -v java >/dev/null 2>&1; then
    fail "Java 17+ is required. Install Temurin/OpenJDK 17+ and retry."
  fi
  local major
  major="$(java -version 2>&1 | awk -F[\".] '/version/ {print $2}')"
  if [[ -z "${major}" || "${major}" -lt 17 ]]; then
    fail "Java 17+ required (found java major=${major:-unknown})."
  fi
  if ! command -v jpackage >/dev/null 2>&1; then
    warn "jpackage not found — portable zip will still be produced."
  fi
  java -version
}

arch_classifier() {
  case "$(uname -m)" in
    arm64|aarch64) echo "mac-aarch64" ;;
    x86_64|amd64) echo "mac" ;;
    *) fail "Unsupported macOS architecture: $(uname -m)" ;;
  esac
}

build_jar() {
  info "Building desktop JAR..."
  chmod +x "${WALLET_DIR}/gradlew"
  (cd "${WALLET_DIR}" && ./gradlew --no-daemon :x2x-core:test :x2x-desktop:desktopJar)
  JAR="$(find "${WALLET_DIR}/x2x-desktop/build/libs" -name '2x2-wallet-desktop*.jar' | head -n1)"
  [[ -n "${JAR}" && -f "${JAR}" ]] || fail "desktop JAR not found"
  ok "JAR: ${JAR}"
}

download_javafx_mac() {
  local classifier="$1"
  local cache="${CACHE_DIR}/javafx"
  mkdir -p "${cache}"
  local mods=(base controls graphics)
  for m in "${mods[@]}"; do
    local jar="${cache}/javafx-${m}-${JAVA_FX_VERSION}-${classifier}.jar"
    if [[ ! -f "${jar}" ]]; then
      info "Downloading JavaFX ${m} (${classifier})..."
      curl -fsSL -o "${jar}" \
        "https://repo1.maven.org/maven2/org/openjfx/javafx-${m}/${JAVA_FX_VERSION}/javafx-${m}-${JAVA_FX_VERSION}-${classifier}.jar"
    fi
  done
}

package_portable() {
  local jar="$1"
  local fx_dir="$2"
  local classifier="$3"
  local out="${DIST_DIR}/2x2-Wallet-macos"
  rm -rf "${out}"
  mkdir -p "${out}/lib" "${out}/javafx"
  cp "${jar}" "${out}/lib/2x2-wallet-desktop.jar"
  cp "${fx_dir}/javafx-"*"-${classifier}.jar" "${out}/javafx/"
  cat > "${out}/2x2-Wallet.sh" << 'EOF'
#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! command -v "${JAVA_BIN}" >/dev/null 2>&1 && ! command -v java >/dev/null 2>&1; then
  echo "Java 17+ is required to run 2X2 Wallet." >&2
  exit 1
fi
[[ -x "${JAVA_BIN}" ]] || JAVA_BIN=java
FX="$(echo "${DIR}/javafx"/*.jar | tr ' ' ':')"
exec "${JAVA_BIN}" --module-path "${FX}" --add-modules javafx.controls,javafx.graphics \
  -jar "${DIR}/lib/2x2-wallet-desktop.jar" "$@"
EOF
  chmod +x "${out}/2x2-Wallet.sh"
  cat > "${out}/README.txt" << 'EOF'
2X2 Wallet — macOS portable build
Requirements: Java 17+
Run: ./2x2-Wallet.sh
For a native .app bundle, use the jpackage output in this folder when present.
EOF
  (cd "${DIST_DIR}" && zip -qr "2x2-wallet-desktop-macos.zip" "2x2-Wallet-macos")
  ok "Portable zip: ${DIST_DIR}/2x2-wallet-desktop-macos.zip"
}

package_jpackage() {
  local jar="$1"
  local fx_dir="$2"
  if ! command -v jpackage >/dev/null 2>&1; then
    warn "Skipping jpackage (.app) — jpackage missing."
    return 0
  fi
  info "Creating macOS app-image with jpackage..."
  local tmp="${DIST_DIR}/jpackage-input"
  rm -rf "${tmp}"
  mkdir -p "${tmp}"
  cp "${jar}" "${tmp}/2x2-wallet-desktop.jar"
  rm -rf "${DIST_DIR}/2x2-Wallet.app"
  jpackage \
    --type app-image \
    --name "2x2-Wallet" \
    --input "${tmp}" \
    --main-jar "2x2-wallet-desktop.jar" \
    --main-class "com.x2x.desktop.MainApp" \
    --dest "${DIST_DIR}" \
    --java-options "--module-path=$fx_dir" \
    --java-options "--add-modules=javafx.controls,javafx.graphics" \
    || warn "jpackage failed — portable zip is still available."
  if [[ -d "${DIST_DIR}/2x2-Wallet.app" ]]; then
    ok "App bundle: ${DIST_DIR}/2x2-Wallet.app"
  fi
}

main() {
  require_macos
  ensure_jdk
  CLASSIFIER="$(arch_classifier)"
  build_jar
  JAR="$(find "${WALLET_DIR}/x2x-desktop/build/libs" -name '2x2-wallet-desktop*.jar' | head -n1)"
  [[ -n "${JAR}" && -f "${JAR}" ]] || fail "desktop JAR not found after build"
  download_javafx_mac "${CLASSIFIER}"
  FX_DIR="${CACHE_DIR}/javafx"
  package_portable "${JAR}" "${FX_DIR}" "${CLASSIFIER}"
  package_jpackage "${JAR}" "${FX_DIR}"
  ok "macOS desktop build complete."
}

main "$@"
