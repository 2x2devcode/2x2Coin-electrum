package com.x2x.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class TxBuilderTest {

    private TxBuilder.Spendable utxo(String privHex, String txid, long vout, long valueSat) {
        byte[] priv = Hex.decode(privHex);
        byte[] pub = Secp256k1.publicFromPrivate(priv, true);
        String addr = Address.p2pkhFromPublicKey(pub);
        return new TxBuilder.Spendable(txid, vout, valueSat, Address.p2pkhScript(addr), priv, pub);
    }

    @Test
    public void buildsSignsAndBalancesMultiInput() {
        List<TxBuilder.Spendable> utxos = new ArrayList<>();
        utxos.add(utxo("11".repeat(32), "aa".repeat(32), 0, 300_000_000L)); // 3 2X2
        utxos.add(utxo("22".repeat(32), "bb".repeat(32), 1, 250_000_000L)); // 2.5 2X2

        String dest = Address.p2pkhFromPublicKey(
                Secp256k1.publicFromPrivate(Hex.decode("33".repeat(32)), true));
        String change = Address.p2pkhFromPublicKey(
                Secp256k1.publicFromPrivate(Hex.decode("44".repeat(32)), true));

        // send 4 2X2 -> needs both inputs
        TxBuilder.Built b = TxBuilder.build(utxos, dest, 400_000_000L, 10_000L, change);

        assertEquals(2, b.numInputs);
        // value conservation: inputs == outputs + fee
        long inSum = 550_000_000L;
        long outSum = 0;
        for (Transaction.Output o : b.tx.outputs) outSum += o.value;
        assertEquals(inSum, outSum + b.feeSat);

        // there must be a change output (>= dust)
        assertTrue(b.changeSat > TxBuilder.DUST);
        assertEquals(2, b.tx.outputs.size());

        // every input signature must verify against its sighash
        for (int i = 0; i < b.tx.inputs.size(); i++) {
            Transaction.Input in = b.tx.inputs.get(i);
            byte[] hash = b.tx.sigHash(i, in.connectedScript, Transaction.SIGHASH_ALL);
            byte[] ss = in.scriptSig;
            int sigLen = ss[0] & 0xff;
            byte[] der = new byte[sigLen - 1];
            System.arraycopy(ss, 1, der, 0, sigLen - 1);
            byte[] pub = new byte[ss[1 + sigLen] & 0xff];
            System.arraycopy(ss, 2 + sigLen, pub, 0, pub.length);
            assertTrue("input " + i + " sig must verify", Secp256k1.verifyDer(hash, der, pub));
        }
        assertEquals(64, b.txid().length());
    }

    @Test
    public void builtTxUsesLaggedNTimeAndAbsorbsSubDustChange() {
        List<TxBuilder.Spendable> utxos = new ArrayList<>();
        // 3.00015 2X2 — after sending 3 and paying a 10k fee, leftover (~5k) is below dust.
        utxos.add(utxo("11".repeat(32), "aa".repeat(32), 0, 300_015_000L));
        String dest = Address.p2pkhFromPublicKey(
                Secp256k1.publicFromPrivate(Hex.decode("33".repeat(32)), true));
        String change = Address.p2pkhFromPublicKey(
                Secp256k1.publicFromPrivate(Hex.decode("44".repeat(32)), true));

        long before = System.currentTimeMillis() / 1000L;
        TxBuilder.Built b = TxBuilder.build(utxos, dest, 300_000_000L, 10_000L, change);
        long after = System.currentTimeMillis() / 1000L;

        assertTrue(b.tx.nTime <= after);
        assertTrue(b.tx.nTime <= before - NetworkParameters.TX_TIME_SAFETY_LAG_SECONDS + 2);
        assertEquals(0, b.changeSat);
        assertEquals(1, b.tx.outputs.size());
    }

    @Test
    public void coinNTimeFloorRaisesTxNTime() {
        byte[] priv = Hex.decode("11".repeat(32));
        byte[] pub = Secp256k1.publicFromPrivate(priv, true);
        String addr = Address.p2pkhFromPublicKey(pub);
        long recentCoin = System.currentTimeMillis() / 1000L - 10;
        List<TxBuilder.Spendable> utxos = new ArrayList<>();
        utxos.add(new TxBuilder.Spendable("aa".repeat(32), 0, 500_000_000L,
                Address.p2pkhScript(addr), priv, pub, recentCoin));
        String dest = Address.p2pkhFromPublicKey(
                Secp256k1.publicFromPrivate(Hex.decode("33".repeat(32)), true));

        TxBuilder.Built b = TxBuilder.build(utxos, dest, 100_000_000L, 10_000L, addr);
        assertEquals(recentCoin, b.tx.nTime);
    }

    @Test(expected = IllegalStateException.class)
    public void insufficientFundsThrows() {
        List<TxBuilder.Spendable> utxos = new ArrayList<>();
        utxos.add(utxo("11".repeat(32), "aa".repeat(32), 0, 1_000_000L));
        String dest = Address.p2pkhFromPublicKey(
                Secp256k1.publicFromPrivate(Hex.decode("33".repeat(32)), true));
        TxBuilder.build(utxos, dest, 500_000_000L, 10_000L, dest);
    }
}
