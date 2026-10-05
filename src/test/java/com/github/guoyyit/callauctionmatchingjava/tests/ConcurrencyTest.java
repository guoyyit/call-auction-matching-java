package com.github.guoyyit.callauctionmatchingjava.tests;

import com.github.guoyyit.callauctionmatchingjava.core.command.Command;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionKind;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionResult;
import com.github.guoyyit.callauctionmatchingjava.core.model.Order;
import com.github.guoyyit.callauctionmatchingjava.core.model.Side;
import com.github.guoyyit.callauctionmatchingjava.core.model.UnfilledPolicy;
import com.github.guoyyit.callauctionmatchingjava.service.AuctionEngineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConcurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    public void interleavedCommandsAreEquivalentToOneSerialOrder() throws Exception {
        TestSupport.Context context = TestSupport.newContext(tempDir.resolve("concurrent.log"), 11L);
        context.recover();
        var service = context.service();
        service.startAuction(AuctionKind.OPEN, new BigDecimal("10.00"),
                UnfilledPolicy.CARRY_OVER, null);

        int threads = 8;
        int perThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger placed = new AtomicInteger();
        for (int thread = 0; thread < threads; thread++) {
            final int threadIndex = thread;
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    String id = "t" + threadIndex + "-" + i;
                    Side side = (threadIndex + i) % 2 == 0 ? Side.BUY : Side.SELL;
                    String price = side == Side.BUY ? "10.10" : "9.90";
                    try {
                        service.placeOrder(id, new Command.PlaceOrder(
                                id, "SEC", side, new BigDecimal(price), false, 10L));
                        placed.incrementAndGet();
                    } catch (IllegalStateException ignored) {
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        AuctionResult result = service.finishAuction();

        AuctionEngineService recoveredService = context.newRecoveredService();
        AuctionResult replayed = recoveredService.results().get(0);
        assertEquals(result.matchedQuantity(), replayed.matchedQuantity());
        assertEquals(result.priceTick(), replayed.priceTick());
        assertEquals(result.fills().size(), replayed.fills().size());

        long activeCount = recoveredService.activeOrders().size();
        long filledCount = result.fills().size();
        assertTrue(activeCount + filledCount <= placed.get());
    }
}
