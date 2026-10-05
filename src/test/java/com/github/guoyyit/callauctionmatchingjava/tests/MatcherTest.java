package com.github.guoyyit.callauctionmatchingjava.tests;

import com.github.guoyyit.callauctionmatchingjava.core.engine.Matcher;
import com.github.guoyyit.callauctionmatchingjava.core.model.Order;
import com.github.guoyyit.callauctionmatchingjava.core.model.OrderStatus;
import com.github.guoyyit.callauctionmatchingjava.core.model.Side;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MatcherTest {

    private static Order limitOrder(String id, Side side, long price, long quantity, long seq) {
        Order order = new Order(id, "SEC", side, price, false, quantity, seq);
        order.setStatus(OrderStatus.ACTIVE);
        return order;
    }

    private static Order marketOrder(String id, Side side, long quantity, long seq) {
        Order order = new Order(id, "SEC", side, null, true, quantity, seq);
        order.setStatus(OrderStatus.ACTIVE);
        return order;
    }

    @Test
    public void matchesAtSinglePriceAndAllocatesPassiveSideByArrival() {
        List<Order> book = new ArrayList<>(List.of(
                limitOrder("s1", Side.SELL, 100L, 30L, 1L),
                limitOrder("s2", Side.SELL, 101L, 40L, 2L),
                marketOrder("b1", Side.BUY, 50L, 3L)
        ));
        Matcher.Outcome outcome = Matcher.match("AUC-1", book, 101L, 1L);
        assertEquals(50L, outcome.matchedQuantity());
        assertEquals(30L, outcome.filledQuantityByOrder().get("s1"));
        assertEquals(20L, outcome.filledQuantityByOrder().get("s2"));
        assertEquals(50L, outcome.filledQuantityByOrder().get("b1"));
        assertEquals(List.of("s1", "s2", "b1"),
                outcome.fills().stream().map(com.github.guoyyit.callauctionmatchingjava.core.model.Fill::orderId).toList());
        assertEquals(101L, outcome.fills().get(0).priceTick());
    }

    @Test
    public void fillSequencesAreContiguousAndStartAtProvidedValue() {
        List<Order> book = new ArrayList<>(List.of(
                marketOrder("b1", Side.BUY, 100L, 1L),
                marketOrder("s1", Side.SELL, 100L, 2L)
        ));
        Matcher.Outcome outcome = Matcher.match("AUC-1", book, 100L, 42L);
        assertEquals(100L, outcome.matchedQuantity());
        assertEquals(List.of(42L, 43L),
                outcome.fills().stream().map(com.github.guoyyit.callauctionmatchingjava.core.model.Fill::sequence).toList());
    }

    @Test
    public void producesNoFillsWhenNothingCanCross() {
        List<Order> book = new ArrayList<>(List.of(
                limitOrder("b1", Side.BUY, 99L, 100L, 1L),
                limitOrder("s1", Side.SELL, 101L, 100L, 2L)
        ));
        Matcher.Outcome outcome = Matcher.match("AUC-1", book, 100L, 1L);
        assertEquals(0L, outcome.matchedQuantity());
        assertEquals(0, outcome.fills().size());
    }
}
