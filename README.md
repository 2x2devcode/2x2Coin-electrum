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

### Windows package (cross-built on Ubuntu)

```bash
bash compile-windows.sh
# → dist/windows/2x2-Wallet.exe                     (SELF-CONTAINED — preferred)
# → dist/windows/2x2-wallet-desktop-windows.zip     (portable folder)
# → dist/windows/2x2-Wallet-Setup.exe               (optional classic installer)
```

**Self-contained:** double-click `2x2-Wallet.exe` (embeds Liberica JRE 17 Full + app).
It extracts under `%LOCALAPPDATA%\2x2-Wallet\runtime` and launches — no other files needed.
Requires **64-bit Windows (x64)**.

If you see *“This app can’t run on your PC”*, you are not on Windows x64 (or the file was corrupted/quarantined) — re-download and use the new build.

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
- Diagnostic logs (send/broadcast): Windows `%LOCALAPPDATA%\2x2-Wallet\logs\wallet.log`;
  Linux/macOS `~/.2x2-wallet/logs/wallet.log`.

## Version

App version: **1.3.14** (`versionCode` 15).

If Windows self-contained `2x2-Wallet.exe` still shows **certificate pin mismatch** after updating,
delete the folder `%LOCALAPPDATA%\2x2-Wallet` and run the new exe again (old runtimes were
cached under the previous app version). Do **not** delete this folder if you only need logs —
logs live in `%LOCALAPPDATA%\2x2-Wallet\logs\wallet.log` (wallet seed stays in `~/.2x2-wallet\`).
