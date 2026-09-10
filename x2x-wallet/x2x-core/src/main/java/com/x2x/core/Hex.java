package com.x2x.core;

/** Minimal hex codec. */
public final class Hex {
    private static final char[] H = "0123456789abcdef".toCharArray();

    private Hex() {}

    public static String encode(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(H[(x >> 4) & 0xf]);
            sb.append(H[x & 0xf]);
        }
        return sb.toString();
    }

    public static byte[] decode(String s) {
        if (s == null) throw new IllegalArgumentException("null hex");
        s = s.trim();
        int n = s.length();
        if ((n & 1) != 0) throw new IllegalArgumentException("odd-length hex");
        byte[] out = new byte[n / 2];
        for (int i = 0; i < n; i += 2) {
            int hi = Character.digit(s.charAt(i), 16);
            int lo = Character.digit(s.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("bad hex char");
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
