package com.x2x.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Validates BIP39/BIP32 against the official reference test vectors. */
public class HdWalletTest {

    @Test
    public void bip39StandardVector() {
        // BIP39 vector: 16 zero bytes of entropy.
        String mnemonic = Bip39.fromEntropy(Hex.decode("00000000000000000000000000000000"));
        assertEquals("abandon abandon abandon abandon abandon abandon abandon abandon "
                + "abandon abandon abandon about", mnemonic);
        assertTrue(Bip39.isValid(mnemonic));
        // seed with passphrase "TREZOR" (official vector)
        byte[] seed = Bip39.toSeed(mnemonic, "TREZOR");
        assertEquals("c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e5349553"
                + "1f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04",
                Hex.encode(seed));
    }

    @Test
    public void bip39RejectsBadChecksum() {
        assertFalse(Bip39.isValid("abandon abandon abandon abandon abandon abandon abandon "
                + "abandon abandon abandon abandon abandon")); // wrong last word
    }

    @Test
    public void bip32StandardVector1() {
        // BIP32 Test Vector 1: seed = 000102030405060708090a0b0c0d0e0f
        Bip32.Node m = Bip32.fromSeed(Hex.decode("000102030405060708090a0b0c0d0e0f"));
        assertEquals("e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35",
                Hex.encode(m.privKey));
        assertEquals("873dff81c02f525623fd1fe5167eac3a55a049de3d314bb42ee227ffed37d508",
                Hex.encode(m.chainCode));

        // m/0'
        Bip32.Node m0h = Bip32.derivePath(m, "m/0'");
        assertEquals("edb2e14f9ee77d26dd93b4ecede8d16ed408ce149b6cd80b0715a2d911a0afea",
                Hex.encode(m0h.privKey));

        // m/0'/1
        Bip32.Node m0h1 = Bip32.derivePath(m, "m/0'/1");
        assertEquals("3c6cb8d0f6a264c91ea8b5030fadaa8e538b020f0a387421a12de9319dc93368",
                Hex.encode(m0h1.privKey));
    }

    @Test
    public void end2endAddressFromMnemonic() {
        String mnemonic = Bip39.generate(128);
        assertTrue(Bip39.isValid(mnemonic));
        byte[] seed = Bip39.toSeed(mnemonic, "");
        Bip32.Node acct = Bip32.derivePath(Bip32.fromSeed(seed),
                NetworkParameters.defaultAccountPath() + "/0/0");
        String addr = Address.p2pkhFromPublicKey(acct.pubKey);
        assertTrue(addr.startsWith("2"));
        assertTrue(Address.isValid(addr));
    }
}
