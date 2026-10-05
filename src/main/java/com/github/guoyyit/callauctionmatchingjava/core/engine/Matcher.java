package com.github.guoyyit.callauctionmatchingjava.core.engine;

import com.github.guoyyit.callauctionmatchingjava.core.model.Fill;
import com.github.guoyyit.callauctionmatchingjava.core.model.Order;
import com.github.guoyyit.callauctionmatchingjava.core.model.Side;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Matcher {

    public record Outcome(
            List<Fill> fills,
            Map<String, Long> filledQuantityByOrder,
            long matchedQuantity
    ) {
    }

    public static Outcome match(String auctionId, List<Order> arrivalOrders, long priceTick, long firstFillSeq) {
        List<Order> eligibleBuys = new ArrayList<>();
        List<Order> eligibleSells = new ArrayList<>();
        for (Order order : arrivalOrders) {
            if (!isExecutableAt(order, priceTick)) {
                continue;
            }
            if (order.getOpenQuantity() <= 0L) {
                continue;
            }
            if (order.getSide() == Side.BUY) {
                eligibleBuys.add(order);
            } else {
                eligibleSells.add(order);
            }
        }

        long buyTotal = eligibleBuys.stream().mapToLong(Order::getOpenQuantity).sum();
        long sellTotal = eligibleSells.stream().mapToLong(Order::getOpenQuantity).sum();
        long matchedTotal = Math.min(buyTotal, sellTotal);

        List<Order> aggressiveSide;
        List<Order> passiveSide;
        if (buyTotal < sellTotal) {
            aggressiveSide = eligibleBuys;
            passiveSide = eligibleSells;
        } else if (sellTotal < buyTotal) {
            aggressiveSide = eligibleSells;
            passiveSide = eligibleBuys;
        } else {
            aggressiveSide = eligibleBuys;
            passiveSide = eligibleSells;
        }

        Map<String, Long> filledByOrder = new LinkedHashMap<>();
        List<Fill> fills = new ArrayList<>();
        long[] passiveRemaining = passiveSide.stream().mapToLong(Order::getOpenQuantity).toArray();
        int passiveIndex = 0;
        for (Order aggressive : aggressiveSide) {
            long left = Math.min(aggressive.getOpenQuantity(), matchedTotal);
            long allocatedToOrder = 0L;
            while (left > 0L) {
                while (passiveIndex < passiveSide.size() && passiveRemaining[passiveIndex] <= 0L) {
                    passiveIndex++;
                }
                if (passiveIndex >= passiveSide.size()) {
                    break;
                }
                long take = Math.min(left, passiveRemaining[passiveIndex]);
                Order passive = passiveSide.get(passiveIndex);
                filledByOrder.merge(passive.getClientOrderId(), take, Long::sum);
                passiveRemaining[passiveIndex] -= take;
                allocatedToOrder += take;
                left -= take;
            }
            if (allocatedToOrder > 0L) {
                filledByOrder.merge(aggressive.getClientOrderId(), allocatedToOrder, Long::sum);
            }
        }

        long fillSeq = firstFillSeq;
        for (Order order : arrivalOrders) {
            long quantity = filledByOrder.getOrDefault(order.getClientOrderId(), 0L);
            if (quantity > 0L) {
                fills.add(new Fill(auctionId, order.getClientOrderId(), order.getSide(),
                        quantity, priceTick, fillSeq++));
            }
        }
        return new Outcome(List.copyOf(fills), Map.copyOf(filledByOrder), matchedTotal);
    }

    private static boolean isExecutableAt(Order order, long price) {
        if (order.isMarket() || order.getLimitTick() == null) {
            return true;
        }
        if (order.getSide() == Side.BUY) {
            return order.getLimitTick() >= price;
        }
        return order.getLimitTick() <= price;
    }
}
