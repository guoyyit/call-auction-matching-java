package com.github.guoyyit.callauctionmatchingjava.web.dto;

import com.github.guoyyit.callauctionmatchingjava.model.OrderType;
import com.github.guoyyit.callauctionmatchingjava.model.Side;

/**
 * 改单请求：撤销原委托并以新价格/数量挂入新委托(保持可追溯链)。
 */
public record ReplaceRequest(
        Long originalOrderId,
        Side side,
        OrderType type,
        Double price,
        Long quantity
) {
}
