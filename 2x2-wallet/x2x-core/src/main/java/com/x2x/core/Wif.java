package com.x2x.core;

import java.util.Arrays;

/** Wallet Import Format for 2x2 private keys (version 128, compressed by default). */
public final class Wif {
    private Wif() {}

    public static String encode(byte[] priv32, boolean compressed) {
        if (priv32.length != 32) throw new IllegalArgumentException("priv must be 32 bytes");
        byte[] payload = new byte[compressed ? 34 : 33];
        payload[0] = (byte) NetworkParameters.WIF_VERSION;
        System.arraycopy(priv32, 0, payload, 1, 32);
        if (compressed) payload[33] = 0x01;
        return Base58.encodeChecked(payload);
    }

    public static final class Decoded {
        public final byte[] key;
        public final boolean compressed;
        Decoded(byte[] key, boolean compressed) { this.key = key; this.compressed = compressed; }
    }

    public static Decoded decode(String wif) {
        byte[] p = Base58.decodeChecked(wif);
        if ((p[0] & 0xff) != NetworkParameters.WIF_VERSION)
            throw new IllegalArgumentException("bad WIF version byte");
        boolean compressed = p.length == 34 && p[33] == 0x01;
        if (!compressed && p.length != 33)
            throw new IllegalArgumentException("bad WIF length");
        return new Decoded(Arrays.copyOfRange(p, 1, 33), compressed);
    }
}
