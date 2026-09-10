package com.x2x.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Coin selection + transaction assembly + signing (pure; no network). */
public final class TxBuilder {

    /** ~dust threshold in satoshis; change below this is dropped into the fee. */
    public static final long DUST = 1_000L;

    private TxBuilder() {}

    /** An unspent output together with the key that can spend it. */
    public static final class Spendable {
        public final String txid;
        public final long vout;
        public final long valueSat;
        public final byte[] scriptPubKey;
        public final byte[] priv;
        public final byte[] pub;

        public Spendable(String txid, long vout, long valueSat, byte[] scriptPubKey,
                         byte[] priv, byte[] pub) {
            this.txid = txid; this.vout = vout; this.valueSat = valueSat;
            this.scriptPubKey = scriptPubKey; this.priv = priv; this.pub = pub;
        }
    }

    public static final class Built {
        public final Transaction tx;
        public final long feeSat;
        public final long changeSat;
        public final int numInputs;
        Built(Transaction tx, long feeSat, long changeSat, int numInputs) {
            this.tx = tx; this.feeSat = feeSat; this.changeSat = changeSat; this.numInputs = numInputs;
        }
        public String hex() { return tx.toHex(); }
        public String txid() { return tx.txid(); }
    }

    /** Rough serialized size (bytes) of a legacy P2PKH tx incl. the 4-byte nTime. */
    public static long estimateSize(int nIn, int nOut) {
        return 10 + 4 + (long) nIn * 148 + (long) nOut * 34;
    }

    public static long feeFor(int nIn, int nOut, long feePerKb) {
        long size = estimateSize(nIn, nOut);
        long fee = (long) Math.ceil(size / 1000.0 * feePerKb);
        return Math.max(fee, feePerKb); // floor at one kB of fee (server min-relay safety)
    }

    /**
     * Build and sign a payment. Largest-first coin selection; adds a change output
     * back to {@code changeAddress} unless the change would be dust.
     */
    public static Built build(List<Spendable> available, String toAddress, long amountSat,
                              long feePerKb, String changeAddress) {
        if (!Address.isValid(toAddress)) throw new IllegalArgumentException("invalid destination address");
        if (amountSat <= 0) throw new IllegalArgumentException("amount must be positive");

        List<Spendable> sorted = new ArrayList<>(available);
        sorted.sort(Comparator.comparingLong((Spendable s) -> s.valueSat).reversed());

        List<Spendable> chosen = new ArrayList<>();
        long sum = 0;
        long fee = 0;
        boolean enough = false;
        for (Spendable s : sorted) {
            chosen.add(s);
            sum += s.valueSat;
            fee = feeFor(chosen.size(), 2, feePerKb); // assume change for selection
            if (sum >= amountSat + fee) { enough = true; break; }
        }
        if (!enough) throw new IllegalStateException("insufficient funds");

        long change = sum - amountSat - fee;
        boolean withChange = change > DUST;
        if (!withChange) {
            // no change output: recompute fee for a single output; absorb remainder as fee
            fee = feeFor(chosen.size(), 1, feePerKb);
            long remainder = sum - amountSat - fee;
            if (remainder < 0) throw new IllegalStateException("insufficient funds for fee");
            fee += remainder; // tip the dust to the miner
            change = 0;
        }

        Transaction tx = new Transaction();
        for (Spendable s : chosen) tx.addInput(s.txid, s.vout, s.scriptPubKey, s.valueSat);
        tx.addOutputToAddress(amountSat, toAddress);
        if (withChange) tx.addOutputToAddress(change, changeAddress);

        for (int i = 0; i < chosen.size(); i++) {
            Spendable s = chosen.get(i);
            tx.signInput(i, s.priv, s.pub);
        }
        return new Built(tx, fee, change, chosen.size());
    }
}
