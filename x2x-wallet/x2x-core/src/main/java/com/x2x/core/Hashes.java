package com.x2x.core;

import java.text.Normalizer;

import org.bouncycastle.crypto.PBEParametersGenerator;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

/** Hash / MAC / KDF helpers built on the Bouncy Castle lightweight API. */
public final class Hashes {
    private Hashes() {}

    public static byte[] sha256(byte[] in) {
        SHA256Digest d = new SHA256Digest();
        d.update(in, 0, in.length);
        byte[] out = new byte[32];
        d.doFinal(out, 0);
        return out;
    }

    /** Double SHA-256 — used for txids and Base58Check checksums. */
    public static byte[] sha256d(byte[] in) {
        return sha256(sha256(in));
    }

    public static byte[] ripemd160(byte[] in) {
        RIPEMD160Digest d = new RIPEMD160Digest();
        d.update(in, 0, in.length);
        byte[] out = new byte[20];
        d.doFinal(out, 0);
        return out;
    }

    /** RIPEMD160(SHA256(x)) — the standard "hash160" for addresses. */
    public static byte[] hash160(byte[] in) {
        return ripemd160(sha256(in));
    }

    public static byte[] hmacSha512(byte[] key, byte[] data) {
        HMac h = new HMac(new SHA512Digest());
        h.init(new KeyParameter(key));
        h.update(data, 0, data.length);
        byte[] out = new byte[64];
        h.doFinal(out, 0);
        return out;
    }

    /** PBKDF2-HMAC-SHA512, used by BIP39 to turn a mnemonic into a 64-byte seed. */
    public static byte[] pbkdf2HmacSha512(String password, String salt, int iterations, int dkLenBytes) {
        byte[] pw = Normalizer.normalize(password, Normalizer.Form.NFKD)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] sl = Normalizer.normalize(salt, Normalizer.Form.NFKD)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA512Digest());
        gen.init(pw, sl, iterations);
        KeyParameter key = (KeyParameter) gen.generateDerivedParameters(dkLenBytes * 8);
        return key.getKey();
    }
}
