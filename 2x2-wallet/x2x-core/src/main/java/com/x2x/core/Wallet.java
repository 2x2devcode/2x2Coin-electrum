package com.x2x.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        List<TxBuilder.Spendable> out = new ArrayList<>();
        int receiveN = scanCount(highestReceiveIndex);
        int changeN = scanCount(highestChangeIndex);
        for (int c = 0; c < 2; c++) {
            int n = (c == 0) ? receiveN : changeN;
            for (int i = 0; i < n; i++) {
                DerivedKey k = key(c, i);
                for (ApiClient.Utxo u : api.getUtxos(k.address)) {
                    out.add(new TxBuilder.Spendable(u.txid, u.vout, u.valueSat, u.scriptPubKey,
                            k.priv, k.pub));
                }
            }
        }
        return out;
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

    public long feePerKb() throws IOException { return api.getFeePerKb(); }

    public TxBuilder.Built createTransaction(String to, long amountSat, int changeIndex)
            throws IOException {
        return createTransaction(to, amountSat, changeIndex, lookAhead - 1, changeIndex);
    }

    public TxBuilder.Built createTransaction(String to, long amountSat, int changeIndex,
                                             int highestReceiveIndex, int highestChangeIndex)
            throws IOException {
        int changeScan = Math.max(highestChangeIndex, changeIndex);
        return TxBuilder.build(
                collectSpendable(highestReceiveIndex, changeScan),
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
        TxBuilder.Built built = createTransaction(to, amountSat, changeIndex,
                highestReceiveIndex, highestChangeIndex);
        ApiClient.BroadcastResult r = api.broadcast(built.hex());
        if (!r.ok) {
            throw new ApiException(ApiException.Kind.INVALID_TX, 0, ApiException.MSG_INVALID_TX);
        }
        return r.txid != null ? r.txid : built.txid();
    }

    public String send(String to, long amountSat) throws IOException {
        return send(to, amountSat, 0);
    }

    public ApiClient api() { return api; }
}
