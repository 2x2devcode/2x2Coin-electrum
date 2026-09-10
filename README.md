# 2X2 Wallet

Self-custody wallet for **2x2coin (2X2)** — hybrid PoW+PoS.

## Modules (`2x2-wallet/`)

| Module | Purpose |
|---|---|
| `x2x-core` | Shared pure-JVM wallet core (keys, txs, REST client) |
| `x2x-android` | Android UI (APK) |
| `x2x-desktop` | Native desktop UI (JavaFX) for Windows / Linux / macOS |

`electrum-2x2/` is a legacy Electrum-LTC fork kept for reference.

## Build Android APK (Ubuntu 22.04)

```bash
bash compile-android.sh          # release (default)
bash compile-android.sh debug
```

Or manually:

```bash
cd 2x2-wallet
cp keystore.properties.example keystore.properties   # local signing only — never commit
# Requires ANDROID_HOME or local.properties sdk.dir
./gradlew :x2x-android:assembleRelease
```

## Build desktop

### Linux (Ubuntu 22.04)

```bash
bash compile-linux.sh
# → dist/linux/2x2-wallet-desktop-linux.zip
```

### Windows package (cross-built on Ubuntu 22.04)

```bash
bash compile-windows.sh
# → dist/windows/2x2-Wallet-windows/2x2-Wallet.exe   (portable — no install)
# → dist/windows/2x2-wallet-desktop-windows.zip
# → dist/windows/2x2-Wallet-Setup.exe                (optional NSIS installer)
```

**Portable (recommended):** unzip `2x2-wallet-desktop-windows.zip` anywhere and double-click
`2x2-Wallet.exe`. Bundled JRE 17 + JavaFX 17 — no system Java and no installer.

The optional Setup.exe installs under Program Files, creates Start Menu + Desktop shortcuts,
and registers an uninstaller in Windows Settings.

### macOS (run on a Mac)

```bash
bash compile-macos.sh
# → dist/macos/2x2-wallet-desktop-macos.zip  (+ .app when jpackage is available)
```

### Dev run (any OS with JDK 17+)

```bash
cd 2x2-wallet
./gradlew :x2x-desktop:run
```

## Security notes

- Never commit `keystore.properties`, `*.jks`, or release binaries.
- If a signing key was ever published, **rotate the keystore**.
- TLS SPKI pinning is enabled for official API hosts — see `DEVELOPER.md`.
- Desktop wallet data is stored encrypted under `~/.2x2-wallet/`.

## Version

App version: **1.3.0** (`versionCode` 4).
