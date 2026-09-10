package com.x2x.core;

import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * TLS public-key (SPKI) pinning for {@link HttpsURLConnection}.
 *
 * <p>Pins are SHA-256 hashes of the certificate SubjectPublicKeyInfo, encoded as
 * standard Base64 (same format as OkHttp {@code sha256/...} pins without the prefix).
 */
public final class TlsPinning {
    private TlsPinning() {}

    /** Build an {@link SSLSocketFactory} that performs normal PKIX trust then pin checks. */
    public static SSLSocketFactory socketFactory(Set<String> base64Sha256Pins) {
        try {
            X509TrustManager system = systemTrustManager();
            X509TrustManager pinned = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    system.checkClientTrusted(chain, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    system.checkServerTrusted(chain, authType);
                    if (base64Sha256Pins == null || base64Sha256Pins.isEmpty()) return;
                    if (!chainMatchesPin(chain, base64Sha256Pins)) {
                        throw new CertificateException("certificate pin mismatch");
                    }
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return system.getAcceptedIssuers();
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] { pinned }, null);
            return ctx.getSocketFactory();
        } catch (CertificateException e) {
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException("TLS pin factory init failed", e);
        }
    }

    /** Verify peer certificates already negotiated on a connection (defense in depth). */
    public static void verifyConnection(HttpsURLConnection conn, Set<String> base64Sha256Pins)
            throws SSLPeerUnverifiedException {
        if (base64Sha256Pins == null || base64Sha256Pins.isEmpty()) return;
        try {
            Certificate[] chain = conn.getServerCertificates();
            X509Certificate[] x509 = new X509Certificate[chain.length];
            for (int i = 0; i < chain.length; i++) {
                if (!(chain[i] instanceof X509Certificate)) {
                    throw new SSLPeerUnverifiedException("non-X509 certificate in chain");
                }
                x509[i] = (X509Certificate) chain[i];
            }
            if (!chainMatchesPin(x509, base64Sha256Pins)) {
                throw new SSLPeerUnverifiedException("certificate pin mismatch");
            }
        } catch (SSLPeerUnverifiedException e) {
            throw e;
        } catch (Exception e) {
            throw new SSLPeerUnverifiedException("pin verify failed: " + e.getMessage());
        }
    }

    public static boolean chainMatchesPin(X509Certificate[] chain, Set<String> pins) {
        for (X509Certificate cert : chain) {
            String spki = spkiSha256Base64(cert);
            if (pins.contains(spki)) return true;
        }
        return false;
    }

    public static String spkiSha256Base64(X509Certificate cert) {
        try {
            byte[] spki = cert.getPublicKey().getEncoded();
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(spki);
            return base64(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static Set<String> pinsOf(String... base64Pins) {
        Set<String> out = new HashSet<>();
        if (base64Pins != null) {
            for (String p : base64Pins) {
                if (p != null && !p.trim().isEmpty()) out.add(p.trim());
            }
        }
        return Collections.unmodifiableSet(out);
    }

    private static X509TrustManager systemTrustManager() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((java.security.KeyStore) null);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) return (X509TrustManager) tm;
        }
        throw new IllegalStateException("No system X509TrustManager");
    }

    private static String base64(byte[] data) {
        // Prefer java.util.Base64 (Android API 24+ / JVM 8+).
        return java.util.Base64.getEncoder().encodeToString(data);
    }
}
