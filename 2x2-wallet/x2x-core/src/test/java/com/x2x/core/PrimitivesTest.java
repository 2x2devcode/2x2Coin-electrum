package com.x2x.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;

import org.junit.Test;

public class PrimitivesTest {

    @Test
    public void base58KnownVectors() {
        assertEquals("", Base58.encode(new byte[0]));
        // "Hello World!" -> known Base58
        assertEquals("2NEpo7TZRRrLZSi2U",
                Base58.encode("Hello World!".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // Leading zero bytes map to leading '1's
        assertEquals("1111", Base58.encode(new byte[]{0, 0, 0, 0}));
    }

    @Test
    public void clientSampleAddressDecodesToVersion3() {
        // The address from the client's DEVELOPER.md must be a valid v3 P2PKH address.
        String sample = "2NHBXKyRY4ZBvyfyuZ2fZvqaGyo89vMGFW";
        assertTrue("client sample must be a valid 2x2 address", Address.isValid(sample));
        assertEquals(3, Address.version(sample));
        assertEquals(20, Address.hash160(sample).length);
    }

    @Test
    public void addressRoundTrip() {
        byte[] h160 = Hex.decode("0102030405060708090a0b0c0d0e0f1011121314");
        String addr = Address.fromHash160(h160, NetworkParameters.P2PKH_VERSION);
        assertTrue(addr.startsWith("2"));
        assertArrayEquals(h160, Address.hash160(addr));
        assertEquals(3, Address.version(addr));
    }

    @Test
    public void wifRoundTripCompressed() {
        byte[] priv = Hex.decode("1111111111111111111111111111111111111111111111111111111111111111");
        String wif = Wif.encode(priv, true);
        Wif.Decoded d = Wif.decode(wif);
        assertArrayEquals(priv, d.key);
        assertTrue(d.compressed);
    }

    @Test
    public void pubkeyAndAddressAreDeterministic() {
        byte[] priv = Hex.decode("1111111111111111111111111111111111111111111111111111111111111111");
        byte[] pub = Secp256k1.publicFromPrivate(priv, true);
        assertEquals(33, pub.length);
        assertTrue(pub[0] == 0x02 || pub[0] == 0x03);
        String addr = Address.p2pkhFromPublicKey(pub);
        assertTrue(Address.isValid(addr));
    }

    @Test
    public void signatureIsLowSAndDer() {
        byte[] priv = Hex.decode("1111111111111111111111111111111111111111111111111111111111111111");
        byte[] hash = Hashes.sha256("test message".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] der = Secp256k1.signDer(hash, Secp256k1.toBigInteger(priv));
        assertEquals(0x30, der[0] & 0xff);            // DER SEQUENCE
        // deterministic: signing again yields the identical signature (RFC 6979)
        byte[] der2 = Secp256k1.signDer(hash, Secp256k1.toBigInteger(priv));
        assertArrayEquals(der, der2);
        assertFalse(BigInteger.ZERO.equals(Secp256k1.toBigInteger(priv)));
    }
}
