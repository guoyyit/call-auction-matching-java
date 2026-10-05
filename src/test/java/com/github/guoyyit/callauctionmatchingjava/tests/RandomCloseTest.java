package com.github.guoyyit.callauctionmatchingjava.tests;

import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionKind;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionResult;
import com.github.guoyyit.callauctionmatchingjava.core.model.Phase;
import com.github.guoyyit.callauctionmatchingjava.core.model.UnfilledPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RandomCloseTest {

    @TempDir
    Path tempDir;

    @Test
    public void sameSeedProducesIdenticalCloseEndTime() throws Exception {
        long firstEnd;
        {
            TestSupport.Context context = TestSupport.newContext(tempDir.resolve("seed-a.log"), 42L);
            context.properties().setCloseWindowStartMillisAfterStart(1_000L);
            context.properties().setCloseWindowDurationMillis(5_000L);
            context.recover();
            context.service().startAuction(AuctionKind.CLOSE, new BigDecimal("10.00"),
                    UnfilledPolicy.CANCEL, null);
            firstEnd = context.service().snapshotState().getScheduledEndTimestampMillis();
        }

        long secondEnd;
        {
            TestSupport.Context context = TestSupport.newContext(tempDir.resolve("seed-b.log"), 42L);
            context.properties().setCloseWindowStartMillisAfterStart(1_000L);
            context.properties().setCloseWindowDurationMillis(5_000L);
            context.recover();
            context.service().startAuction(AuctionKind.CLOSE, new BigDecimal("10.00"),
                    UnfilledPolicy.CANCEL, null);
            secondEnd = context.service().snapshotState().getScheduledEndTimestampMillis();
        }

        assertEquals(firstEnd, secondEnd);
        assertTrue(secondEnd >= 1_002_000L);
        assertTrue(secondEnd <= 1_006_000L);
    }

    @Test
    public void scheduledCloseFinishesAuctionInsideWindow() throws Exception {
        TestSupport.Context context = TestSupport.newContext(tempDir.resolve("scheduled.log"), 5L);
        context.properties().setCloseWindowStartMillisAfterStart(0L);
        context.properties().setCloseWindowDurationMillis(50L);
        context.recover();
        context.service().startAuction(AuctionKind.CLOSE, new BigDecimal("10.00"),
                UnfilledPolicy.CANCEL, null);
        long scheduledEnd = context.service().snapshotState().getScheduledEndTimestampMillis();
        context.clock().set(scheduledEnd);
        context.service().runDueScheduledClose();
        assertEquals(Phase.FINISHED, context.service().snapshotState().getPhase());
        AuctionResult result = context.service().results().get(0);
        assertEquals(scheduledEnd, result.endTimestampMillis());
    }
}
