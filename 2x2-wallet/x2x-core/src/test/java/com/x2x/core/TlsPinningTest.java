package com.x2x.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TlsPinningTest {

    @Test
    public void pinsOfIgnoresBlank() {
        assertEquals(1, TlsPinning.pinsOf("abc", "", null, "  ").size());
        assertTrue(TlsPinning.pinsOf("abc").contains("abc"));
    }

    @Test
    public void emptyPinsAllowAny() {
        // chainMatchesPin with empty set is false; ApiClient skips verify when empty.
        assertFalse(TlsPinning.pinsOf().contains("x"));
    }
}
