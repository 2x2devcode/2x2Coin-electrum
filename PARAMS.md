# 2x2coin (2X2) — Authoritative Wallet Parameters

Source of truth: `core-src/src/chainparams.cpp` (github.com/coinsdevcode/2x2Coin).
**These override the client's requirements doc where they differ (they do — see ⚠️).**

## Chain identity
| Item | Value | Notes |
|---|---|---|
| Name / Ticker | 2x2coin / 2X2 | |
| PoW algorithm | **scrypt** (`scrypt_1024_1_1_256`) | Litecoin-style → base fork = Electrum-LTC |
| Consensus | Hybrid PoW→PoS, `nLastPOWBlock = 110000` | PoS blocks have NO scrypt PoW target |
| Block header | Standard 80-byte (nVersion,prev,merkle,nTime,nBits,nNonce) | PoS `vchBlockSig` is in the block, NOT the header → SPV headers unaffected |
| Block time | ~10 min (262,800 blocks/yr) | |
| Premine | 11,000,000 at genesis | |

## Mainnet
| Item | Value |
|---|---|
| Magic bytes | `32 78 32 43` ("2x2C") |
| P2P port | 15190 |
| Genesis hash | `00000eea834a06692bc4f56d6f0061631c72fd75431ce9e5d7f3b9d712dc3a9b` |
| Genesis merkle | `8ec923e8d644631a549ddbd51cd350f597e29ca665979f01a501456bbc77f16b` |
| Genesis nTime / nNonce / nBits | 1769817600 / 184621 / 0x1e0fffff |
| **P2PKH version byte** | **3** ⚠️ (client doc wrongly said 0x1C=28) |
| **P2SH version byte** | **90** ⚠️ (client doc wrongly said 0x05=5) |
| **WIF (secret key)** | **128** (0x80) |
| xpub / xprv | `0x0488B21E` / `0x0488ADE4` (Bitcoin-standard) |
| Bech32 / SegWit | NONE (no bech32 HRP in source) → disable segwit address types |
| BIP44 coin type (SLIP-44) | **UNKNOWN — must confirm with client** (affects seed restore compatibility with any official 2x2 wallet) |

## Testnet
| Item | Value |
|---|---|
| Magic bytes | `4c 90 34 fb` |
| P2P port | 51884 |
| Genesis nTime / nNonce / nBits | 1769817600 / 127143 / 0x1f00ffff |
| P2PKH / P2SH / WIF | 51 / 50 / 239 |
| xpub / xprv | `0x043587CF` / `0x04358394` |

## DNS seeds / full nodes (mainnet)
seed.quimeralabs.org, seed1-5.quimeralabs.org
IPs: 161.97.176.125, 77.237.232.84, 75.119.137.26, 149.102.139.53, 144.91.107.244, 38.242.236.173

## ElectrumX server (client-provided) — ⚠️ BLOCKED
`144.91.107.244:50012` — host is UP (443/80/22 open, it is also a full-node seed) but
port **50012 is firewalled/not listening** (times out; 50002 refused). Cannot connect.
→ Client must expose/whitelist the ElectrumX port before any live-network sync/testing.

## Build decision
Fork **Electrum-LTC** (pooler/electrum-ltc) — already ships scrypt header verification.
Reparameterize `electrum_ltc/constants.py` with the mainnet/testnet values above.
Patch `electrum_ltc/blockchain.py` `verify_header()` (line ~306-320, "insufficient proof
of work") to relax the PoW-target check for PoS blocks (height > 110000). Disable segwit
address kinds. Point DEFAULT_SERVERS at the ElectrumX server. Android APK via Electrum's
kivy / python-for-android pipeline (Milestone 2).
