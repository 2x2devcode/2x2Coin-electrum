#!/usr/bin/env bash
# compile-android.sh — Build the 2x2coin Android wallet APK on Ubuntu 22.04
#
# Target: 2x2-wallet (minSdk 24, targetSdk/compileSdk 34 → Android 13 / API 33+)
# Usage:  bash compile-android.sh
#         bash compile-android.sh debug|release
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WALLET_DIR="${SCRIPT_DIR}/2x2-wallet"
LOG_DIR="${SCRIPT_DIR}/build-logs"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
ERROR_LOG="${LOG_DIR}/compile-android-error-${TIMESTAMP}.log"
BUILD_LOG="${LOG_DIR}/compile-android-${TIMESTAMP}.log"
BUILD_TYPE="${1:-release}"   # release | debug
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${HOME}/Android/Sdk}"
CMDLINE_TOOLS_VERSION="11076708"
SDK_PLATFORM="platforms;android-34"
SDK_BUILD_TOOLS="build-tools;34.0.0"
SDK_PLATFORM_TOOLS="platform-tools"

info()  { printf '\033[1;34m[INFO]\033[0m  %s\n' "$*"; }
ok()    { printf '\033[1;32m[OK]\033[0m    %s\n' "$*"; }
warn()  { printf '\033[1;33m[WARN]\033[0m  %s\n' "$*"; }
fail()  { printf '\033[1;31m[ERROR]\033[0m %s\n' "$*" >&2; exit 1; }

on_error() {
  local exit_code=$?
  mkdir -p "${LOG_DIR}"
  {
    echo "===== compile-android.sh FAILED ====="
    echo "timestamp : $(date -Is)"
    echo "exit_code : ${exit_code}"
    echo "cwd       : $(pwd)"
    echo "build_type: ${BUILD_TYPE}"
    echo "----- last build log (tail) -----"
    if [[ -f "${BUILD_LOG}" ]]; then
      tail -n 200 "${BUILD_LOG}"
    else
      echo "(no build log written yet)"
    fi
  } > "${ERROR_LOG}" 2>&1 || true
  printf '\033[1;31m[ERROR]\033[0m Compilation failed. Error log saved to:\n  %s\n' "${ERROR_LOG}" >&2
  exit "${exit_code}"
}
trap on_error ERR

require_ubuntu_2204() {
  if [[ ! -f /etc/os-release ]]; then
    warn "Could not detect OS (/etc/os-release missing). Continuing anyway."
    return 0
  fi
  # shellcheck source=/dev/null
  . /etc/os-release
  if [[ "${ID:-}" != "ubuntu" ]]; then
    warn "This script is intended for Ubuntu 22.04 (detected: ${ID:-unknown}). Continuing."
  elif [[ "${VERSION_ID:-}" != "22.04" ]]; then
    warn "This script is intended for Ubuntu 22.04 (detected: ${VERSION_ID:-unknown}). Continuing."
  else
    ok "Ubuntu 22.04 detected."
  fi
}

sudo_if_needed() {
  if [[ "$(id -u)" -eq 0 ]]; then
    "$@"
  else
    if ! command -v sudo >/dev/null 2>&1; then
      fail "Root privileges required to install packages, but 'sudo' was not found."
    fi
    sudo "$@"
  fi
}

pkg_installed() {
  dpkg -s "$1" >/dev/null 2>&1
}

