package com.github.guoyyit.callauctionmatchingjava.core.engine;

import com.github.guoyyit.callauctionmatchingjava.core.model.Order;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderBook {

    private final Map<String, Order> active = new LinkedHashMap<>();

    public void add(Order order) {
        active.put(order.getClientOrderId(), order);
    }

    public Order remove(String clientOrderId) {
        return active.remove(clientOrderId);
    }

    public Order get(String clientOrderId) {
        return active.get(clientOrderId);
    }

    public Collection<Order> activeOrders() {
        return List.copyOf(active.values());
    }

    public void clear() {
        active.clear();
    }
}
