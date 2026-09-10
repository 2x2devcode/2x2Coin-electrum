package com.x2x.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** BIP39 mnemonic generation, validation, and seed derivation (English wordlist). */
public final class Bip39 {
    private static volatile List<String> WORDS;
    private static volatile Map<String, Integer> INDEX;

    private Bip39() {}

    private static void load() {
        if (WORDS != null) return;
        synchronized (Bip39.class) {
            if (WORDS != null) return;
            List<String> w = new ArrayList<>(2048);
            try (InputStream in = Bip39.class.getResourceAsStream("bip39-english.txt");
                 BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) w.add(line);
                }
            } catch (IOException e) {
                throw new RuntimeException("cannot load bip39 wordlist", e);
            }
            if (w.size() != 2048) throw new IllegalStateException("wordlist size " + w.size());
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < w.size(); i++) idx.put(w.get(i), i);
            INDEX = idx;
            WORDS = w;
        }
    }

    /** strengthBits: 128 (12 words) ... 256 (24 words). */
    public static String generate(int strengthBits) {
        if (strengthBits % 32 != 0 || strengthBits < 128 || strengthBits > 256)
            throw new IllegalArgumentException("bad strength");
        byte[] entropy = new byte[strengthBits / 8];
        new SecureRandom().nextBytes(entropy);
        return fromEntropy(entropy);
    }

    public static String fromEntropy(byte[] entropy) {
        load();
        int cs = entropy.length * 8 / 32;
        byte[] hash = Hashes.sha256(entropy);
        StringBuilder bits = new StringBuilder();
        for (byte b : entropy) bits.append(pad(Integer.toBinaryString(b & 0xff), 8));
        for (int i = 0; i < cs; i++) bits.append(((hash[0] >> (7 - i)) & 1) == 1 ? '1' : '0');
        List<String> out = new ArrayList<>();
        for (int i = 0; i < bits.length(); i += 11)
            out.add(WORDS.get(Integer.parseInt(bits.substring(i, i + 11), 2)));
        return String.join(" ", out);
    }

    public static boolean isValid(String mnemonic) {
        load();
        String[] words = mnemonic.trim().toLowerCase().split("\\s+");
        if (words.length % 3 != 0 || words.length < 12 || words.length > 24) return false;
        StringBuilder bits = new StringBuilder();
        for (String w : words) {
            Integer idx = INDEX.get(w);
            if (idx == null) return false;
            bits.append(pad(Integer.toBinaryString(idx), 11));
        }
        int total = bits.length();
        int cs = total / 33;
        int ent = total - cs;
        if (ent % 8 != 0) return false;
        byte[] entropy = new byte[ent / 8];
        for (int i = 0; i < entropy.length; i++)
            entropy[i] = (byte) Integer.parseInt(bits.substring(i * 8, i * 8 + 8), 2);
        byte[] hash = Hashes.sha256(entropy);
        for (int i = 0; i < cs; i++) {
            int expect = (hash[0] >> (7 - i)) & 1;
            int actual = bits.charAt(ent + i) - '0';
            if (expect != actual) return false;
        }
        return true;
    }

    /** BIP39 seed = PBKDF2-HMAC-SHA512(mnemonic, "mnemonic"+passphrase, 2048, 64 bytes). */
    public static byte[] toSeed(String mnemonic, String passphrase) {
        return Hashes.pbkdf2HmacSha512(mnemonic.trim(),
                "mnemonic" + (passphrase == null ? "" : passphrase), 2048, 64);
    }

    private static String pad(String s, int len) {
        StringBuilder b = new StringBuilder();
        for (int i = s.length(); i < len; i++) b.append('0');
        return b.append(s).toString();
    }
}
