package com.github.guoyyit.callauctionmatchingjava.tests;

import com.github.guoyyit.callauctionmatchingjava.core.engine.PriceSelector;
import com.github.guoyyit.callauctionmatchingjava.core.model.Order;
import com.github.guoyyit.callauctionmatchingjava.core.model.OrderStatus;
import com.github.guoyyit.callauctionmatchingjava.core.model.Side;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PriceSelectorTest {

    private static Order order(String id, Side side, long priceTick, long quantity, long seq) {
        Order order = new Order(id, "SEC", side, priceTick, false, quantity, seq);
        order.setStatus(OrderStatus.ACTIVE);
        return order;
    }

    private static List<Order> orders(Order... orders) {
        List<Order> list = new ArrayList<>();
        long seq = 1L;
        for (Order order : orders) {
            list.add(order);
            seq++;
        }
        return list;
    }

    @Test
    public void choosesPriceWithMaximumTradableVolume() {
        List<Order> book = orders(
                order("b1", Side.BUY, 102L, 100L, 1L),
                order("s1", Side.SELL, 98L, 100L, 2L),
                order("b2", Side.BUY, 100L, 50L, 3L),
                order("s2", Side.SELL, 100L, 50L, 4L)
        );
        PriceSelector.Selection selection = PriceSelector.select(book, 90L, 110L, 100L);
        assertEquals(100L, selection.priceTick());
        assertEquals(150L, selection.matchedQuantity());
    }

    @Test
    public void tieOnVolumeChoosesMinimumRemaining() {
        List<Order> book = orders(
                order("b", Side.BUY, 101L, 100L, 1L),
                order("s", Side.SELL, 99L, 100L, 2L)
        );
        PriceSelector.Selection selection = PriceSelector.select(book, 90L, 110L, 100L);
        assertEquals(100L, selection.priceTick());
    }

    @Test
    public void tieResolvesByBuyPressureTowardsHigherPrice() {
        List<Order> book = orders(
                order("b1", Side.BUY, 105L, 100L, 1L),
                order("b2", Side.BUY, 100L, 20L, 2L),
                order("s1", Side.SELL, 95L, 100L, 3L),
                order("s2", Side.SELL, 105L, 20L, 4L)
        );
        PriceSelector.Selection selection = PriceSelector.select(book, 90L, 110L, 100L);
        assertEquals(105L, selection.priceTick());
    }

    @Test
    public void tieResolvesBySellPressureTowardsLowerPrice() {
        List<Order> book = orders(
                order("s1", Side.SELL, 95L, 100L, 1L),
                order("s2", Side.SELL, 100L, 20L, 2L),
                order("b1", Side.BUY, 105L, 100L, 3L),
                order("b2", Side.BUY, 95L, 20L, 4L)
        );
        PriceSelector.Selection selection = PriceSelector.select(book, 90L, 110L, 100L);
        assertEquals(95L, selection.priceTick());
    }

    @Test
    public void neutralPressureTieResolvesByDistanceToReference() {
        List<Order> book = orders(
                order("b1", Side.BUY, 103L, 100L, 1L),
                order("b2", Side.BUY, 97L, 20L, 2L),
                order("s1", Side.SELL, 97L, 100L, 3L),
                order("s2", Side.SELL, 103L, 20L, 4L)
        );
        PriceSelector.Selection selection = PriceSelector.select(book, 90L, 110L, 90L);
        assertEquals(97L, selection.priceTick());
    }

    @Test
    public void noCrossingBookReturnsNoPrice() {
        List<Order> book = orders(
                order("b", Side.BUY, 95L, 100L, 1L),
                order("s", Side.SELL, 105L, 100L, 2L)
        );
        PriceSelector.Selection selection = PriceSelector.select(book, 90L, 110L, 100L);
        assertNull(selection.priceTick());
    }
}
