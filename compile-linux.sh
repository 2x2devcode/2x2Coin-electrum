#!/usr/bin/env bash
# compile-linux.sh — Build native Linux desktop package for 2X2 Wallet on Ubuntu 22.04
#
# Produces:
#   dist/linux/2x2-Wallet/          (portable app-image directory via jpackage, when available)
#   dist/linux/2x2-wallet-desktop-linux.zip
#
# Usage:  bash compile-linux.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WALLET_DIR="${SCRIPT_DIR}/2x2-wallet"
DIST_DIR="${SCRIPT_DIR}/dist/linux"
LOG_DIR="${SCRIPT_DIR}/build-logs"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BUILD_LOG="${LOG_DIR}/compile-linux-${TIMESTAMP}.log"
ERROR_LOG="${LOG_DIR}/compile-linux-error-${TIMESTAMP}.log"
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
    echo "===== compile-linux.sh FAILED ====="
    echo "timestamp : $(date -Is)"
    echo "exit_code : ${exit_code}"
    echo "----- last build log (tail) -----"
    [[ -f "${BUILD_LOG}" ]] && tail -n 200 "${BUILD_LOG}" || echo "(no build log)"
  } > "${ERROR_LOG}" 2>&1 || true
  printf '\033[1;31m[ERROR]\033[0m Compilation failed. Error log:\n  %s\n' "${ERROR_LOG}" >&2
  exit "${exit_code}"
}
trap on_error ERR

mkdir -p "${LOG_DIR}" "${DIST_DIR}"
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

ensure_jdk() {
  if ! command -v java >/dev/null 2>&1; then
    info "Installing OpenJDK 17..."
    sudo_if_needed apt-get update -y
    sudo_if_needed apt-get install -y openjdk-17-jdk wget unzip zip
  fi
  if ! command -v jpackage >/dev/null 2>&1; then
    warn "jpackage not found on PATH — portable zip will still be produced."
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

download_javafx_linux() {
  local cache="${SCRIPT_DIR}/.cache/javafx"
  mkdir -p "${cache}"
  local mods=(base controls graphics)
  for m in "${mods[@]}"; do
    local jar="${cache}/javafx-${m}-${JAVA_FX_VERSION}-linux.jar"
    if [[ ! -f "${jar}" ]]; then
      info "Downloading JavaFX ${m} (linux)..."
      wget -q -O "${jar}" \
        "https://repo1.maven.org/maven2/org/openjfx/javafx-${m}/${JAVA_FX_VERSION}/javafx-${m}-${JAVA_FX_VERSION}-linux.jar"
    fi
  done
}

package_portable() {
  local jar="$1"
  local fx_dir="$2"
  local out="${DIST_DIR}/2x2-Wallet-linux"
  rm -rf "${out}"
  mkdir -p "${out}/lib" "${out}/javafx"
  cp "${jar}" "${out}/lib/2x2-wallet-desktop.jar"
  cp "${fx_dir}/javafx-"*"-linux.jar" "${out}/javafx/"
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
2X2 Wallet — Linux portable build
Requirements: Java 17+ (OpenJDK recommended)
Run: ./2x2-Wallet.sh
EOF
  (cd "${DIST_DIR}" && zip -qr "2x2-wallet-desktop-linux.zip" "2x2-Wallet-linux")
  ok "Portable zip: ${DIST_DIR}/2x2-wallet-desktop-linux.zip"
}

package_jpackage() {
  local jar="$1"
  local fx_dir="$2"
  if ! command -v jpackage >/dev/null 2>&1; then
    warn "Skipping jpackage app-image (jpackage missing)."
    return 0
  fi
  info "Creating Linux app-image with jpackage..."
  local tmp="${DIST_DIR}/jpackage-input"
  rm -rf "${tmp}" "${DIST_DIR}/2x2-Wallet"
  mkdir -p "${tmp}"
  cp "${jar}" "${tmp}/2x2-wallet-desktop.jar"
  local fx_args=()
  for j in "${fx_dir}"/javafx-*-linux.jar; do
    fx_args+=(--module-path "${j}")
  done
  # jpackage --module-path wants a single dir
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
  if [[ -d "${DIST_DIR}/2x2-Wallet" ]]; then
    ok "App image: ${DIST_DIR}/2x2-Wallet"
  fi
}

main() {
  require_ubuntu_2204
  ensure_jdk
  build_jar
  JAR="$(find "${WALLET_DIR}/x2x-desktop/build/libs" -name '2x2-wallet-desktop*.jar' | head -n1)"
  [[ -n "${JAR}" && -f "${JAR}" ]] || fail "desktop JAR not found after build"
  download_javafx_linux >/dev/null
  FX_DIR="${SCRIPT_DIR}/.cache/javafx"
  package_portable "${JAR}" "${FX_DIR}"
  package_jpackage "${JAR}" "${FX_DIR}"
  ok "Linux desktop build complete."
}

main "$@"