ensure_apt_packages() {
  local packages=(
    ca-certificates
    curl
    wget
    unzip
    zip
    git
    openjdk-17-jdk
    libc6
    libstdc++6
    zlib1g
  )
  # Optional (some Android cmdline tools still look for these on Ubuntu 22.04)
  local optional_packages=(
    libncurses5
    libtinfo5
  )
  local missing=()
  local pkg

  info "Checking required apt packages..."
  for pkg in "${packages[@]}"; do
    if pkg_installed "${pkg}"; then
      ok "Package present: ${pkg}"
    else
      warn "Package missing: ${pkg}"
      missing+=("${pkg}")
    fi
  done

  if [[ ${#missing[@]} -gt 0 ]]; then
    info "Installing missing packages: ${missing[*]}"
    sudo_if_needed apt-get update -qq
    sudo_if_needed DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "${missing[@]}"
    ok "Required packages installed."
  else
    ok "All required apt packages are installed."
  fi

  local opt_missing=()
  for pkg in "${optional_packages[@]}"; do
    if ! pkg_installed "${pkg}"; then
      opt_missing+=("${pkg}")
    fi
  done
  if [[ ${#opt_missing[@]} -gt 0 ]]; then
    info "Trying optional packages: ${opt_missing[*]}"
    sudo_if_needed apt-get update -qq || true
    sudo_if_needed DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "${opt_missing[@]}" \
      || warn "Optional packages could not be installed (continuing)."
  fi
}

ensure_java() {
  if ! command -v java >/dev/null 2>&1; then
    fail "Java was not found after package installation."
  fi
  local ver
  ver="$(java -version 2>&1 | head -n1 || true)"
  ok "Java available: ${ver}"

  # Prefer JDK 17 for AGP 8.x
  if [[ -d /usr/lib/jvm/java-17-openjdk-amd64 ]]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
  elif [[ -d /usr/lib/jvm/java-17-openjdk-arm64 ]]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-arm64"
  fi
  if [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="${JAVA_HOME}/bin:${PATH}"
    ok "JAVA_HOME=${JAVA_HOME}"
  fi
}

sdkmanager_bin() {
  # Prefer modern cmdline-tools layout
  local candidates=(
    "${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"
    "${ANDROID_SDK_ROOT}/cmdline-tools/bin/sdkmanager"
  )
  local c
  for c in "${candidates[@]}"; do
    if [[ -x "${c}" ]]; then
      echo "${c}"
      return 0
    fi
  done
  return 1
}

ensure_android_sdk() {
  export ANDROID_SDK_ROOT
  export ANDROID_HOME="${ANDROID_SDK_ROOT}"
  mkdir -p "${ANDROID_SDK_ROOT}"

  if ! sdkmanager_bin >/dev/null 2>&1; then
    info "Android SDK cmdline-tools not found. Downloading..."
    local tmp zip_path
    tmp="$(mktemp -d)"
    zip_path="${tmp}/cmdline-tools.zip"
    curl -fsSL \
      "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
      -o "${zip_path}"
    mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"
    unzip -q "${zip_path}" -d "${tmp}"
    rm -rf "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
    mv "${tmp}/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
    rm -rf "${tmp}"
    ok "cmdline-tools installed under ${ANDROID_SDK_ROOT}/cmdline-tools/latest"
  else
    ok "sdkmanager found: $(sdkmanager_bin)"
  fi

  local SDKMANAGER
  SDKMANAGER="$(sdkmanager_bin)"

  info "Accepting Android SDK licenses..."
  yes | "${SDKMANAGER}" --sdk_root="${ANDROID_SDK_ROOT}" --licenses >/dev/null || true

  info "Ensuring Android SDK components for API 34 (Android 13+)..."
  "${SDKMANAGER}" --sdk_root="${ANDROID_SDK_ROOT}" \
    "${SDK_PLATFORM}" \
    "${SDK_BUILD_TOOLS}" \
    "${SDK_PLATFORM_TOOLS}" \
    >/dev/null
  ok "Android SDK components ready."
}

write_local_properties() {
  local props="${WALLET_DIR}/local.properties"
  # Always rewrite sdk.dir for this Linux host (checked-in file may point to macOS).
  printf 'sdk.dir=%s\n' "${ANDROID_SDK_ROOT}" > "${props}"
  ok "Wrote ${props} → sdk.dir=${ANDROID_SDK_ROOT}"
}

# Pre-download Gradle with curl (better retries/timeouts than the Java wrapper).
ensure_gradle_distribution() {
  local props="${WALLET_DIR}/gradle/wrapper/gradle-wrapper.properties"
  local version zip_name remote_url cache_dir zip abs_zip tmp_props
  version="$(sed -nE 's/.*gradle-([0-9.]+)-bin\.zip.*/\1/p' "${props}" | head -n1)"
  if [[ -z "${version}" ]]; then
    version="8.7"
  fi
  zip_name="gradle-${version}-bin.zip"
  remote_url="https://services.gradle.org/distributions/${zip_name}"
  cache_dir="${HOME}/.cache/2x2coin-android"
  zip="${cache_dir}/${zip_name}"
  mkdir -p "${cache_dir}"

  if [[ -f "${zip}" ]] && [[ -s "${zip}" ]]; then
    ok "Gradle ${version} already cached at ${zip}"
  else
    info "Downloading Gradle ${version} with curl (retries enabled)..."
    info "URL: ${remote_url}"
    curl -fL \
      --retry 15 \
      --retry-all-errors \
      --retry-delay 5 \
      --connect-timeout 60 \
      --max-time 900 \
      -o "${zip}.partial" \
      "${remote_url}"
    mv -f "${zip}.partial" "${zip}"
    ok "Downloaded ${zip}"
  fi

  abs_zip="$(readlink -f "${zip}" 2>/dev/null || realpath "${zip}")"
  tmp_props="$(mktemp)"
  # gradle-wrapper.properties escapes ':' as '\:' → file\:///abs/path.zip
  awk -v zip="${abs_zip}" '
    BEGIN { done_url=0; done_timeout=0 }
    /^distributionUrl=/ {
      print "distributionUrl=file\\://" zip
      done_url=1
      next
    }
    /^networkTimeout=/ {
      print "networkTimeout=600000"
      done_timeout=1
      next
    }
    { print }
    END {
      if (!done_url) print "distributionUrl=file\\://" zip
      if (!done_timeout) print "networkTimeout=600000"
    }
  ' "${props}" > "${tmp_props}"
  mv -f "${tmp_props}" "${props}"
  ok "Gradle wrapper will use local file: ${abs_zip}"
}

resolve_gradle_task() {
  local jks="${WALLET_DIR}/x2x-release.jks"
  case "${BUILD_TYPE}" in
    release)
      if [[ -f "${jks}" ]] && [[ -f "${WALLET_DIR}/keystore.properties" ]]; then
        GRADLE_TASK=":x2x-android:assembleRelease"
      else
        warn "Release keystore not found (${jks}). Falling back to debug APK."
        BUILD_TYPE="debug"
        GRADLE_TASK=":x2x-android:assembleDebug"
      fi
      ;;
    debug)
      GRADLE_TASK=":x2x-android:assembleDebug"
      ;;
    *)
      fail "Unknown build type '${BUILD_TYPE}'. Use: release | debug"
      ;;
  esac
}

find_apk() {
  local pattern
  if [[ "${BUILD_TYPE}" == "release" ]]; then
    pattern="${WALLET_DIR}/x2x-android/build/outputs/apk/release/*.apk"
  else
    pattern="${WALLET_DIR}/x2x-android/build/outputs/apk/debug/*.apk"
  fi
  # shellcheck disable=SC2086
  ls -1t ${pattern} 2>/dev/null | head -n1 || true
}

main() {
  mkdir -p "${LOG_DIR}"

  info "2x2coin Android wallet build starting..."
  info "Repo root : ${SCRIPT_DIR}"
  info "Wallet dir: ${WALLET_DIR}"
  info "Build type: ${BUILD_TYPE}"
  info "Build log : ${BUILD_LOG}"

  require_ubuntu_2204

  if [[ ! -d "${WALLET_DIR}" ]]; then
    fail "Wallet project not found: ${WALLET_DIR}"
  fi
  if [[ ! -f "${WALLET_DIR}/gradlew" ]]; then
    fail "Gradle wrapper missing: ${WALLET_DIR}/gradlew"
  fi

  ensure_apt_packages
  ensure_java
  ensure_android_sdk
  write_local_properties
  ensure_gradle_distribution

  chmod +x "${WALLET_DIR}/gradlew"

  local GRADLE_TASK=""
  resolve_gradle_task
  info "Running Gradle task: ${GRADLE_TASK} (build type: ${BUILD_TYPE})"

  local attempt max_attempts=3 gradle_rc=1
  for attempt in $(seq 1 "${max_attempts}"); do
    info "Gradle build attempt ${attempt}/${max_attempts}..."
    set +e
    (
      cd "${WALLET_DIR}"
      export ANDROID_SDK_ROOT ANDROID_HOME JAVA_HOME
      ./gradlew --no-daemon "${GRADLE_TASK}"
    ) 2>&1 | tee "${BUILD_LOG}"
    gradle_rc=${PIPESTATUS[0]}
    set -e
    if [[ "${gradle_rc}" -eq 0 ]]; then
      break
    fi
    warn "Gradle attempt ${attempt} failed (exit ${gradle_rc})."
    if [[ "${attempt}" -lt "${max_attempts}" ]]; then
      info "Retrying in 10s..."
      sleep 10
    fi
  done
  if [[ "${gradle_rc}" -ne 0 ]]; then
    return "${gradle_rc}"
  fi

  local apk
  apk="$(find_apk)"
  if [[ -z "${apk}" ]]; then
    fail "Build finished but no APK was found under x2x-android/build/outputs/apk/"
  fi

  # Disable ERR trap for clean success exit
  trap - ERR

  echo
  ok "Compilation succeeded."
  ok "APK path: ${apk}"
  ok "Full build log: ${BUILD_LOG}"
  echo
  echo "Install on device (optional):"
  echo "  adb install -r \"${apk}\""
}

main "$@"
