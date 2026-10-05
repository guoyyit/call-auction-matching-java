package com.github.guoyyit.callauctionmatchingjava.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.RoundingMode;

import org.junit.jupiter.api.Test;

import com.github.guoyyit.callauctionmatchingjava.engine.PriceLimit;
import com.github.guoyyit.callauctionmatchingjava.engine.PriceNormalizer;

class PriceNormalizerAndLimitTest {

    @Test
    void normalizesHalfUpToNearestTick() {
        PriceNormalizer normalizer = new PriceNormalizer(0.01);
        assertEquals(1001, normalizer.toTicks(10.0051));
        assertEquals(1000, normalizer.toTicks(10.004));
        assertEquals(1001, normalizer.toTicks(10.006));
        assertEquals(10.00, normalizer.toPriceDouble(1000), 0.0);
    }

    @Test
    void limitBoundsAreRoundedOutwardNeverRelaxed() {
        PriceNormalizer normalizer = new PriceNormalizer(0.01);
        PriceLimit limit = PriceLimit.of(normalizer, 10.00, 0.10, 0.10);
        assertEquals(9.00, normalizer.toPriceDouble(limit.getLowerTick()), 0.0);
        assertEquals(11.00, normalizer.toPriceDouble(limit.getUpperTick()), 0.0);
        assertTrue(limit.contains(limit.getLowerTick()));
        assertTrue(limit.contains(limit.getUpperTick()));
        assertEquals(1, limit.suppressionFlag(limit.getUpperTick() + 5));
        assertEquals(-1, limit.suppressionFlag(limit.getLowerTick() - 5));
        assertEquals(0, limit.suppressionFlag(limit.getLowerTick()));
        assertEquals(limit.getUpperTick(),
                limit.repriceSuppressed(limit.getUpperTick() + 100));
    }

    @Test
    void unboundedLimitNeverClamps() {
        PriceNormalizer normalizer = new PriceNormalizer(0.01);
        PriceLimit limit = PriceLimit.unbounded(normalizer);
        assertFalse(limit.isBounded());
        assertEquals(999_999L, limit.clamp(999_999L));
        assertEquals(0, limit.suppressionFlag(Long.MAX_VALUE - 1));
    }

    @Test
    void floorAndUpRoundingModesForLimitComputation() {
        PriceNormalizer normalizer = new PriceNormalizer(0.01);
        assertEquals(1100, normalizer.toTicks(11.009, RoundingMode.DOWN));
        assertEquals(901, normalizer.toTicks(9.001, RoundingMode.UP));
    }
}
