package com.github.guoyyit.callauctionmatchingjava.web.dto;

import com.github.guoyyit.callauctionmatchingjava.model.OrderType;
import com.github.guoyyit.callauctionmatchingjava.model.Side;

/**
 * 挂单请求。市价单 price 可省略；限价单 price 为货币价格。
 */
public record OrderRequest(
        String symbol,
        Side side,
        OrderType type,
        Double price,
        Long quantity
) {
}
