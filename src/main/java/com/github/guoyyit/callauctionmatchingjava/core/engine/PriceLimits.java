package com.github.guoyyit.callauctionmatchingjava.core.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record PriceLimits(long lowerTick, long upperTick) {

    public static PriceLimits from(BigDecimal referencePrice, BigDecimal tickSize, BigDecimal changePercent) {
        if (referencePrice == null || tickSize == null || changePercent == null) {
            throw new IllegalArgumentException("referencePrice, tickSize and changePercent must not be null");
        }
        if (referencePrice.signum() <= 0 || tickSize.signum() <= 0 || changePercent.signum() < 0) {
            throw new IllegalArgumentException("referencePrice and tickSize must be positive, changePercent non-negative");
        }
        BigDecimal factor = BigDecimal.ONE.subtract(changePercent);
        BigDecimal lowerRaw = referencePrice.multiply(factor);
        BigDecimal upperRaw = referencePrice.multiply(BigDecimal.ONE.add(changePercent));
        long lowerTick = lowerRaw.divide(tickSize, 0, RoundingMode.FLOOR).longValueExact();
        long upperTick = upperRaw.divide(tickSize, 0, RoundingMode.CEILING).longValueExact();
        return new PriceLimits(lowerTick, upperTick);
    }

    public boolean contains(long tick) {
        return tick >= lowerTick && tick <= upperTick;
    }
}
