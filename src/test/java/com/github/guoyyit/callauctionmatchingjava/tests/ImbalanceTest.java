package com.github.guoyyit.callauctionmatchingjava.tests;

import com.github.guoyyit.callauctionmatchingjava.core.command.Command;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionKind;
import com.github.guoyyit.callauctionmatchingjava.core.model.Imbalance;
import com.github.guoyyit.callauctionmatchingjava.core.model.ImbalanceLevel;
import com.github.guoyyit.callauctionmatchingjava.core.model.Side;
import com.github.guoyyit.callauctionmatchingjava.core.model.UnfilledPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ImbalanceTest {

    @TempDir
    Path tempDir;

    private static Command.PlaceOrder limit(String id, Side side, String price, long quantity) {
        return new Command.PlaceOrder(id, "SEC", side, new BigDecimal(price), false, quantity);
    }

    private TestSupport.Context seededContext(ImbalanceLevel level, long bucketSize, boolean discloseRef) throws Exception {
        TestSupport.Context context = TestSupport.newContext(tempDir.resolve("imb-" + level + ".log"), 4L);
        context.properties().getImbalance().setLevel(level);
        context.properties().getImbalance().setQuantityBucketSize(bucketSize);
        context.properties().getImbalance().setDiscloseReferencePrice(discloseRef);
        context.recover();
        context.service().startAuction(AuctionKind.OPEN, new BigDecimal("10.00"),
                UnfilledPolicy.CANCEL, null);
        context.service().placeOrder("b1", limit("b1", Side.BUY, "10.05", 250L));
        context.service().placeOrder("s1", limit("s1", Side.SELL, "9.95", 100L));
        return context;
    }

    @Test
    public void noneLevelDisclosesNothing() throws Exception {
        TestSupport.Context context = seededContext(ImbalanceLevel.NONE, 100L, true);
        Imbalance imbalance = context.service().imbalance();
        assertEquals(ImbalanceLevel.NONE, imbalance.level());
        assertNull(imbalance.dominantSide());
        assertEquals(0L, imbalance.imbalanceQuantity());
        assertFalse(imbalance.referencePriceDisclosed());
    }

    @Test
    public void directionOnlyDisclosesSideWithoutQuantitiesOrPrice() throws Exception {
        TestSupport.Context context = seededContext(ImbalanceLevel.DIRECTION_ONLY, 100L, true);
        Imbalance imbalance = context.service().imbalance();
        assertEquals(Side.BUY, imbalance.dominantSide());
        assertEquals(0L, imbalance.disclosedBuyQuantity());
        assertEquals(0L, imbalance.disclosedSellQuantity());
        assertFalse(imbalance.referencePriceDisclosed());
        assertNull(imbalance.referencePriceTick());
    }

    @Test
    public void bucketedLevelRoundsQuantitiesDownAndHidesExactValues() throws Exception {
        TestSupport.Context context = seededContext(ImbalanceLevel.BUCKETED, 100L, false);
        Imbalance imbalance = context.service().imbalance();
        assertEquals(Side.BUY, imbalance.dominantSide());
        assertEquals(200L, imbalance.disclosedBuyQuantity());
        assertEquals(100L, imbalance.disclosedSellQuantity());
        assertEquals(100L, imbalance.imbalanceQuantity());
        assertFalse(imbalance.referencePriceDisclosed());
        assertNull(imbalance.referencePriceTick());
    }

    @Test
    public void referencePriceDisclosedOnlyWhenEnabled() throws Exception {
        TestSupport.Context context = seededContext(ImbalanceLevel.BUCKETED, 100L, true);
        Imbalance imbalance = context.service().imbalance();
        assertEquals(1000L, imbalance.referencePriceTick());
    }
}
