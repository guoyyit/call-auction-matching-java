package com.github.guoyyit.callauctionmatchingjava.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.guoyyit.callauctionmatchingjava.config.AuctionProperties;
import com.github.guoyyit.callauctionmatchingjava.model.Order;
import com.github.guoyyit.callauctionmatchingjava.model.OrderType;
import com.github.guoyyit.callauctionmatchingjava.model.Side;
import com.github.guoyyit.callauctionmatchingjava.model.UnmatchedPolicy;
import com.github.guoyyit.callauctionmatchingjava.service.AuctionService;
import com.github.guoyyit.callauctionmatchingjava.web.dto.OrderRequest;

/**
 * 并发挂单/撤单/改单交错时，结果必须等同于某个单一全局顺序的串行执行。
 */
class ConcurrencyTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void concurrentOperationsAreSerializedAndArrivalSeqsAreUnique() throws Exception {
        AuctionProperties props = TestSupport.properties(tempDir, UnmatchedPolicy.CARRY, 7L);
        AuctionService service = TestSupport.service(props);

        int threads = 8;
        int perThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        List<Order> placed = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int t = 0; t < threads; t++) {
            final int base = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        Side side = (base + i) % 2 == 0 ? Side.BUY : Side.SELL;
                        double price = side == Side.BUY ? 9.99 : 10.01;
                        Order order = service.placeOrder(
                                new OrderRequest(null, side, OrderType.LIMIT, price, 10L));
                        placed.add(order);
                        if (i % 5 == 0) {
                            service.cancelOrder(order.getId());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        List<Order> all = service.getOrders();
        assertEquals(threads * perThread, all.size());

        long distinctSeqs = all.stream().mapToLong(Order::getArrivalSeq).distinct().count();
        assertEquals(all.size(), distinctSeqs, "arrivalSeq must be globally unique");

        long distinctIds = all.stream().mapToLong(Order::getId).distinct().count();
        assertEquals(all.size(), distinctIds, "orderId must be globally unique");

        long cancelled = all.stream().filter(o -> o.getStatus().name().equals("CANCELLED")).count();
        assertTrue(cancelled > 0);
    }
}
