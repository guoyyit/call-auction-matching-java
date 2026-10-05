package com.github.guoyyit.callauctionmatchingjava.core.engine;

import com.github.guoyyit.callauctionmatchingjava.core.model.Imbalance;
import com.github.guoyyit.callauctionmatchingjava.core.model.ImbalanceLevel;
import com.github.guoyyit.callauctionmatchingjava.core.model.Order;
import com.github.guoyyit.callauctionmatchingjava.core.model.Side;

import java.util.Collection;

public class ImbalanceCalculator {

    public static Imbalance calculate(Collection<Order> activeOrders,
                                      long referenceTick,
                                      ImbalanceLevel level,
                                      long quantityBucketSize,
                                      boolean discloseReferencePrice) {
        long buyQuantity = 0L;
        long sellQuantity = 0L;
        for (Order order : activeOrders) {
            if (order.getOpenQuantity() <= 0L) {
                continue;
            }
            if (order.getSide() == Side.BUY) {
                buyQuantity += order.getOpenQuantity();
            } else {
                sellQuantity += order.getOpenQuantity();
            }
        }
        long indicativeMatched = Math.min(buyQuantity, sellQuantity);
        long imbalanceQuantity = Math.abs(buyQuantity - sellQuantity);
        Side dominant = buyQuantity > sellQuantity ? Side.BUY
                : sellQuantity > buyQuantity ? Side.SELL : null;

        if (level == ImbalanceLevel.NONE) {
            return new Imbalance(level, null, 0L, 0L, 0L, 0L, false, null);
        }

        if (level == ImbalanceLevel.DIRECTION_ONLY) {
            return new Imbalance(level, dominant, 0L, 0L, 0L, 0L, false, null);
        }

        long bucketSize = Math.max(1L, quantityBucketSize);
        long disclosedBuy = bucket(buyQuantity, bucketSize);
        long disclosedSell = bucket(sellQuantity, bucketSize);
        Long disclosedReference = discloseReferencePrice ? referenceTick : null;
        return new Imbalance(level, dominant, bucket(indicativeMatched, bucketSize),
                bucket(imbalanceQuantity, bucketSize), disclosedBuy, disclosedSell,
                discloseReferencePrice, disclosedReference);
    }

    private static long bucket(long quantity, long bucketSize) {
        return (quantity / bucketSize) * bucketSize;
    }
}
