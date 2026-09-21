package com.x2x.core;

import java.util.List;
import java.util.Set;

/**
 * 2x2coin (2X2) network parameters — the single source of truth for the whole app.
 *
 * <p>Every value here is taken from the coin daemon source
 * ({@code 2x2Coin/src/chainparams.cpp}) and the project's developer notes.
 * Do not duplicate these constants elsewhere.
 *
 * <p>IMPORTANT: older client docs listed P2PKH=0x1C / P2SH=0x05, but the actual
 * chain uses the values below (P2PKH=3, P2SH=90, WIF=128). These are authoritative.
 */
public final class NetworkParameters {

    private NetworkParameters() {}

    // ---- Identity ----
    public static final String COIN_NAME = "2x2coin";
    public static final String TICKER = "2X2";
    public static final int DECIMALS = 8;
    public static final long COIN = 100_000_000L; // 1 2X2 = 1e8 satoshi

    // ---- Base58 address versions (mainnet, from chainparams.cpp) ----
    public static final int P2PKH_VERSION = 3;    // PUBKEY_ADDRESS  -> addresses start with '2'
    public static final int P2SH_VERSION = 90;    // SCRIPT_ADDRESS
    public static final int WIF_VERSION = 128;    // SECRET_KEY (0x80)

    // ---- BIP32 extended key version bytes ----
    public static final int XPUB_HEADER = 0x0488B21E; // xpub
    public static final int XPRV_HEADER = 0x0488ADE4; // xprv

    // ---- HD derivation ----
    /**
     * SLIP-44 coin type. Still awaiting an official assignment; {@code 0'} (Bitcoin)
     * is used as a temporary placeholder. Changing this later will produce different
     * addresses for the same seed — document any migration carefully.
     */
    public static final int BIP44_COIN_TYPE = 0;

    /** Default account derivation path prefix: m/44'/coin'/0' */
    public static String defaultAccountPath() {
        return "m/44'/" + BIP44_COIN_TYPE + "'/0'";
    }

    // ---- Consensus (reference; light wallet trusts the server index) ----
    public static final long LAST_POW_BLOCK = 110000; // hybrid PoW -> PoS switch
    public static final String GENESIS_HASH =
            "00000eea834a06692bc4f56d6f0061631c72fd75431ce9e5d7f3b9d712dc3a9b";

    // ---- Transaction wire format ----
    // 2x2 (Peercoin-style) transactions carry an extra nTime field:
    //   nVersion(int32), nTime(uint32), vin[], vout[], nLockTime(uint32)
    public static final boolean TX_HAS_NTIME = true;
    public static final int TX_CURRENT_VERSION = 1;

    /**
     * Seconds to subtract from wall-clock when setting tx {@code nTime}.
     * Protocol V2 mempool policy only allows {@code GetAdjustedTime() + 15}
     * ({@code FutureDrift}); a slightly-fast client clock otherwise yields
     * {@code time-too-new} → API "invalid transaction". Past timestamps are fine
     * as long as {@code nTime >=} each spent coin's {@code nTime}.
     */
    public static final long TX_TIME_SAFETY_LAG_SECONDS = 120L;

    // ---- Fees ----
    public static final long DEFAULT_FEE_PER_KB = 10_000L; // sat/kB (overridden by /api/fee)

    /**
     * Minimum non-dust output (sat). Matches daemon {@code IsDust} at the default
     * {@code minRelayTxFee} of 10_000 sat/kB (~5460) with a small margin.
     */
    public static final long MIN_NON_DUST_OUTPUT = 10_000L;

    // ---- REST endpoints ----
    public static final String OFFICIAL_API_BASE_URL = "https://server.2x2coin.com";
    public static final String EXPLORER_BASE_URL = "https://serverexplorer.2x2coin.com";

    /**
     * Official API base URLs tried in order (first success wins).
     * Add failover hosts here when available.
     */
    public static final List<String> OFFICIAL_API_BASE_URLS = List.of(
            OFFICIAL_API_BASE_URL
    );

    public static final List<String> EXPLORER_BASE_URLS = List.of(
            EXPLORER_BASE_URL
    );

    /**
     * SHA-256 SPKI pins (Base64) for the official API host leaf certificate.
     * Update when the server TLS key rotates — see DEVELOPER.md.
     * Current leaf (Let's Encrypt, SAN includes server + serverexplorer) plus previous
     * pins kept briefly for rollover of older installs mid-update.
     */
    public static final Set<String> API_TLS_PINS = TlsPinning.pinsOf(
            "gDTqmm23QEzOQQYWCnoEhCHrMPyLZ93yQzSshVcLato=", // 2026-09 leaf (reuse-key recommended)
            "901DCr7Jn2MOhdKSoe1+tE/itM0QNe0bGrc77NgzTvk="  // previous
    );

    /** SHA-256 SPKI pins (Base64) for the explorer host leaf certificate. */
    public static final Set<String> EXPLORER_TLS_PINS = TlsPinning.pinsOf(
            "gDTqmm23QEzOQQYWCnoEhCHrMPyLZ93yQzSshVcLato=", // same leaf as API (shared cert)
            "w3iMXmahnCVJ21hO1kapsob8NGeQ9GlWOpmCjY89/dU="  // previous
    );

    /** Block explorer / website shown in the UI. */
    public static final String WEBSITE_URL = "https://2x2coin.com";

    /** Public block explorer (human-facing web UI). */
    public static final String EXPLORER_WEB_URL = "https://explorer.2x2coin.com";

    /** Deep-link to a transaction on the public explorer. */
    public static String explorerTxUrl(String txid) {
        if (txid == null || txid.isEmpty()) return EXPLORER_WEB_URL;
        return EXPLORER_WEB_URL + "/tx/" + txid;
    }
}
