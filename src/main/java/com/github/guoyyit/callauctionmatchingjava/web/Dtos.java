package com.github.guoyyit.callauctionmatchingjava.web;

import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionKind;
import com.github.guoyyit.callauctionmatchingjava.core.model.Side;
import com.github.guoyyit.callauctionmatchingjava.core.model.UnfilledPolicy;

import java.math.BigDecimal;

public final class Dtos {

    private Dtos() {
    }

    public record OrderRequest(
            String clientOrderId,
            String symbol,
            Side side,
            BigDecimal limitPrice,
            boolean market,
            long quantity
    ) {
    }

    public record CancelRequest(
            String clientOrderId
    ) {
    }

    public record ReplaceRequest(
            String clientOrderId,
            BigDecimal limitPrice,
            boolean market,
            long quantity
    ) {
    }

    public record StartRequest(
            AuctionKind kind,
            BigDecimal referencePrice,
            UnfilledPolicy unfilledPolicy,
            Long fixedEndMillis
    ) {
    }
}
