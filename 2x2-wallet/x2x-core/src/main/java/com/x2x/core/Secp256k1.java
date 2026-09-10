package com.x2x.core;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.math.ec.ECPoint;

/** secp256k1 key operations: pubkey derivation, point tweak (for BIP32), and
 *  deterministic (RFC 6979) low-S ECDSA signing with DER output. */
public final class Secp256k1 {
    private static final X9ECParameters PARAMS = SECNamedCurves.getByName("secp256k1");
    public static final ECDomainParameters CURVE =
            new ECDomainParameters(PARAMS.getCurve(), PARAMS.getG(), PARAMS.getN(), PARAMS.getH());
    public static final BigInteger N = CURVE.getN();
    private static final BigInteger HALF_N = N.shiftRight(1);

    private Secp256k1() {}

    public static BigInteger toBigInteger(byte[] b) {
        return new BigInteger(1, b);
    }

    /** 32-byte big-endian encoding of a private key scalar. */
    public static byte[] encode32(BigInteger d) {
        byte[] tmp = d.toByteArray();
        if (tmp.length == 32) return tmp;
        byte[] out = new byte[32];
        if (tmp.length > 32) {
            System.arraycopy(tmp, tmp.length - 32, out, 0, 32);
        } else {
            System.arraycopy(tmp, 0, out, 32 - tmp.length, tmp.length);
        }
        return out;
    }

    /** Public key point for a private scalar, SEC1-encoded (compressed by default). */
    public static byte[] publicFromPrivate(BigInteger d, boolean compressed) {
        ECPoint q = CURVE.getG().multiply(d).normalize();
        return q.getEncoded(compressed);
    }

    public static byte[] publicFromPrivate(byte[] priv32, boolean compressed) {
        return publicFromPrivate(toBigInteger(priv32), compressed);
    }

    /** Decode a compressed/uncompressed SEC1 public key to a curve point. */
    public static ECPoint decodePoint(byte[] enc) {
        return CURVE.getCurve().decodePoint(enc);
    }

    /** BIP32 public parent -> public child: point = parentPoint + tweak*G. */
    public static byte[] pointAddTweak(byte[] parentPubEnc, BigInteger tweak, boolean compressed) {
        ECPoint parent = decodePoint(parentPubEnc);
        ECPoint child = CURVE.getG().multiply(tweak).add(parent).normalize();
        if (child.isInfinity()) throw new IllegalStateException("invalid child (infinity)");
        return child.getEncoded(compressed);
    }

    /**
     * Deterministic low-S ECDSA signature over a 32-byte hash, DER-encoded.
     * This is exactly what Bitcoin-family consensus requires for scriptSig.
     */
    public static byte[] signDer(byte[] hash32, BigInteger priv) {
        ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
        signer.init(true, new ECPrivateKeyParameters(priv, CURVE));
        BigInteger[] sig = signer.generateSignature(hash32);
        BigInteger r = sig[0];
        BigInteger s = sig[1];
        if (s.compareTo(HALF_N) > 0) s = N.subtract(s); // enforce low-S (BIP62)
        return derEncode(r, s);
    }

    /** Verify a DER ECDSA signature over a 32-byte hash against a SEC1 public key. */
    public static boolean verifyDer(byte[] hash32, byte[] der, byte[] pubKey) {
        try {
            ASN1Sequence seq = ASN1Sequence.getInstance(der);
            BigInteger r = ((ASN1Integer) seq.getObjectAt(0)).getValue();
            BigInteger s = ((ASN1Integer) seq.getObjectAt(1)).getValue();
            ECDSASigner signer = new ECDSASigner();
            signer.init(false, new ECPublicKeyParameters(decodePoint(pubKey), CURVE));
            return signer.verifySignature(hash32, r, s);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] derEncode(BigInteger r, BigInteger s) {
        try {
            byte[] rb = toUnsignedMinimal(r);
            byte[] sb = toUnsignedMinimal(s);
            ByteArrayOutputStream seq = new ByteArrayOutputStream();
            seq.write(0x02); seq.write(rb.length); seq.write(rb);
            seq.write(0x02); seq.write(sb.length); seq.write(sb);
            byte[] body = seq.toByteArray();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(0x30); out.write(body.length); out.write(body);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Minimal signed big-endian (DER integer) encoding of a positive value. */
    private static byte[] toUnsignedMinimal(BigInteger v) {
        byte[] b = v.toByteArray(); // already minimal two's complement, may have leading 0x00
        return b;
    }
}
