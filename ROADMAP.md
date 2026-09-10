# 2x2coin Android Wallet — Project Roadmap

**Client:** web_master2025 (Fiverr) · **Budget:** $350 · **Deadline:** 3 months (from 2026-08-07)
**Deliverable:** signed Android `.apk` — Electrum-style SPV wallet for 2x2coin (2X2).
**Repo/source of truth:** github.com/coinsdevcode/2x2Coin · **Dev machine:** macOS (this Mac).
**Project dir:** `/Volumes/SP_SSD/2x2coin-wallet/`

## Architecture (client-locked)
Electrum client → the client's **ElectrumX server** (NOT the P2P/breadwallet design in the
original offer text). 2x2 is a **scrypt** Litecoin-lineage **hybrid PoW→PoS** chain
(`nLastPOWBlock=110000`). Chosen base: **fork of Electrum-LTC** (already ships scrypt).
Android APK built from Electrum's Kivy / python-for-android pipeline (client accepts "Option 3").

Note: an Electrum client speaks the **ElectrumX JSON protocol**, so the coin's P2P magic bytes
and P2P port (15190) are NOT used by the wallet — only address/key params, header hashing, and
the ElectrumX endpoint matter.

---

## MILESTONE 1 — Wallet Build & 2x2 Network Integration ($150, 20d)

### DONE ✅
- [x] Fork Electrum-LTC 4.3.2; Python 3.11 venv; all deps + libsecp256k1 + scrypt working.
- [x] Reparameterize `constants.py` (mainnet + testnet): P2PKH=3, P2SH=90, WIF=128,
      xpub/xprv, genesis `00000eea…dc3a9b`, ports, `NLAST_POW_BLOCK=110000`.
- [x] Corrected the client's WRONG address prefixes (doc said 0x1C/0x05 → real 3/90).
- [x] Force **legacy p2pkh** wallets (2x2 has no segwit): default seed type = standard.
- [x] Patch `verify_header` for hybrid PoW/PoS (enforce scrypt PoW only ≤ block 110000,
      skip PoS blocks, trust header's own bits — 2x2 retargets unlike LTC).
- [x] Patch `get_target` (drop LTC 2016-block retarget).
- [x] Patch `hash_header` **version-aware**: v≤6 → scrypt, v>6 → double-SHA256
      (matches 2x2 `CBlockHeader::GetHash()`). Verified against genesis hash. ✅
- [x] Point default server at ElectrumX `144.91.107.244:50012`.
- [x] Verified: wallet create + restore-from-seed reproduce identical valid 2x2 addresses
      (e.g. `2Lqgpn7wqtUxQgEjMJEgjqNKAdW2W3zmeJ`, version byte 3), correct WIF.

- [x] Offline transaction build + sign test — builds, signs, finalizes a valid 2x2 p2pkh
      tx (is_complete=True, correct fee). Send path proven.

### REMAINING for M1
- [ ] **Live sync against ElectrumX — BLOCKED** (server port 50012 firewalled/unreachable).
- [ ] Confirm with client: (a) ElectrumX is SSL or plain TCP on 50012, (b) BIP44 SLIP-44
      coin type (currently placeholder 0) for restore compatibility.

---

## MILESTONE 2 — Branding, Send/Receive & Release APK ($200, 30d)

- [ ] **Branding:** app name "2X2 Electrum Wallet", green theme, app icon, splash,
      website link https://2x2coin.com, ticker 2X2. Rebrand `electrum_ltc` strings/assets.
- [ ] **UI (Kivy Android):** Electrum's mobile UI already provides Balance / Receive(Deposit,
      QR, copy, new address, labels) / Send(fee normal/fast, QR scan) / Transactions /
      settings / server-status(active mainnet + block height). Restyle to client's spec.
- [ ] **Send / Receive / QR / fee control** — verify on Electrum mobile flow.
- [ ] **Android build:** buildozer / python-for-android. macOS can't build p4a natively →
      use Electrum's provided **Docker Linux** android build. Produce **signed release APK**.
- [ ] **Live-network test** (needs server unblocked) → deliver signed `.apk`.

---

## BLOCKERS (need client) 🚧
1. **ElectrumX `144.91.107.244:50012` unreachable** — host up, port firewalled. Blocks all
   live sync/testing. Client must expose/whitelist the port + confirm SSL vs TCP.
2. **BIP44 coin type** unknown — needed for seed backup/restore compatibility.

## Environment
macOS · JDK17 · Python 3.11 venv at `electrum-2x2/.venv` · libsecp256k1 (brew) symlinked
into `electrum_ltc/`. Android build will need Docker (for p4a) — to be set up in M2.
