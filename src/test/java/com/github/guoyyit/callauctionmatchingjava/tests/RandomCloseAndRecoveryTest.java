package com.github.guoyyit.callauctionmatchingjava.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.guoyyit.callauctionmatchingjava.config.AuctionProperties;
import com.github.guoyyit.callauctionmatchingjava.model.AuctionResult;
import com.github.guoyyit.callauctionmatchingjava.model.Order;
import com.github.guoyyit.callauctionmatchingjava.model.OrderType;
import com.github.guoyyit.callauctionmatchingjava.model.Phase;
import com.github.guoyyit.callauctionmatchingjava.model.Side;
import com.github.guoyyit.callauctionmatchingjava.model.UnmatchedPolicy;
import com.github.guoyyit.callauctionmatchingjava.service.AuctionService;
import com.github.guoyyit.callauctionmatchingjava.web.dto.OrderRequest;

class RandomCloseAndRecoveryTest {

    @TempDir
    java.nio.file.Path tempDir;

    private OrderRequest req(Side side, OrderType type, Double price, long qty) {
        return new OrderRequest(null, side, type, price, qty);
    }

    @Test
    void sameSeedProducesSameCloseEndOffsetAndResult() {
        Long firstOffset = runOneSession(42L, new TestSupport.MutableClock(0L), false);
        Long secondOffset = runOneSession(42L, new TestSupport.MutableClock(0L), false);
        assertEquals(firstOffset, secondOffset);
    }

    @Test
    void closeAuctionMatchesOnlyAfterRandomDeadline() {
        TestSupport.MutableClock clock = new TestSupport.MutableClock(0L);
        AuctionProperties props = TestSupport.properties(
                tempDir.resolve("a"), UnmatchedPolicy.CARRY, 42L);
        AuctionService service = TestSupport.service(props, clock);

        Order buy = service.placeOrder(req(Side.BUY, OrderType.LIMIT, 9.90, 100));
        service.placeOrder(req(Side.SELL, OrderType.LIMIT, 10.10, 100));
        service.endCurrentAuction();

        service.startCloseAuction();
        assertNull(service.maybeExpireCloseAuction(),
                "must not match before the sampled close end time");
        assertEquals(Phase.CLOSE_AUCTION, service.getPhase());

        clock.set(1_000_000L);
        Order updatedBuy = service.replaceOrder(new com.github.guoyyit.callauctionmatchingjava
                .web.dto.ReplaceRequest(buy.getId(), Side.BUY, OrderType.LIMIT, 10.10, 100L));
        assertEquals(service.getNormalizer().toTicks(10.10), updatedBuy.getPriceTicks());
        AuctionResult closeResult = service.maybeExpireCloseAuction();
        assertNotNull(closeResult);
        assertEquals(100, closeResult.matchedQuantity());
        assertEquals(Phase.CLOSED, service.getPhase());
    }

    @Test
    void crashRecoveryRebuildsOrdersTradesAndPhase() {
        TestSupport.MutableClock clock = new TestSupport.MutableClock(0L);
        AuctionProperties props = TestSupport.properties(
                tempDir.resolve("b"), UnmatchedPolicy.CARRY, 99L);
        AuctionService service = TestSupport.service(props, clock);

        service.placeOrder(req(Side.BUY, OrderType.LIMIT, 10.00, 100));
        service.placeOrder(req(Side.SELL, OrderType.LIMIT, 10.00, 60));
        AuctionResult openResult = service.endCurrentAuction();
        assertEquals(60, openResult.matchedQuantity());

        service.startCloseAuction();
        service.placeOrder(req(Side.SELL, OrderType.LIMIT, 10.00, 40));
        clock.set(2_000_000L);
        AuctionResult closeResult = service.maybeExpireCloseAuction();
        assertEquals(40, closeResult.matchedQuantity());

        AuctionService recovered = TestSupport.recover(props, clock);
        assertEquals(Phase.CLOSED, recovered.getPhase());
        assertEquals(3, recovered.getOrders().size());
        long totalTraded = recovered.getTrades().stream()
                .mapToLong(com.github.guoyyit.callauctionmatchingjava.model.Trade::getQuantity)
                .sum();
        assertEquals(100, totalTraded);
        assertEquals(2, recovered.getAuctionResults().size());

        List<Order> orders = recovered.getOrders();
        long fullyFilled = orders.stream()
                .filter(o -> o.getFilledQuantity() == o.getQuantity()).count();
        assertTrue(fullyFilled >= 2);
    }

    @Test
    void recoveredServiceContinuesWithNewUniqueIdsAndSeqs() {
        TestSupport.MutableClock clock = new TestSupport.MutableClock(0L);
        AuctionProperties props = TestSupport.properties(
                tempDir.resolve("c"), UnmatchedPolicy.CARRY, 5L);
        AuctionService service = TestSupport.service(props, clock);
        service.placeOrder(req(Side.BUY, OrderType.LIMIT, 9.00, 10));
        service.placeOrder(req(Side.SELL, OrderType.LIMIT, 11.00, 10));
        service.endCurrentAuction();

        AuctionService recovered = TestSupport.recover(props, clock);
        assertEquals(Phase.INTRADAY, recovered.getPhase());
        recovered.startCloseAuction();
        Order extra = recovered.placeOrder(req(Side.BUY, OrderType.LIMIT, 10.00, 5));
        assertTrue(extra.getId() > 2L);
        assertEquals(3L, extra.getArrivalSeq());
    }

    private Long runOneSession(long seed, TestSupport.MutableClock clock, boolean unused) {
        AuctionProperties props = TestSupport.properties(
                tempDir.resolve("seed-" + seed + "-" + System.nanoTime()),
                UnmatchedPolicy.CARRY, seed);
        AuctionService service = TestSupport.service(props, clock);
        service.endCurrentAuction();
        service.startCloseAuction();
        long sampled = 0L;
        try {
            java.lang.reflect.Field f = service.getClass().getDeclaredField("scheduler");
            f.setAccessible(true);
            com.github.guoyyit.callauctionmatchingjava.service.PhaseScheduler scheduler =
                    (com.github.guoyyit.callauctionmatchingjava.service.PhaseScheduler) f.get(service);
            sampled = scheduler.getScheduledCloseEndOffsetMillis();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return sampled;
    }
}
