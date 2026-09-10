package com.x2x.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Signed transaction assembly, including the 2x2-specific nTime field. */
public class TransactionTest {

    private byte[] priv() {
        return Hex.decode("1111111111111111111111111111111111111111111111111111111111111111");
    }

    @Test
    public void serializationContainsNTime() {
        Transaction tx = new Transaction();
        tx.version = 1;
        tx.nTime = 0x5F5E1000L; // fixed value for a deterministic check
        String toAddr = Address.p2pkhFromPublicKey(Secp256k1.publicFromPrivate(priv(), true));
        tx.addInput("00".repeat(32), 0, Address.p2pkhScript(toAddr), 100_000_000L);
        tx.addOutputToAddress(90_000_000L, toAddr);

        byte[] raw = tx.serialize();
        // bytes 0-3 = version LE (01000000), bytes 4-7 = nTime LE (00105e5f)
        assertEquals("01000000", Hex.encode(new byte[]{raw[0], raw[1], raw[2], raw[3]}));
        assertEquals("00105e5f", Hex.encode(new byte[]{raw[4], raw[5], raw[6], raw[7]}));
    }

    @Test
    public void signedInputVerifiesAgainstSigHash() {
        byte[] pk = priv();
        byte[] pub = Secp256k1.publicFromPrivate(pk, true);
        String myAddr = Address.p2pkhFromPublicKey(pub);
        String dest = Address.p2pkhFromPublicKey(
                Secp256k1.publicFromPrivate(Hex.decode(
                        "2222222222222222222222222222222222222222222222222222222222222222"), true));

        Transaction tx = new Transaction();
        tx.nTime = 1_766_000_000L;
        byte[] prevSpk = Address.p2pkhScript(myAddr);
        tx.addInput("a1".repeat(32), 1, prevSpk, 500_000_000L);
        tx.addOutputToAddress(490_000_000L, dest); // 0.1 fee

        // capture the sighash that WILL be signed, then sign
        byte[] hash = tx.sigHash(0, prevSpk, Transaction.SIGHASH_ALL);
        tx.signInput(0, pk, pub);

        // scriptSig must be non-empty and the embedded signature must verify
        byte[] scriptSig = tx.inputs.get(0).scriptSig;
        assertTrue(scriptSig.length > 70);

        // parse: <len><sig(+hashtype)><len><pubkey>
        int sigLen = scriptSig[0] & 0xff;
        byte[] sigPlusType = new byte[sigLen];
        System.arraycopy(scriptSig, 1, sigPlusType, 0, sigLen);
        byte[] der = new byte[sigLen - 1]; // drop trailing sighash-type byte
        System.arraycopy(sigPlusType, 0, der, 0, sigLen - 1);
        int pkOff = 1 + sigLen;
        int pkLen = scriptSig[pkOff] & 0xff;
        byte[] embeddedPub = new byte[pkLen];
        System.arraycopy(scriptSig, pkOff + 1, embeddedPub, 0, pkLen);

        assertArrayEquals(pub, embeddedPub);
        assertEquals(Transaction.SIGHASH_ALL, sigPlusType[sigLen - 1] & 0xff);
        assertTrue("signature must verify against the nTime-inclusive sighash",
                Secp256k1.verifyDer(hash, der, pub));

        // txid must be stable and 32 bytes
        assertEquals(64, tx.txid().length());
    }
}
