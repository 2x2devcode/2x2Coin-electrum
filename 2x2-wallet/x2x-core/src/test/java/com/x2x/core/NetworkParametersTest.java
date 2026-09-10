package com.x2x.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Smoke test — validates the toolchain and the core constants. */
public class NetworkParametersTest {

    @Test
    public void coinConstants() {
        assertEquals("2X2", NetworkParameters.TICKER);
        assertEquals(3, NetworkParameters.P2PKH_VERSION);
        assertEquals(90, NetworkParameters.P2SH_VERSION);
        assertEquals(128, NetworkParameters.WIF_VERSION);
        assertTrue(NetworkParameters.TX_HAS_NTIME);
    }

    @Test
    public void bouncyCastleIsAvailable() {
        // Ensures the BC dependency resolved and loads on the JVM.
        org.bouncycastle.crypto.digests.SHA256Digest d =
                new org.bouncycastle.crypto.digests.SHA256Digest();
        assertEquals(32, d.getDigestSize());
    }
}
