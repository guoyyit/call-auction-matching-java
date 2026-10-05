package com.github.guoyyit.callauctionmatchingjava.web.dto;

import com.github.guoyyit.callauctionmatchingjava.model.Order;
import com.github.guoyyit.callauctionmatchingjava.model.OrderStatus;
import com.github.guoyyit.callauctionmatchingjava.model.OrderType;
import com.github.guoyyit.callauctionmatchingjava.model.Side;

/**
 * 委托对外视图。
 */
public record OrderView(
        long orderId,
        String symbol,
        Side side,
        OrderType type,
        Double price,
        long quantity,
        long filledQuantity,
        long remainingQuantity,
        OrderStatus status,
        Long replaces,
        Long replacedBy,
        boolean priceLimited,
        long arrivalSeq
) {

    public static OrderView from(Order order, java.util.function.ToDoubleFunction<Long> tickToPrice) {
        Double price = order.getType() == OrderType.MARKET ? null
                : tickToPrice.applyAsDouble(order.getPriceTicks());
        return new OrderView(order.getId(), order.getSymbol(), order.getSide(), order.getType(),
                price, order.getQuantity(), order.getFilledQuantity(),
                order.getRemainingQuantity(), order.getStatus(), order.getReplaces(),
                order.getReplacedBy(), order.isPriceLimited(), order.getArrivalSeq());
    }
}
