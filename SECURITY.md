# Security Policy

## Reporting a vulnerability

Email the maintainers of the `2x2devcode/2x2Coin_android` repository. Do not open public issues for undisclosed wallet or key-handling flaws.

## Signing keys

Release APKs must be signed with a keystore that is **never** committed to git.

If `x2x-release.jks` or `keystore.properties` were previously published:

1. Generate a **new** keystore.
2. Remove the old files from the working tree and from git history if needed.
3. Publish an app update signed with the new key (users must uninstall/reinstall or accept a new signing certificate, depending on distribution channel).

## Certificate pinning

The REST client pins SHA-256 SPKI hashes for `server.2x2coin.com` and `serverexplorer.2x2coin.com`.
When TLS certificates rotate, update `NetworkParameters.API_TLS_PINS` / `EXPLORER_TLS_PINS` and ship a new app build. See `DEVELOPER.md`.

## Trust model

This light wallet trusts the official REST indexer for balances and UTXOs (it is not full SPV). Treat server compromise as high severity.
