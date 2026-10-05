package com.github.guoyyit.callauctionmatchingjava.core.command;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionKind;
import com.github.guoyyit.callauctionmatchingjava.core.model.Side;
import com.github.guoyyit.callauctionmatchingjava.core.model.UnfilledPolicy;

import java.math.BigDecimal;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Command.PlaceOrder.class, name = "PLACE"),
        @JsonSubTypes.Type(value = Command.CancelOrder.class, name = "CANCEL"),
        @JsonSubTypes.Type(value = Command.ReplaceOrder.class, name = "REPLACE"),
        @JsonSubTypes.Type(value = Command.StartAuction.class, name = "START"),
        @JsonSubTypes.Type(value = Command.FinishAuction.class, name = "FINISH")
})
public sealed interface Command
        permits Command.PlaceOrder, Command.CancelOrder, Command.ReplaceOrder,
                Command.StartAuction, Command.FinishAuction {

    record PlaceOrder(
            String clientOrderId,
            String symbol,
            Side side,
            BigDecimal limitPrice,
            boolean market,
            long quantity
    ) implements Command {
    }

    record CancelOrder(
            String clientOrderId,
            String origClientOrderId
    ) implements Command {
    }

    record ReplaceOrder(
            String clientOrderId,
            String origClientOrderId,
            BigDecimal limitPrice,
            boolean market,
            long quantity
    ) implements Command {
    }

    record StartAuction(
            String auctionId,
            AuctionKind kind,
            long startTimestampMillis,
            BigDecimal tickSize,
            BigDecimal referencePrice,
            BigDecimal lowerLimitPrice,
            BigDecimal upperLimitPrice,
            UnfilledPolicy unfilledPolicy,
            long scheduledEndTimestampMillis
    ) implements Command {
    }

    record FinishAuction(
            long timestampMillis
    ) implements Command {
    }
}
