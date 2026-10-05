package com.github.guoyyit.callauctionmatchingjava.core.model;

public class Order {

    private final String clientOrderId;
    private final String symbol;
    private final Side side;
    private Long limitTick;
    private boolean market;
    private final long arrivalSeq;

    private long originalLimitTick;
    private boolean priceClamped;
    private long quantity;
    private long filledQuantity;
    private OrderStatus status;
    private String replacedByOrderId;

    public Order(String clientOrderId, String symbol, Side side, Long limitTick,
                 boolean market, long quantity, long arrivalSeq) {
        this.clientOrderId = clientOrderId;
        this.symbol = symbol;
        this.side = side;
        this.limitTick = limitTick;
        this.market = market;
        this.quantity = quantity;
        this.arrivalSeq = arrivalSeq;
    }

    public String getClientOrderId() { return clientOrderId; }
    public String getSymbol() { return symbol; }
    public Side getSide() { return side; }
    public Long getLimitTick() { return limitTick; }
    public void setLimitTickInternal(Long limitTick) { this.limitTick = limitTick; }
    public boolean isMarket() { return market; }
    public void setMarket(boolean market) { this.market = market; }
    public long getArrivalSeq() { return arrivalSeq; }
    public long getOriginalLimitTick() { return originalLimitTick; }
    public boolean isPriceClamped() { return priceClamped; }
    public long getQuantity() { return quantity; }
    public long getFilledQuantity() { return filledQuantity; }
    public OrderStatus getStatus() { return status; }
    public String getReplacedByOrderId() { return replacedByOrderId; }

    public long getOpenQuantity() {
        return Math.max(0L, quantity - filledQuantity);
    }

    public void setOriginalLimitTick(long originalLimitTick) { this.originalLimitTick = originalLimitTick; }
    public void setPriceClamped(boolean priceClamped) { this.priceClamped = priceClamped; }
    public void setQuantity(long quantity) { this.quantity = quantity; }
    public void setFilledQuantity(long filledQuantity) { this.filledQuantity = filledQuantity; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public void setReplacedByOrderId(String replacedByOrderId) { this.replacedByOrderId = replacedByOrderId; }
}
