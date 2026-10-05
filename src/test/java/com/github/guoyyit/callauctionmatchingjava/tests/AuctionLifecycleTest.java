package com.github.guoyyit.callauctionmatchingjava.tests;

import com.github.guoyyit.callauctionmatchingjava.core.command.Command;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionKind;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionResult;
import com.github.guoyyit.callauctionmatchingjava.core.model.Order;
import com.github.guoyyit.callauctionmatchingjava.core.model.OrderStatus;
import com.github.guoyyit.callauctionmatchingjava.core.model.Side;
import com.github.guoyyit.callauctionmatchingjava.core.model.UnfilledPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuctionLifecycleTest {

    @TempDir
    Path tempDir;

    private static Command.PlaceOrder limit(String id, Side side, String price, long quantity) {
        return new Command.PlaceOrder(id, "SEC", side, new BigDecimal(price), false, quantity);
    }

    @Test
    public void collectsCancelsReplacesAndMatchesAtSinglePrice() throws Exception {
        TestSupport.Context context = TestSupport.newContext(tempDir.resolve("a.log"), 1L);
        context.recover();
        var service = context.service();
        service.startAuction(AuctionKind.OPEN, new BigDecimal("10.00"),
                UnfilledPolicy.CANCEL, null);

        service.placeOrder("b1", limit("b1", Side.BUY, "10.05", 100L));
        service.placeOrder("s1", limit("s1", Side.SELL, "10.00", 40L));
        service.placeOrder("s2", limit("s2", Side.SELL, "10.02", 60L));
        service.placeOrder("b2", limit("b2", Side.BUY, "10.01", 1_000L));
        service.cancelOrder("CXL-b2", "b2");
        service.replaceOrder("s3", "s2", new Command.ReplaceOrder(
                "s3", "s2", new BigDecimal("10.01"), false, 30L));

        AuctionResult result = service.finishAuction();
        assertNotNull(result.priceTick());
        assertEquals(70L, result.matchedQuantity());
        assertEquals(900L, result.lowerLimitTick());
        assertEquals(1100L, result.upperLimitTick());
        assertTrue(service.results().get(0).fills().size() >= 3);
    }

    @Test
    public void ordersOutsideLimitBandAreClampedAndTraceable() throws Exception {
        TestSupport.Context context = TestSupport.newContext(tempDir.resolve("b.log"), 7L);
        context.recover();
        var service = context.service();
        service.startAuction(AuctionKind.OPEN, new BigDecimal("10.00"),
                UnfilledPolicy.CANCEL, null);

        Order aggressiveBuy = service.placeOrder("b1", limit("b1", Side.BUY, "12.00", 100L));
        service.placeOrder("s1", limit("s1", Side.SELL, "11.00", 100L));

        assertTrue(aggressiveBuy.isPriceClamped());
        assertEquals(1100L, aggressiveBuy.getLimitTick());
        assertEquals(1200L, aggressiveBuy.getOriginalLimitTick());

        AuctionResult result = service.finishAuction();
        assertEquals(1100L, result.priceTick());
    }

    @Test
    public void unfilledOrdersCarryOverToNextAuction() throws Exception {
        TestSupport.Context context = TestSupport.newContext(tempDir.resolve("c.log"), 2L);
        context.recover();
        var service = context.service();
        service.startAuction(AuctionKind.OPEN, new BigDecimal("10.00"),
                UnfilledPolicy.CARRY_OVER, null);
        service.placeOrder("b1", limit("b1", Side.BUY, "10.00", 100L));
        service.placeOrder("s1", limit("s1", Side.SELL, "10.20", 40L));
        service.finishAuction();

        service.startAuction(AuctionKind.CLOSE, null, UnfilledPolicy.CARRY_OVER,
                context.clock().get() + 60_000L);
        assertEquals(2, service.activeOrders().size());
        Order carriedSell = context.service().snapshotState().getBook().get("s1");
        assertEquals(OrderStatus.ACTIVE, carriedSell.getStatus());
        Order carriedBuy = context.service().snapshotState().getBook().get("b1");
        assertEquals(OrderStatus.ACTIVE, carriedBuy.getStatus());

        service.placeOrder("b2", limit("b2", Side.BUY, "10.20", 40L));
        AuctionResult result = service.finishAuction();
        assertEquals(40L, result.matchedQuantity());
    }

    @Test
    public void convertToLimitPolicyConvertsResidualMarketOrders() throws Exception {
        TestSupport.Context context = TestSupport.newContext(tempDir.resolve("d.log"), 3L);
        context.recover();
        var service = context.service();
        service.startAuction(AuctionKind.OPEN, new BigDecimal("10.00"),
                UnfilledPolicy.CONVERT_TO_LIMIT, null);
        service.placeOrder("b1", new Command.PlaceOrder("b1", "SEC", Side.BUY,
                null, true, 100L));
        service.placeOrder("s1", limit("s1", Side.SELL, "10.00", 60L));
        service.finishAuction();

        Order converted = context.service().snapshotState().getAllOrders().get("b1");
        assertEquals(OrderStatus.CONVERTED, converted.getStatus());
        assertEquals(1000L, converted.getLimitTick());
        assertFalse(converted.isMarket());
    }

    @Test
    public void convertToLimitUsesAuctionExecutionPrice() throws Exception {
        TestSupport.Context context = TestSupport.newContext(tempDir.resolve("e.log"), 6L);
        context.recover();
        var service = context.service();
        service.startAuction(AuctionKind.OPEN, new BigDecimal("10.00"),
                UnfilledPolicy.CONVERT_TO_LIMIT, null);
        service.placeOrder("b1", new Command.PlaceOrder("b1", "SEC", Side.BUY,
                null, true, 100L));
        service.placeOrder("s1", limit("s1", Side.SELL, "10.05", 60L));
        AuctionResult result = service.finishAuction();

        assertEquals(1005L, result.priceTick());
        Order converted = service.snapshotState().getAllOrders().get("b1");
        assertEquals(1005L, converted.getLimitTick());
        assertFalse(converted.isMarket());
    }
}
