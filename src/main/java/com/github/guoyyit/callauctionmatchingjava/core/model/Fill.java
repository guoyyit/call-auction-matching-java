package com.github.guoyyit.callauctionmatchingjava.core.model;

public record Fill(
        String auctionId,
        String orderId,
        Side side,
        long quantity,
        long priceTick,
        long sequence
) {
}
