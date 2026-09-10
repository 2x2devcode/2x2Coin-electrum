# 2X2 Wallet (Android)

Self-custody Android wallet for **2x2coin (2X2)** — hybrid PoW+PoS.

## App module

The shipping app lives under `x2x-wallet/`:

- `x2x-core` — keys, addresses, transactions, REST client (pure JVM)
- `x2x-android` — Android UI

```bash
cd x2x-wallet
cp keystore.properties.example keystore.properties   # local signing only
./gradlew :x2x-core:test
./gradlew :x2x-android:assembleRelease   # requires Android SDK + keystore
```

## Security notes

- Never commit `keystore.properties`, `*.jks`, or release APKs.
- If a signing key was ever pushed to a public remote, **rotate the keystore** and publish an update signed with the new key.
- TLS SPKI pinning is enabled for the official API hosts — see `DEVELOPER.md`.

## Version

Current app version: **1.2.0** (`versionCode` 3).
