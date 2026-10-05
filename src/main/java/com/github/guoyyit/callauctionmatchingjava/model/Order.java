package com.github.guoyyit.callauctionmatchingjava.model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 一笔竞价委托。
 *
 * <p>价格统一以"最小变动价位的整数倍"(tick 数)存储，避免浮点误差；
 * 涨跌停价同理。改单采用"撤销旧单 + 以新价格/数量生成新单"的语义，
 * 通过 {@code replacedBy} / {@code replaces} 保持可追溯。</p>
 */
public class Order {

    private static final AtomicLong ID_SEQ = new AtomicLong(0);

    private long id;
    private String symbol;
    private Side side;
    private OrderType type;
    private long priceTicks;
    private long quantity;
    private long filledQuantity;
    private long arrivalSeq;
    private long arrivalTimeMillis;
    private OrderStatus status;
    private Long replaces;
    private Long replacedBy;
    private boolean priceLimited;

    public Order() {
    }

    public static long nextId() {
        return ID_SEQ.incrementAndGet();
    }

    public static void resetIdSeq(long maxId) {
        ID_SEQ.set(maxId);
    }

    public long getRemainingQuantity() {
        return quantity - filledQuantity;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }

    public long getPriceTicks() {
        return priceTicks;
    }

    public void setPriceTicks(long priceTicks) {
        this.priceTicks = priceTicks;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public long getFilledQuantity() {
        return filledQuantity;
    }

    public void setFilledQuantity(long filledQuantity) {
        this.filledQuantity = filledQuantity;
    }

    public long getArrivalSeq() {
        return arrivalSeq;
    }

    public void setArrivalSeq(long arrivalSeq) {
        this.arrivalSeq = arrivalSeq;
    }

    public long getArrivalTimeMillis() {
        return arrivalTimeMillis;
    }

    public void setArrivalTimeMillis(long arrivalTimeMillis) {
        this.arrivalTimeMillis = arrivalTimeMillis;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Long getReplaces() {
        return replaces;
    }

    public void setReplaces(Long replaces) {
        this.replaces = replaces;
    }

    public Long getReplacedBy() {
        return replacedBy;
    }

    public void setReplacedBy(Long replacedBy) {
        this.replacedBy = replacedBy;
    }

    public boolean isPriceLimited() {
        return priceLimited;
    }

    public void setPriceLimited(boolean priceLimited) {
        this.priceLimited = priceLimited;
    }
}
