package com.github.guoyyit.callauctionmatchingjava.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.github.guoyyit.callauctionmatchingjava.config.AuctionProperties;
import com.github.guoyyit.callauctionmatchingjava.engine.MatchingEngine;
import com.github.guoyyit.callauctionmatchingjava.engine.PriceLimit;
import com.github.guoyyit.callauctionmatchingjava.engine.PriceNormalizer;
import com.github.guoyyit.callauctionmatchingjava.model.AuctionResult;
import com.github.guoyyit.callauctionmatchingjava.model.Order;
import com.github.guoyyit.callauctionmatchingjava.model.OrderStatus;
import com.github.guoyyit.callauctionmatchingjava.model.OrderType;
import com.github.guoyyit.callauctionmatchingjava.model.Phase;
import com.github.guoyyit.callauctionmatchingjava.model.Side;

class MatchingEngineTest {

    private final PriceNormalizer normalizer = new PriceNormalizer(0.01);

    private Order order(long id, Side side, OrderType type, double price, long qty, long seq) {
        Order o = new Order();
        o.setId(id);
        o.setSymbol("600000");
        o.setSide(side);
        o.setType(type);
        o.setPriceTicks(type == OrderType.MARKET ? 0L : normalizer.toTicks(price));
        o.setQuantity(qty);
        o.setFilledQuantity(0L);
        o.setArrivalSeq(seq);
        o.setStatus(OrderStatus.NEW);
        return o;
    }

    private AuctionResult match(List<Order> orders, Long refTicks, PriceLimit limit,
                                AuctionProperties.TieBreakByPressure rule) {
        return MatchingEngine.match(orders, limit, refTicks, rule,
                Phase.OPEN_AUCTION, "600000", 1L, 1_000L);
    }

    @Test
    void maximizesMatchedQuantity() {
        List<Order> orders = List.of(
                order(1, Side.BUY, OrderType.LIMIT, 10.00, 100, 1),
                order(2, Side.SELL, OrderType.LIMIT, 9.90, 60, 2),
                order(3, Side.SELL, OrderType.LIMIT, 10.00, 100, 3));
        AuctionResult result = match(new ArrayList<>(orders), normalizer.toTicks(10.00),
                PriceLimit.unbounded(normalizer), AuctionProperties.TieBreakByPressure.STANDARD);
        assertEquals(normalizer.toTicks(10.00), result.equilibriumPriceTicks());
        assertEquals(100, result.matchedQuantity());
    }

    @Test
    void tieOnVolumeChoosesSmallerResidual() {
        // 10.00 与 10.01 均可成交 100；10.00 处买方需求 200/供给 100(残差100)，
        // 10.01 处恰好平衡(残差 0)，规则取残差最小的 10.01。
        List<Order> orders = List.of(
                order(1, Side.BUY, OrderType.LIMIT, 10.00, 100, 1),
                order(2, Side.BUY, OrderType.LIMIT, 10.01, 100, 2),
                order(3, Side.SELL, OrderType.LIMIT, 10.00, 100, 3));
        AuctionResult result = match(new ArrayList<>(orders), normalizer.toTicks(10.00),
                PriceLimit.unbounded(normalizer), AuctionProperties.TieBreakByPressure.STANDARD);
        assertEquals(100, result.matchedQuantity());
        assertEquals(normalizer.toTicks(10.01), result.equilibriumPriceTicks());
        assertEquals(0, result.unmatchedBuyQuantity() + result.unmatchedSellQuantity());
    }

    @Test
    void residualTieBuyPressurePicksPriceCloserToReferenceThenHigher() {
        List<Order> orders = List.of(
                order(1, Side.BUY, OrderType.LIMIT, 10.02, 100, 1),
                order(2, Side.BUY, OrderType.LIMIT, 9.98, 100, 2),
                order(3, Side.SELL, OrderType.LIMIT, 9.98, 50, 3),
                order(4, Side.SELL, OrderType.LIMIT, 10.02, 50, 4));
        AuctionResult result = match(new ArrayList<>(orders), normalizer.toTicks(10.00),
                PriceLimit.unbounded(normalizer), AuctionProperties.TieBreakByPressure.STANDARD);
        assertEquals(100, result.matchedQuantity());
        assertEquals(normalizer.toTicks(10.02), result.equilibriumPriceTicks());
    }

