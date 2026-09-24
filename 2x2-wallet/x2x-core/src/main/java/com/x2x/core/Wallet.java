package com.x2x.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * High-level 2x2 HD wallet: derives keys/addresses, queries balances and UTXOs from the
 * REST server, and builds/signs/broadcasts payments. This is the API the Android UI uses.
 */
public final class Wallet {

    private final Bip32.Node account;   // m/44'/coin'/0'
    private final ApiClient api;

    /**
     * Extra unused addresses to scan beyond the highest known receive/change index
     * (BIP44 gap limit style). Minimum scan count is always at least this many.
     */
    public int lookAhead = 20;

    public Wallet(byte[] seed, ApiClient api) {
        this.account = Bip32.derivePath(Bip32.fromSeed(seed), NetworkParameters.defaultAccountPath());
        this.api = api;
    }

    public static Wallet fromMnemonic(String mnemonic, String passphrase, ApiClient api) {
        return new Wallet(Bip39.toSeed(mnemonic, passphrase), api);
    }

    public static final class DerivedKey {
        public final int change, index;
        public final byte[] priv, pub;
        public final String address;
        DerivedKey(int change, int index, byte[] priv, byte[] pub, String address) {
            this.change = change; this.index = index; this.priv = priv; this.pub = pub;
            this.address = address;
        }
    }

    public DerivedKey key(int change, int index) {
        Bip32.Node n = Bip32.deriveChild(Bip32.deriveChild(account, change), index);
        return new DerivedKey(change, index, n.privKey, n.pubKey, Address.p2pkhFromPublicKey(n.pubKey));
    }

    public String receiveAddress() { return key(0, 0).address; }
    public String receiveAddress(int index) { return key(0, index).address; }

    /** Prefer rotating change addresses; callers should persist the next index. */
    public String changeAddress(int index) { return key(1, index).address; }

    /** @deprecated use {@link #changeAddress(int)} to avoid address reuse */
    @Deprecated
    public String changeAddress() { return changeAddress(0); }

    /**
     * How many addresses to scan on a chain given the highest known used index
     * (inclusive). Always scans at least {@link #lookAhead} addresses.
     */
    public int scanCount(int highestKnownIndex) {
        int known = Math.max(0, highestKnownIndex);
        return Math.max(lookAhead, known + 1 + lookAhead);
    }

    /** Scan derived addresses and collect all spendable outputs with their keys. */
    public List<TxBuilder.Spendable> collectSpendable() throws IOException {
        return collectSpendable(lookAhead - 1, lookAhead - 1);
    }

    /**
     * @param highestReceiveIndex highest receive index the UI has shown / used (inclusive)
     * @param highestChangeIndex  highest change index used for spends (inclusive)
     */
    public List<TxBuilder.Spendable> collectSpendable(int highestReceiveIndex, int highestChangeIndex)
            throws IOException {
        return collectSpendable(highestReceiveIndex, highestChangeIndex, Long.MAX_VALUE);
    }

    /**
     * Collect UTXOs with minimal API traffic. Scans known receive/change indices first
     * (plus a small gap), stops once {@code needSat} is covered, and never walks the full
     * BIP44 look-ahead window on every call (that trips server rate limits).
     */
    public List<TxBuilder.Spendable> collectSpendable(int highestReceiveIndex, int highestChangeIndex,
                                                      long needSat) throws IOException {
        List<TxBuilder.Spendable> out = new ArrayList<>();
        int gap = Math.min(3, lookAhead); // light discovery beyond the known tip
        int receiveN = Math.max(0, highestReceiveIndex) + 1 + gap;
        int changeN = Math.max(0, highestChangeIndex) + 1 + gap;

        scanChainForUtxos(out, 0, receiveN, needSat);
        if (sumSat(out) >= needSat) return out;
        scanChainForUtxos(out, 1, changeN, needSat);
        if (sumSat(out) >= needSat) return out;

        // Last resort: if still empty (e.g. rate-limited on first pass), pause and retry
        // only the primary deposit + change tips once.
        if (out.isEmpty()) {
            sleepQuiet(3_000L);
            scanChainForUtxos(out, 0, Math.max(1, highestReceiveIndex + 1), needSat);
            if (out.isEmpty()) {
                scanChainForUtxos(out, 1, Math.max(1, highestChangeIndex + 1), needSat);
            }
        }
        return out;
    }

