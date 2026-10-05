package com.github.guoyyit.callauctionmatchingjava.tests;

import com.github.guoyyit.callauctionmatchingjava.core.engine.PriceLimits;
import com.github.guoyyit.callauctionmatchingjava.core.engine.Ticks;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TicksTest {

    private final BigDecimal tickSize = new BigDecimal("0.01");

    @Test
    public void normalizesPricesToLegalTicks() {
        assertEquals(1000L, Ticks.toTickIndex(new BigDecimal("10.00"), tickSize));
        assertEquals(1000L, Ticks.toTickIndex(new BigDecimal("10.004"), tickSize));
        assertEquals(1001L, Ticks.toTickIndex(new BigDecimal("10.005"), tickSize));
        assertEquals(new BigDecimal("10.00"), Ticks.toPrice(1000L, tickSize));
    }

    @Test
    public void computesLimitBandRoundedOutward() {
        PriceLimits limits = PriceLimits.from(new BigDecimal("10.00"), tickSize, new BigDecimal("0.10"));
        assertEquals(900L, limits.lowerTick());
        assertEquals(1100L, limits.upperTick());
        assertTrue(limits.contains(1000L));
        assertFalse(limits.contains(899L));
        assertFalse(limits.contains(1101L));
    }

    @Test
    public void limitBandRoundsOutwardWhenBoundaryIsNotOnTick() {
        PriceLimits limits = PriceLimits.from(new BigDecimal("10.05"), tickSize, new BigDecimal("0.10"));
        assertEquals(904L, limits.lowerTick());
        assertEquals(1106L, limits.upperTick());
    }

    @Test
    public void clampsToLegalBand() {
        assertEquals(900L, Ticks.clamp(800L, 900L, 1100L));
        assertEquals(1100L, Ticks.clamp(1200L, 900L, 1100L));
        assertEquals(1000L, Ticks.clamp(1000L, 900L, 1100L));
    }
}
