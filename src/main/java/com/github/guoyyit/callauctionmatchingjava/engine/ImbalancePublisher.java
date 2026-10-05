package com.github.guoyyit.callauctionmatchingjava.engine;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.github.guoyyit.callauctionmatchingjava.config.AuctionProperties;
import com.github.guoyyit.callauctionmatchingjava.model.ImbalanceSnapshot;
import com.github.guoyyit.callauctionmatchingjava.model.Order;
import com.github.guoyyit.callauctionmatchingjava.model.OrderType;
import com.github.guoyyit.callauctionmatchingjava.model.Phase;
import com.github.guoyyit.callauctionmatchingjava.model.Side;

/**
 * 买卖不平衡指示发布器。
 *
 * <p>在集合竞价参考价(指示性均衡价)上汇总可成交买量/卖量，计算不平衡方向
 * 与差值。所有对外数量都按 {@code quantityBucketSize} 向下取整，
 * 不发布时只返回 {@code disclosed=false} 的空快照，调用方无法从中获得
 * 任何精确的未成交数量或价格。</p>
 */
public class ImbalancePublisher {

    private final AuctionProperties.Imbalance config;
    private final AtomicLong snapshotSeq = new AtomicLong(0);

    public ImbalancePublisher(AuctionProperties properties) {
        this.config = properties.getImbalance();
    }

    /** 恢复时重置快照序号。 */
    public void resetSnapshotSeq(long seq) {
        snapshotSeq.set(seq);
    }

    public long currentSnapshotSeq() {
        return snapshotSeq.get();
    }

    /**
     * 生成不平衡快照。
     *
     * @param indicativePriceTick 当前指示性均衡价；null 时只统计市价单
     */
    public ImbalanceSnapshot publish(String symbol, Phase phase, List<Order> activeOrders,
                                    Long indicativePriceTick) {
        if (!config.isPublishEnabled()) {
            return ImbalanceSnapshot.hidden(symbol, phase);
        }
        if (phase == Phase.OPEN_AUCTION && !config.isPublishInOpenAuction()) {
            return ImbalanceSnapshot.hidden(symbol, phase);
        }
        if (phase == Phase.CLOSE_AUCTION && !config.isPublishInCloseAuction()) {
            return ImbalanceSnapshot.hidden(symbol, phase);
        }

        long buyQuantity = 0L;
        long sellQuantity = 0L;
        for (Order order : activeOrders) {
            if (order.getRemainingQuantity() <= 0) {
                continue;
            }
            boolean included = indicativePriceTick == null
                    ? order.getType() == OrderType.MARKET
                    : (order.getSide() == Side.BUY
                        ? (order.getType() == OrderType.MARKET
                            || order.getPriceTicks() >= indicativePriceTick)
                        : (order.getType() == OrderType.MARKET
                            || order.getPriceTicks() <= indicativePriceTick));
            if (!included) {
                continue;
            }
            if (order.getSide() == Side.BUY) {
                buyQuantity += order.getRemainingQuantity();
            } else {
                sellQuantity += order.getRemainingQuantity();
            }
        }

        long imbalance = buyQuantity - sellQuantity;
        String direction = imbalance > 0 ? "BUY" : imbalance < 0 ? "SELL" : "BALANCED";
        long bucket = Math.max(0L, config.getQuantityBucketSize());
        long roundedImbalance = bucket == 0 ? 0L : floorToBucket(Math.abs(imbalance), bucket);
        long roundedBuy = bucket == 0 ? 0L : floorToBucket(buyQuantity, bucket);
        long roundedSell = bucket == 0 ? 0L : floorToBucket(sellQuantity, bucket);
        Long disclosedPrice = config.isDiscloseReferencePrice() ? indicativePriceTick : null;

        return new ImbalanceSnapshot(symbol, phase, true, direction,
                roundedImbalance, roundedBuy, roundedSell, disclosedPrice,
                snapshotSeq.incrementAndGet());
    }

    private static long floorToBucket(long value, long bucket) {
        if (value <= 0) {
            return 0L;
        }
        return (value / bucket) * bucket;
    }
}
