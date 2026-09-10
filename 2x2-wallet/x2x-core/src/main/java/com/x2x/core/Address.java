package com.x2x.core;

import java.util.Arrays;

/** 2x2 P2PKH address handling (legacy Base58Check; the chain has no segwit). */
public final class Address {
    private Address() {}

    public static String p2pkhFromPublicKey(byte[] pubKey) {
        return fromHash160(Hashes.hash160(pubKey), NetworkParameters.P2PKH_VERSION);
    }

    public static String fromHash160(byte[] h160, int version) {
        if (h160.length != 20) throw new IllegalArgumentException("hash160 must be 20 bytes");
        byte[] payload = new byte[21];
        payload[0] = (byte) version;
        System.arraycopy(h160, 0, payload, 1, 20);
        return Base58.encodeChecked(payload);
    }

    public static boolean isValid(String addr) {
        try {
            byte[] p = Base58.decodeChecked(addr);
            if (p.length != 21) return false;
            int v = p[0] & 0xff;
            return v == NetworkParameters.P2PKH_VERSION || v == NetworkParameters.P2SH_VERSION;
        } catch (Exception e) {
            return false;
        }
    }

    public static int version(String addr) {
        return Base58.decodeChecked(addr)[0] & 0xff;
    }

    public static byte[] hash160(String addr) {
        byte[] p = Base58.decodeChecked(addr);
        return Arrays.copyOfRange(p, 1, 21);
    }

    /** P2PKH scriptPubKey: OP_DUP OP_HASH160 &lt;20&gt; OP_EQUALVERIFY OP_CHECKSIG. */
    public static byte[] p2pkhScript(String addr) {
        byte[] h = hash160(addr);
        byte[] s = new byte[25];
        s[0] = (byte) 0x76; // OP_DUP
        s[1] = (byte) 0xa9; // OP_HASH160
        s[2] = (byte) 0x14; // push 20
        System.arraycopy(h, 0, s, 3, 20);
        s[23] = (byte) 0x88; // OP_EQUALVERIFY
        s[24] = (byte) 0xac; // OP_CHECKSIG
        return s;
    }
}
