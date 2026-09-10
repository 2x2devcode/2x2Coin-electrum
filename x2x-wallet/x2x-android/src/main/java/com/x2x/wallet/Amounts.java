package com.x2x.wallet;

import com.x2x.core.NetworkParameters;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Convert between satoshis and the display decimal ("X.XXXXXXXX"). */
public final class Amounts {
    private Amounts() {}

    public static String satToCoins(long sat) {
        return new BigDecimal(sat)
                .movePointLeft(NetworkParameters.DECIMALS)
                .setScale(NetworkParameters.DECIMALS, RoundingMode.DOWN)
                .toPlainString();
    }

    public static long coinsToSat(String coins) {
        return new BigDecimal(coins.trim())
                .movePointRight(NetworkParameters.DECIMALS)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }
}
