package com.x2x.core;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** BIP32 hierarchical deterministic key derivation over secp256k1. */
public final class Bip32 {
    public static final long HARDENED = 0x80000000L;

    private Bip32() {}

    public static final class Node {
        public final byte[] privKey;   // 32 bytes, or null for public-only nodes
        public final byte[] pubKey;    // 33-byte compressed
        public final byte[] chainCode; // 32 bytes
        public final int depth;
        public final int parentFingerprint;
        public final long childNumber;

        Node(byte[] priv, byte[] pub, byte[] cc, int depth, int pfp, long cn) {
            this.privKey = priv; this.pubKey = pub; this.chainCode = cc;
            this.depth = depth; this.parentFingerprint = pfp; this.childNumber = cn;
        }

        public boolean hasPrivate() { return privKey != null; }

        public byte[] fingerprint() { return Arrays.copyOf(Hashes.hash160(pubKey), 4); }

        public int fingerprintInt() {
            byte[] f = fingerprint();
            return ((f[0] & 0xff) << 24) | ((f[1] & 0xff) << 16) | ((f[2] & 0xff) << 8) | (f[3] & 0xff);
        }
    }

    public static Node fromSeed(byte[] seed) {
        byte[] I = Hashes.hmacSha512("Bitcoin seed".getBytes(StandardCharsets.UTF_8), seed);
        byte[] il = Arrays.copyOfRange(I, 0, 32);
        byte[] ir = Arrays.copyOfRange(I, 32, 64);
        BigInteger k = new BigInteger(1, il);
        if (k.signum() == 0 || k.compareTo(Secp256k1.N) >= 0)
            throw new IllegalStateException("invalid master key");
        return new Node(il, Secp256k1.publicFromPrivate(il, true), ir, 0, 0, 0);
    }

    public static Node deriveChild(Node parent, long index) {
        boolean hardened = (index & HARDENED) != 0;
        byte[] data = new byte[37];
        if (hardened) {
            if (!parent.hasPrivate())
                throw new IllegalStateException("cannot derive hardened child from public key");
            data[0] = 0x00;
            System.arraycopy(parent.privKey, 0, data, 1, 32);
        } else {
            System.arraycopy(parent.pubKey, 0, data, 0, 33);
        }
        data[33] = (byte) ((index >> 24) & 0xff);
        data[34] = (byte) ((index >> 16) & 0xff);
        data[35] = (byte) ((index >> 8) & 0xff);
        data[36] = (byte) (index & 0xff);

        byte[] I = Hashes.hmacSha512(parent.chainCode, data);
        byte[] il = Arrays.copyOfRange(I, 0, 32);
        byte[] ir = Arrays.copyOfRange(I, 32, 64);
        BigInteger ilNum = new BigInteger(1, il);
        if (ilNum.compareTo(Secp256k1.N) >= 0)
            throw new IllegalStateException("IL >= n (use next index)");

        if (parent.hasPrivate()) {
            BigInteger ki = ilNum.add(new BigInteger(1, parent.privKey)).mod(Secp256k1.N);
            if (ki.signum() == 0) throw new IllegalStateException("ki == 0 (use next index)");
            byte[] childPriv = Secp256k1.encode32(ki);
            return new Node(childPriv, Secp256k1.publicFromPrivate(childPriv, true), ir,
                    parent.depth + 1, parent.fingerprintInt(), index);
        } else {
            byte[] childPub = Secp256k1.pointAddTweak(parent.pubKey, ilNum, true);
            return new Node(null, childPub, ir, parent.depth + 1, parent.fingerprintInt(), index);
        }
    }

    /** Derive along a path such as "m/44'/0'/0'/0/0". */
    public static Node derivePath(Node root, String path) {
        String p = path.trim();
        if (p.equalsIgnoreCase("m")) return root;
        if (p.startsWith("m/") || p.startsWith("M/")) p = p.substring(2);
        Node node = root;
        for (String part : p.split("/")) {
            if (part.isEmpty()) continue;
            boolean hard = part.endsWith("'") || part.endsWith("h") || part.endsWith("H");
            String num = hard ? part.substring(0, part.length() - 1) : part;
            long idx = Long.parseLong(num);
            if (hard) idx |= HARDENED;
            node = deriveChild(node, idx);
        }
        return node;
    }
}
