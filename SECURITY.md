# Security Policy

## Reporting a vulnerability

Email the maintainers of this repository. Do not open public issues for undisclosed wallet or key-handling flaws.

## Signing keys

Release APKs must be signed with a keystore that is **never** committed to git.

If `x2x-release.jks` or `keystore.properties` were previously published:

1. Generate a **new** keystore.
2. Remove the old files from the working tree and from git history if needed.
3. Publish an app update signed with the new key (users must uninstall/reinstall or accept a new signing certificate, depending on distribution channel).

## Certificate pinning

The REST client pins SHA-256 SPKI hashes for `server.2x2coin.com` and `serverexplorer.2x2coin.com`.
When TLS certificates rotate, update `NetworkParameters.API_TLS_PINS` / `EXPLORER_TLS_PINS` and ship a new app build. See `DEVELOPER.md`.

Do **not** empty the pin sets or disable pinning in release to “debug” TLS failures — fix the server cert (`certbot --reuse-key`) or ship updated hashes.

## HTTP client errors

`ApiClient` classifies HTTP status before retrying:

- `400` / `404` / `413` — no retry (broadcast must not POST the same hex again)
- `429` — backoff + at most one extra attempt
- `5xx` / timeouts — up to 3 short attempts

UI must show `ApiException.userMessage(...)` (stable Portuguese copy), never raw JSON bodies.

## Trust model

This light wallet trusts the official REST indexer for balances and UTXOs (it is not full SPV). Treat server compromise as high severity.
