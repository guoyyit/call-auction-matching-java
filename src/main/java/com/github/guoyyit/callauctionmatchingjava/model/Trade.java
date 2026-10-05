package com.github.guoyyit.callauctionmatchingjava.model;

/**
 * 一笔成交记录。集合竞价中所有成交共享同一个统一价格，
 * 成交按买方到达顺序、卖方到达顺序依次配对。
 */
public class Trade {

    private long id;
    private long buyerOrderId;
    private long sellerOrderId;
    private long priceTicks;
    private long quantity;
    private long timeMillis;

    public Trade() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getBuyerOrderId() {
        return buyerOrderId;
    }

    public void setBuyerOrderId(long buyerOrderId) {
        this.buyerOrderId = buyerOrderId;
    }

    public long getSellerOrderId() {
        return sellerOrderId;
    }

    public void setSellerOrderId(long sellerOrderId) {
        this.sellerOrderId = sellerOrderId;
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

    public long getTimeMillis() {
        return timeMillis;
    }

    public void setTimeMillis(long timeMillis) {
        this.timeMillis = timeMillis;
    }
}
