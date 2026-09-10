package com.x2x.wallet;

/** @deprecated Use {@link com.x2x.core.Amounts} — kept for Android binary compatibility. */
@Deprecated
public final class Amounts {
    private Amounts() {}

    public static String satToCoins(long sat) {
        return com.x2x.core.Amounts.satToCoins(sat);
    }

    public static long coinsToSat(String coins) {
        return com.x2x.core.Amounts.coinsToSat(coins);
    }
}
