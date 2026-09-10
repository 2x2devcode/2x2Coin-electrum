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

    /** How many external+change addresses to scan for funds. */
    public int lookAhead = 12;

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

    /** Scan derived addresses and collect all spendable outputs with their keys. */
    public List<TxBuilder.Spendable> collectSpendable() throws IOException {
        List<TxBuilder.Spendable> out = new ArrayList<>();
        for (int c = 0; c < 2; c++) {
            for (int i = 0; i < lookAhead; i++) {
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
        long total = 0;
        for (TxBuilder.Spendable s : collectSpendable()) total += s.valueSat;
        return total;
    }

    public long feePerKb() throws IOException { return api.getFeePerKb(); }

    public TxBuilder.Built createTransaction(String to, long amountSat, int changeIndex)
            throws IOException {
        return TxBuilder.build(collectSpendable(), to, amountSat, api.getFeePerKb(),
                changeAddress(changeIndex));
    }

    public TxBuilder.Built createTransaction(String to, long amountSat) throws IOException {
        return createTransaction(to, amountSat, 0);
    }

    /** Build, sign and broadcast a payment; returns the txid. */
    public String send(String to, long amountSat, int changeIndex) throws IOException {
        TxBuilder.Built built = createTransaction(to, amountSat, changeIndex);
        ApiClient.BroadcastResult r = api.broadcast(built.hex());
        if (!r.ok) throw new IOException("broadcast failed: " + r.error);
        return r.txid != null ? r.txid : built.txid();
    }

    public String send(String to, long amountSat) throws IOException {
        return send(to, amountSat, 0);
    }

    public ApiClient api() { return api; }
}
