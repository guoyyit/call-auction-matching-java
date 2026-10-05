package com.github.guoyyit.callauctionmatchingjava.core.model;

import java.util.List;

public record AuctionResult(
        String auctionId,
        AuctionKind kind,
        long referenceTick,
        Long priceTick,
        long matchedQuantity,
        long buyRemainingQuantity,
        long sellRemainingQuantity,
        long lowerLimitTick,
        long upperLimitTick,
        boolean priceAdjustedByLimit,
        long endTimestampMillis,
        List<Fill> fills,
        String note
) {
}
