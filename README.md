# 2X2 Wallet (Android)

Self-custody Android wallet for **2x2coin (2X2)** — hybrid PoW+PoS.

## App module

The shipping app lives under `2x2-wallet/`:

- `2x2-core` — keys, addresses, transactions, REST client (pure JVM)
- `2x2-android` — Android UI

`electrum-2x2/` is a legacy Electrum-LTC fork kept for reference. The production Android app is the Java/`2x2-wallet` stack.

```bash
cd 2x2-wallet
cp keystore.properties.example keystore.properties   # local signing only — never commit
./gradlew :x2x-core:test
./gradlew :x2x-android:assembleRelease   # requires Android SDK + keystore
```

## Security notes

- Never commit `keystore.properties`, `*.jks`, or release APKs.
- If a signing key was ever pushed to a public remote, **rotate the keystore** and publish an update signed with the new key.
- TLS SPKI pinning is enabled for the official API hosts — see `DEVELOPER.md`.
- Sensitive actions (send, backup, delete) require PIN and/or biometrics.

## Version

Current app version: **1.3.0** (`versionCode` 4).
