package com.github.guoyyit.callauctionmatchingjava.core.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Ticks {

    private Ticks() {
    }

    public static long toTickIndex(BigDecimal price, BigDecimal tickSize) {
        if (price == null || tickSize == null) {
            throw new IllegalArgumentException("price and tickSize must not be null");
        }
        if (price.signum() < 0 || tickSize.signum() <= 0) {
            throw new IllegalArgumentException("price must be non-negative and tickSize positive");
        }
        return price.divide(tickSize, 0, RoundingMode.HALF_UP).longValueExact();
    }

    public static BigDecimal toPrice(long tickIndex, BigDecimal tickSize) {
        return tickSize.multiply(BigDecimal.valueOf(tickIndex));
    }

    public static long clamp(long tickIndex, long lowerTick, long upperTick) {
        if (lowerTick > upperTick) {
            throw new IllegalArgumentException("lowerTick must not exceed upperTick");
        }
        return Math.max(lowerTick, Math.min(upperTick, tickIndex));
    }
}