    @Test
    void sellPressurePicksLowerOnEqualDistance() {
        List<Order> orders = new ArrayList<>(List.of(
                order(1, Side.BUY, OrderType.LIMIT, 10.02, 50, 1),
                order(2, Side.BUY, OrderType.LIMIT, 9.98, 50, 2),
                order(3, Side.SELL, OrderType.LIMIT, 9.98, 100, 3),
                order(4, Side.SELL, OrderType.LIMIT, 10.02, 100, 4)));
        AuctionResult result = match(orders, normalizer.toTicks(10.00),
                PriceLimit.unbounded(normalizer), AuctionProperties.TieBreakByPressure.STANDARD);
        assertEquals(100, result.matchedQuantity());
        assertEquals(normalizer.toTicks(9.98), result.equilibriumPriceTicks());
    }

    @Test
    void marketOrdersTradeAtReferenceDrivenPrice() {
        List<Order> orders = List.of(
                order(1, Side.BUY, OrderType.MARKET, 0, 100, 1),
                order(2, Side.SELL, OrderType.LIMIT, 10.05, 100, 2));
        AuctionResult result = match(new ArrayList<>(orders), normalizer.toTicks(10.00),
                PriceLimit.unbounded(normalizer), AuctionProperties.TieBreakByPressure.STANDARD);
        assertEquals(normalizer.toTicks(10.05), result.equilibriumPriceTicks());
        assertEquals(100, result.matchedQuantity());
    }

    @Test
    void noCrossReturnsNoTrade() {
        List<Order> orders = List.of(
                order(1, Side.BUY, OrderType.LIMIT, 9.90, 100, 1),
                order(2, Side.SELL, OrderType.LIMIT, 10.00, 100, 2));
        AuctionResult result = match(new ArrayList<>(orders), normalizer.toTicks(10.00),
                PriceLimit.unbounded(normalizer), AuctionProperties.TieBreakByPressure.STANDARD);
        assertEquals(-1L, result.equilibriumPriceTicks());
        assertEquals(0, result.matchedQuantity());
        assertTrue(result.trades().isEmpty());
    }

    @Test
    void priceOutsideLimitIsClampedAndStillMatches() {
        List<Order> orders = List.of(
                order(1, Side.BUY, OrderType.LIMIT, 11.50, 100, 1),
                order(2, Side.SELL, OrderType.LIMIT, 11.00, 100, 2));
        PriceLimit limit = PriceLimit.of(normalizer, 10.00, 0.10, 0.10);
        AuctionResult result = match(new ArrayList<>(orders), normalizer.toTicks(10.00),
                limit, AuctionProperties.TieBreakByPressure.STANDARD);
        assertEquals(normalizer.toTicks(11.00), result.equilibriumPriceTicks());
        assertEquals(100, result.matchedQuantity());
    }

    @Test
    void fillsFollowArrivalOrder() {
        List<Order> orders = new ArrayList<>(List.of(
                order(1, Side.SELL, OrderType.LIMIT, 10.00, 30, 1),
                order(2, Side.SELL, OrderType.LIMIT, 10.00, 70, 2),
                order(3, Side.BUY, OrderType.LIMIT, 10.00, 100, 3)));
        AuctionResult result = match(orders, normalizer.toTicks(10.00),
                PriceLimit.unbounded(normalizer), AuctionProperties.TieBreakByPressure.STANDARD);
        assertEquals(2, result.trades().size());
        assertEquals(30, result.trades().get(0).getQuantity());
        assertEquals(1L, result.trades().get(0).getSellerOrderId());
        assertEquals(70, result.trades().get(1).getQuantity());
    }
}