    private void scanChainForUtxos(List<TxBuilder.Spendable> out, int change, int count, long needSat)
            throws IOException {
        IOException lastNetwork = null;
        for (int i = 0; i < count; i++) {
            if (sumSat(out) >= needSat) return;
            DerivedKey k = key(change, i);
            try {
                List<ApiClient.Utxo> utxos = api.getUtxos(k.address);
                for (ApiClient.Utxo u : utxos) {
                    out.add(new TxBuilder.Spendable(u.txid, u.vout, u.valueSat, u.scriptPubKey,
                            k.priv, k.pub, u.nTime));
                }
            } catch (ApiException e) {
                if (e.getKind() == ApiException.Kind.PIN_MISMATCH) throw e;
                if (e.getKind() == ApiException.Kind.RATE_LIMITED) {
                    // Keep what we have; do not probe dozens more addresses into a hot limit.
                    if (!out.isEmpty()) return;
                    lastNetwork = e;
                    break;
                }
                lastNetwork = e;
            } catch (IOException e) {
                lastNetwork = e;
            }
        }
        if (out.isEmpty() && lastNetwork != null) throw lastNetwork;
    }

    private static long sumSat(List<TxBuilder.Spendable> utxos) {
        long t = 0;
        for (TxBuilder.Spendable s : utxos) t += s.valueSat;
        return t;
    }

