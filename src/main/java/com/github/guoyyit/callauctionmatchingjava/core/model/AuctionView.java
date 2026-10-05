package com.github.guoyyit.callauctionmatchingjava.core.model;

import java.math.BigDecimal;

public record AuctionView(
        String auctionId,
        AuctionKind kind,
        Phase phase,
        BigDecimal tickSize,
        long referenceTick,
        long lowerLimitTick,
        long upperLimitTick,
        UnfilledPolicy unfilledPolicy,
        Long scheduledEndTimestampMillis,
        long activeOrderCount
) {
}
