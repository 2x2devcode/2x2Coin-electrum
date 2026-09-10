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

    @Test(expected = IllegalStateException.class)
    public void insufficientFundsThrows() {
        List<TxBuilder.Spendable> utxos = new ArrayList<>();
        utxos.add(utxo("11".repeat(32), "aa".repeat(32), 0, 1_000_000L));
        String dest = Address.p2pkhFromPublicKey(
                Secp256k1.publicFromPrivate(Hex.decode("33".repeat(32)), true));
        TxBuilder.build(utxos, dest, 500_000_000L, 10_000L, dest);
    }
}