    private static void sleepQuiet(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public long getBalanceSat() throws IOException {
        return getBalanceSat(lookAhead - 1, lookAhead - 1);
    }

    public long getBalanceSat(int highestReceiveIndex, int highestChangeIndex) throws IOException {
        long total = 0;
        for (TxBuilder.Spendable s : collectSpendable(highestReceiveIndex, highestChangeIndex)) {
            total += s.valueSat;
        }
        return total;
    }

    /**
     * Cheap confirmed balance for UI refresh: sums {@code /balance} for known receive/change
     * indices only (no look-ahead UTXO storm). Use {@link #collectSpendable(int, int, long)}
     * when building a spend.
     */
    public long getKnownBalancesSat(int highestReceiveIndex, int highestChangeIndex)
            throws IOException {
        long total = 0;
        IOException last = null;
        int failures = 0;
        for (int c = 0; c < 2; c++) {
            int n = Math.max(0, c == 0 ? highestReceiveIndex : highestChangeIndex) + 1;
            for (int i = 0; i < n; i++) {
                try {
                    total += api.getBalance(key(c, i).address).confirmedSat;
                } catch (ApiException e) {
                    if (e.getKind() == ApiException.Kind.PIN_MISMATCH) throw e;
                    if (e.getKind() == ApiException.Kind.RATE_LIMITED) {
                        if (total > 0) return total;
                        throw e;
                    }
                    failures++;
                    last = e;
                } catch (IOException e) {
                    failures++;
                    last = e;
                }
            }
        }
        if (total == 0 && last != null && failures > 0) throw last;
        return total;
    }

    /**
     * Activity across known receive + change addresses. The deposit address alone goes empty
     * after the first spend (funds sit on change); {@code /txs} is often empty so we use
     * {@link ApiClient#getActivity(String)} (UTXO fallback) per address.
     */
    public List<ApiClient.TxInfo> listActivity(int highestReceiveIndex, int highestChangeIndex)
            throws IOException {
        List<ApiClient.TxInfo> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        IOException last = null;
        for (int c = 0; c < 2; c++) {
            int n = Math.max(0, c == 0 ? highestReceiveIndex : highestChangeIndex) + 1;
            for (int i = 0; i < n; i++) {
                try {
                    for (ApiClient.TxInfo t : api.getActivity(key(c, i).address)) {
                        String id = t.txid == null ? "" : t.txid;
                        if (id.isEmpty() || !seen.add(id)) continue;
                        out.add(t);
                    }
                } catch (ApiException e) {
                    if (e.getKind() == ApiException.Kind.PIN_MISMATCH) throw e;
                    if (e.getKind() == ApiException.Kind.RATE_LIMITED) {
                        if (!out.isEmpty()) return out;
                        throw e;
                    }
                    last = e;
                } catch (IOException e) {
                    last = e;
                }
            }
        }
        if (out.isEmpty() && last != null) throw last;
        return out;
    }

    public long feePerKb() throws IOException { return api.getFeePerKb(); }

    public TxBuilder.Built createTransaction(String to, long amountSat, int changeIndex)
            throws IOException {
        return createTransaction(to, amountSat, changeIndex, lookAhead - 1, changeIndex);
    }

    public TxBuilder.Built createTransaction(String to, long amountSat, int changeIndex,
                                             int highestReceiveIndex, int highestChangeIndex)
            throws IOException {
        int changeScan = Math.max(highestChangeIndex, changeIndex);
        // Let the API rate-limit window cool down after a UI refresh storm.
        sleepQuiet(1_500L);
        // Rough ceiling so we stop the UTXO scan once we have enough (avoids rate limits).
        long needSat = amountSat + Math.max(NetworkParameters.DEFAULT_FEE_PER_KB, 10_000L);
        return TxBuilder.build(
                collectSpendable(highestReceiveIndex, changeScan, needSat),
                to, amountSat, api.getFeePerKb(),
                changeAddress(changeIndex));
    }

    public TxBuilder.Built createTransaction(String to, long amountSat) throws IOException {
        return createTransaction(to, amountSat, 0);
    }

    /** Build, sign and broadcast a payment; returns the txid. */
    public String send(String to, long amountSat, int changeIndex) throws IOException {
        return send(to, amountSat, changeIndex, lookAhead - 1, changeIndex);
    }

    public String send(String to, long amountSat, int changeIndex,
                       int highestReceiveIndex, int highestChangeIndex) throws IOException {
        AppLog.info("send start to=" + to + " amountSat=" + amountSat
                + " changeIndex=" + changeIndex
                + " recvTip=" + highestReceiveIndex + " changeTip=" + highestChangeIndex);
        try {
            TxBuilder.Built built = createTransaction(to, amountSat, changeIndex,
                    highestReceiveIndex, highestChangeIndex);
            return broadcastSigned(built);
        } catch (IOException e) {
            AppLog.error("send failed to=" + to + " amountSat=" + amountSat
                    + " msg=" + ApiException.userMessage(e), e);
            throw e;
        }
    }

    /** Broadcast an already-built signed transaction (avoids a second UTXO scan). */
    public String broadcastSigned(TxBuilder.Built built) throws IOException {
        String hex = built.hex();
        AppLog.info("broadcast start txid=" + built.txid()
                + " nTime=" + built.tx.nTime
                + " inputs=" + built.numInputs
                + " feeSat=" + built.feeSat
                + " changeSat=" + built.changeSat
                + " hexLen=" + (hex.length() / 2)
                + " outs=" + built.tx.outputs.size());
        for (int i = 0; i < built.tx.outputs.size(); i++) {
            Transaction.Output o = built.tx.outputs.get(i);
            AppLog.info("broadcast out[" + i + "] valueSat=" + o.value
                    + " spkLen=" + (o.scriptPubKey == null ? 0 : o.scriptPubKey.length));
        }
        AppLog.info("broadcast hex=" + hex);
        try {
            ApiClient.BroadcastResult r = api.broadcast(hex);
            if (!r.ok) {
                AppLog.error("broadcast result not ok txid=" + built.txid());
                throw new ApiException(ApiException.Kind.INVALID_TX, 0, ApiException.MSG_INVALID_TX);
            }
            String txid = r.txid != null ? r.txid : built.txid();
            AppLog.info("broadcast success txid=" + txid);
            return txid;
        } catch (IOException e) {
            AppLog.error("broadcast failed txid=" + built.txid()
                    + " nTime=" + built.tx.nTime
                    + " msg=" + ApiException.userMessage(e), e);
            throw e;
        }
    }

    public String send(String to, long amountSat) throws IOException {
        return send(to, amountSat, 0);
    }

    public ApiClient api() { return api; }
}
