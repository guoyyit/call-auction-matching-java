package com.github.guoyyit.callauctionmatchingjava.tests;

import com.github.guoyyit.callauctionmatchingjava.core.command.Command;
import com.github.guoyyit.callauctionmatchingjava.core.log.FileCommandLog;
import com.github.guoyyit.callauctionmatchingjava.core.command.LoggedCommand;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionKind;
import com.github.guoyyit.callauctionmatchingjava.core.model.Order;
import com.github.guoyyit.callauctionmatchingjava.core.model.OrderStatus;
import com.github.guoyyit.callauctionmatchingjava.core.model.Phase;
import com.github.guoyyit.callauctionmatchingjava.core.model.Side;
import com.github.guoyyit.callauctionmatchingjava.core.model.UnfilledPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class CrashRecoveryTest {

    @TempDir
    Path tempDir;

    private static Command.PlaceOrder limit(String id, Side side, String price, long quantity) {
        return new Command.PlaceOrder(id, "SEC", side, new BigDecimal(price), false, quantity);
    }

    @Test
    public void restoresFillsOpenOrdersPhaseAndSequenceWithoutReplay() throws Exception {
        Path logFile = tempDir.resolve("recovery.log");
        TestSupport.Context context = TestSupport.newContext(logFile, 9L);
        context.recover();
        var service = context.service();
        service.startAuction(AuctionKind.OPEN, new BigDecimal("10.00"),
                UnfilledPolicy.CARRY_OVER, null);
        service.placeOrder("b1", limit("b1", Side.BUY, "10.05", 100L));
        service.placeOrder("s1", limit("s1", Side.SELL, "10.00", 60L));
        service.placeOrder("s2", limit("s2", Side.SELL, "10.30", 20L));
        service.placeOrder("b2", limit("b2", Side.BUY, "9.90", 10L));
        service.finishAuction();

        long lastSeqBefore = service.snapshotState().getLastCommandSeq();
        assertEquals(6L, lastSeqBefore);

        List<LoggedCommand> loggedCommands = new FileCommandLog(logFile, new ObjectMapper()).readAll();
        assertEquals(6, loggedCommands.size());

        var recoveredService = context.newRecoveredService();
        assertEquals(Phase.FINISHED, recoveredService.snapshotState().getPhase());
        assertEquals(lastSeqBefore, recoveredService.snapshotState().getLastCommandSeq());
        assertEquals(1, recoveredService.results().size());
        assertEquals(60L, recoveredService.results().get(0).matchedQuantity());

        List<String> activeIds = recoveredService.activeOrders().stream().map(Order::getClientOrderId).sorted().toList();
        assertEquals(List.of("b1", "b2", "s2"), activeIds);

        Order residualBuy = recoveredService.snapshotState().getAllOrders().get("b1");
        assertEquals(40L, residualBuy.getOpenQuantity());
        assertEquals(OrderStatus.ACTIVE, residualBuy.getStatus());
        assertFalse(recoveredService.snapshotState().getAllOrders().get("s1").isPriceClamped());
    }
}
