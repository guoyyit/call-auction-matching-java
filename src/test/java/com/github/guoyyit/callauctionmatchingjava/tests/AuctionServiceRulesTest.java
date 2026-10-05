package com.github.guoyyit.callauctionmatchingjava.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.guoyyit.callauctionmatchingjava.config.AuctionProperties;
import com.github.guoyyit.callauctionmatchingjava.model.AuctionResult;
import com.github.guoyyit.callauctionmatchingjava.model.ImbalanceSnapshot;
import com.github.guoyyit.callauctionmatchingjava.model.Order;
import com.github.guoyyit.callauctionmatchingjava.model.OrderStatus;
import com.github.guoyyit.callauctionmatchingjava.model.OrderType;
import com.github.guoyyit.callauctionmatchingjava.model.Phase;
import com.github.guoyyit.callauctionmatchingjava.model.Side;
import com.github.guoyyit.callauctionmatchingjava.model.UnmatchedPolicy;
import com.github.guoyyit.callauctionmatchingjava.service.AuctionService;
import com.github.guoyyit.callauctionmatchingjava.web.dto.OrderRequest;

class AuctionServiceRulesTest {

    @TempDir
    Path tempDir;

    private OrderRequest req(Side side, OrderType type, Double price, long qty) {
        return new OrderRequest(null, side, type, price, qty);
    }

    @Test
    void suppressedOrdersAreRepricedAndTraceable() {
        AuctionProperties props = TestSupport.properties(tempDir, UnmatchedPolicy.CARRY, 1L);
        AuctionService service = TestSupport.service(props);

        Order overpricedBuy = service.placeOrder(
                req(Side.BUY, OrderType.LIMIT, 11.50, 100));
        assertTrue(overpricedBuy.isPriceLimited());
        assertEquals(service.getNormalizer().toTicks(11.00), overpricedBuy.getPriceTicks());

        Order underpricedSell = service.placeOrder(
                req(Side.SELL, OrderType.LIMIT, 8.50, 100));
        assertTrue(underpricedSell.isPriceLimited());
        assertEquals(service.getNormalizer().toTicks(9.00), underpricedSell.getPriceTicks());
    }

    @Test
    void cancelAndReplaceKeepTraceability() {
        AuctionProperties props = TestSupport.properties(tempDir, UnmatchedPolicy.CARRY, 1L);
        AuctionService service = TestSupport.service(props);

        Order first = service.placeOrder(req(Side.BUY, OrderType.LIMIT, 10.00, 100));
        Order second = service.replaceOrder(new com.github.guoyyit.callauctionmatchingjava
                .web.dto.ReplaceRequest(first.getId(), Side.BUY, OrderType.LIMIT, 10.01, 120L));

        assertEquals(OrderStatus.REPLACED, service.getOrders().get(0).getStatus());
        assertEquals(second.getId(), service.getOrders().get(0).getReplacedBy());
        assertEquals(first.getId(), second.getReplaces());

        Order cancelled = service.placeOrder(req(Side.SELL, OrderType.LIMIT, 10.01, 50));
        service.cancelOrder(cancelled.getId());
        assertEquals(OrderStatus.CANCELLED,
                service.getOrders().stream().filter(o -> o.getId() == cancelled.getId())
                        .findFirst().orElseThrow().getStatus());
    }

    @Test
    void unmatchedOrdersCarryIntoNextAuction() {
        AuctionProperties props = TestSupport.properties(tempDir, UnmatchedPolicy.CARRY, 1L);
        AuctionService service = TestSupport.service(props);

        service.placeOrder(req(Side.BUY, OrderType.LIMIT, 9.50, 100));
        service.placeOrder(req(Side.SELL, OrderType.LIMIT, 10.50, 100));
        AuctionResult openResult = service.endCurrentAuction();
        assertEquals(0, openResult.matchedQuantity());
        assertEquals(Phase.INTRADAY, service.getPhase());

        service.startCloseAuction();
        assertEquals(2, service.getOrders().size());
        assertTrue(service.getOrders().stream()
                .allMatch(o -> o.getStatus() == OrderStatus.NEW));
    }

    @Test
    void unmatchedOrdersAreCancelledWhenPolicyIsCancel() {
        AuctionProperties props = TestSupport.properties(tempDir, UnmatchedPolicy.CANCEL, 1L);
        AuctionService service = TestSupport.service(props);

        service.placeOrder(req(Side.BUY, OrderType.LIMIT, 9.50, 100));
        service.placeOrder(req(Side.SELL, OrderType.LIMIT, 10.50, 100));
        service.endCurrentAuction();

        assertTrue(service.getOrders().stream()
                .allMatch(o -> o.getStatus() == OrderStatus.CANCELLED));
    }

    @Test
    void marketOrdersConvertToLimitAtReferencePrice() {
        AuctionProperties props = TestSupport.properties(
                tempDir, UnmatchedPolicy.CONVERT_LIMIT, 1L);
        AuctionService service = TestSupport.service(props);

        Order market = service.placeOrder(req(Side.BUY, OrderType.MARKET, null, 100));
        service.endCurrentAuction();

        Order reloaded = service.getOrders().stream()
                .filter(o -> o.getId() == market.getId()).findFirst().orElseThrow();
        assertEquals(OrderStatus.CONVERTED, reloaded.getStatus());
        assertEquals(OrderType.LIMIT, reloaded.getType());
        assertEquals(service.getNormalizer().toTicks(10.00), reloaded.getPriceTicks());
    }

    @Test
    void ordersRejectedOutsideAuctionPhases() {
        AuctionProperties props = TestSupport.properties(tempDir, UnmatchedPolicy.CARRY, 1L);
        AuctionService service = TestSupport.service(props);
        service.endCurrentAuction();
        assertThrows(IllegalStateException.class,
                () -> service.placeOrder(req(Side.BUY, OrderType.LIMIT, 10.00, 100)));
    }

    @Test
    void imbalanceIsBucketedAndNeverLeakyWhenDisabled() {
        AuctionProperties props = TestSupport.properties(tempDir, UnmatchedPolicy.CARRY, 1L);
        AuctionService service = TestSupport.service(props);
        service.placeOrder(req(Side.BUY, OrderType.LIMIT, 10.00, 237));

        ImbalanceSnapshot snapshot = service.getImbalance();
        assertTrue(snapshot.disclosed());
        assertEquals(200, snapshot.roundedReferenceBuyQuantity());

        props.getImbalance().setPublishEnabled(false);
        ImbalanceSnapshot hidden = service.getImbalance();
        assertFalse(hidden.disclosed());
        assertEquals(0, hidden.roundedImbalanceQuantity());
        assertNotNull(hidden);
    }
}
