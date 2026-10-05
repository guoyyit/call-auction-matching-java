package com.github.guoyyit.callauctionmatchingjava.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 一场集合竞价的撮合结果。
 *
 * @param equilibriumPriceTicks 最终撮合价(tick 数)；无成交时为 -1
 * @param matchedQuantity       总成交数量
 * @param unmatchedBuyQuantity  撮合价处剩余未成交买量
 * @param unmatchedSellQuantity 撮合价处剩余未成交卖量
 * @param pressureDirection     市场压力方向：1=买方剩余多(上行压力)，-1=卖方剩余多，0=平衡
 */
public record AuctionResult(
        String symbol,
        Phase phase,
        long equilibriumPriceTicks,
        long matchedQuantity,
        long unmatchedBuyQuantity,
        long unmatchedSellQuantity,
        int pressureDirection,
        boolean priceAdjustedByLimit,
        List<Trade> trades
) {

    public AuctionResult {
        trades = trades == null ? new ArrayList<>() : trades;
    }

    public static AuctionResult noTrade(String symbol, Phase phase) {
        return new AuctionResult(symbol, phase, -1L, 0L, 0L, 0L, 0, false, new ArrayList<>());
    }
}
