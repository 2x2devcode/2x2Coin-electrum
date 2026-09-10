package com.x2x.core;

import java.util.Arrays;

/** Base58 and Base58Check (Bitcoin alphabet). */
public final class Base58 {
    private static final char[] ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final int[] INDEXES = new int[128];

    static {
        Arrays.fill(INDEXES, -1);
        for (int i = 0; i < ALPHABET.length; i++) INDEXES[ALPHABET[i]] = i;
    }

    private Base58() {}

    public static String encode(byte[] input) {
        if (input.length == 0) return "";
        input = Arrays.copyOf(input, input.length);
        int zeros = 0;
        while (zeros < input.length && input[zeros] == 0) zeros++;
        char[] encoded = new char[input.length * 2];
        int outputStart = encoded.length;
        for (int inputStart = zeros; inputStart < input.length; ) {
            encoded[--outputStart] = ALPHABET[divmod(input, inputStart, 256, 58)];
            if (input[inputStart] == 0) inputStart++;
        }
        while (outputStart < encoded.length && encoded[outputStart] == ALPHABET[0]) outputStart++;
        while (--zeros >= 0) encoded[--outputStart] = ALPHABET[0];
        return new String(encoded, outputStart, encoded.length - outputStart);
    }

    public static byte[] decode(String input) {
        if (input.isEmpty()) return new byte[0];
        byte[] input58 = new byte[input.length()];
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            int digit = c < 128 ? INDEXES[c] : -1;
            if (digit < 0) throw new IllegalArgumentException("Invalid Base58 char: " + c);
            input58[i] = (byte) digit;
        }
        int zeros = 0;
        while (zeros < input58.length && input58[zeros] == 0) zeros++;
        byte[] decoded = new byte[input.length()];
        int outputStart = decoded.length;
        for (int inputStart = zeros; inputStart < input58.length; ) {
            decoded[--outputStart] = divmod(input58, inputStart, 58, 256);
            if (input58[inputStart] == 0) inputStart++;
        }
        while (outputStart < decoded.length && decoded[outputStart] == 0) outputStart++;
        return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.length);
    }

    private static byte divmod(byte[] number, int firstDigit, int base, int divisor) {
        int remainder = 0;
        for (int i = firstDigit; i < number.length; i++) {
            int digit = number[i] & 0xFF;
            int temp = remainder * base + digit;
            number[i] = (byte) (temp / divisor);
            remainder = temp % divisor;
        }
        return (byte) remainder;
    }

    /** Encode payload with a 4-byte double-SHA256 checksum appended. */
    public static String encodeChecked(byte[] payload) {
        byte[] checksum = Hashes.sha256d(payload);
        byte[] out = new byte[payload.length + 4];
        System.arraycopy(payload, 0, out, 0, payload.length);
        System.arraycopy(checksum, 0, out, payload.length, 4);
        return encode(out);
    }

    /** Decode and verify a Base58Check string, returning the payload (without checksum). */
    public static byte[] decodeChecked(String input) {
        byte[] all = decode(input);
        if (all.length < 4) throw new IllegalArgumentException("Base58Check too short");
        byte[] payload = Arrays.copyOfRange(all, 0, all.length - 4);
        byte[] checksum = Arrays.copyOfRange(all, all.length - 4, all.length);
        byte[] expected = Arrays.copyOf(Hashes.sha256d(payload), 4);
        if (!Arrays.equals(checksum, expected))
            throw new IllegalArgumentException("Base58Check checksum mismatch");
        return payload;
    }
}
