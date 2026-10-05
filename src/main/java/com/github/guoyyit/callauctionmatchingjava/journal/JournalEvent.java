package com.github.guoyyit.callauctionmatchingjava.journal;

import com.github.guoyyit.callauctionmatchingjava.model.OrderType;
import com.github.guoyyit.callauctionmatchingjava.model.Phase;
import com.github.guoyyit.callauctionmatchingjava.model.Side;

/**
 * 写前日志中的单条事件。
 *
 * <p>采用单一 JSON 行格式，字段顺序固定。恢复时严格按 {@code seq} 顺序
 * 重放，既不会重放也不会跳过任何一条已确认事件。</p>
 */
public record JournalEvent(
        long seq,
        EventType type,
        long timeMillis,
        Long orderId,
        String symbol,
        Side side,
        OrderType orderType,
        Long priceTicks,
        Long quantity,
        Long arrivalSeq,
        Long replacesOrderId,
        Phase phase,
        Long matchPriceTicks,
        Long matchedQuantity,
        String detail
) {

    public static JournalEvent orderNew(long seq, long timeMillis, long orderId, String symbol,
                                        Side side, OrderType orderType, long priceTicks,
                                        long quantity, long arrivalSeq, Long replacesOrderId) {
        return new JournalEvent(seq, EventType.ORDER_NEW, timeMillis, orderId, symbol, side,
                orderType, priceTicks, quantity, arrivalSeq, replacesOrderId, null, null, null, null);
    }

    public static JournalEvent orderCancel(long seq, long timeMillis, long orderId, String detail) {
        return new JournalEvent(seq, EventType.ORDER_CANCEL, timeMillis, orderId, null, null,
                null, null, null, null, null, null, null, null, detail);
    }

    public static JournalEvent orderReplace(long seq, long timeMillis, long oldOrderId,
                                            long newOrderId, String detail) {
        return new JournalEvent(seq, EventType.ORDER_REPLACE, timeMillis, oldOrderId, null, null,
                null, null, null, null, null, null, null, null,
                detail == null ? String.valueOf(newOrderId) : detail);
    }

    public static JournalEvent phaseChange(long seq, long timeMillis, Phase phase, String detail) {
        return new JournalEvent(seq, EventType.PHASE_CHANGE, timeMillis, null, null, null,
                null, null, null, null, null, phase, null, null, detail);
    }

    public static JournalEvent auctionMatch(long seq, long timeMillis, String symbol, Phase phase,
                                            long matchPriceTicks, long matchedQuantity) {
        return new JournalEvent(seq, EventType.AUCTION_MATCH, timeMillis, null, symbol, null,
                null, null, null, null, null, phase, matchPriceTicks, matchedQuantity, null);
    }
}
