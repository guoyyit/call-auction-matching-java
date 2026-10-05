package com.github.guoyyit.callauctionmatchingjava.core.engine;

import com.github.guoyyit.callauctionmatchingjava.core.model.Order;
import com.github.guoyyit.callauctionmatchingjava.core.model.Side;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class PriceSelector {

    public record Selection(
            Long priceTick,
            long matchedQuantity,
            long buyRemaining,
            long sellRemaining,
            boolean adjustedByLimit
    ) {
    }

    private record Candidate(long price, long matched, long buyRemaining, long sellRemaining) {
    }

    private record VolumeAt(long buyQuantity, long sellQuantity) {
    }

    public static Selection select(List<Order> arrivalOrders, long lowerTick, long upperTick, long referenceTick) {
        if (lowerTick > upperTick) {
            throw new IllegalArgumentException("lowerTick must not exceed upperTick");
        }
        TreeSet<Long> candidatePrices = new TreeSet<>();
        candidatePrices.add(referenceTick);
        for (Order order : arrivalOrders) {
            if (order.isMarket() || order.getLimitTick() == null) {
                continue;
            }
            if (order.getLimitTick() >= lowerTick && order.getLimitTick() <= upperTick) {
                candidatePrices.add(order.getLimitTick());
            }
        }
        candidatePrices.removeIf(price -> price < lowerTick || price > upperTick);

        List<Candidate> candidates = new ArrayList<>();
        for (long price : candidatePrices) {
            VolumeAt volume = volumeAt(arrivalOrders, price);
            long matched = Math.min(volume.buyQuantity(), volume.sellQuantity());
            candidates.add(new Candidate(price, matched,
                    volume.buyQuantity() - matched, volume.sellQuantity() - matched));
        }

        if (candidates.isEmpty()) {
            return new Selection(null, 0L, 0L, 0L, false);
        }

        long maxMatched = candidates.stream().mapToLong(Candidate::matched).max().orElse(0L);
        if (maxMatched <= 0L) {
            return new Selection(null, 0L, 0L, 0L, false);
        }
        List<Candidate> round1 = filter(candidates, candidate -> candidate.matched() == maxMatched);
        if (round1.size() == 1) {
            return selectionOf(round1.get(0));
        }

        long minRemaining = round1.stream()
                .mapToLong(candidate -> candidate.buyRemaining() + candidate.sellRemaining())
                .min().orElse(0L);
        List<Candidate> round2 = filter(round1,
                candidate -> candidate.buyRemaining() + candidate.sellRemaining() == minRemaining);
        if (round2.size() == 1) {
            return selectionOf(round2.get(0));
        }

        long buyPressure = round2.stream().mapToLong(Candidate::buyRemaining).sum()
                - round2.stream().mapToLong(Candidate::sellRemaining).sum();
        List<Candidate> round3 = round2;
        if (buyPressure > 0) {
            long maxPrice = round3.stream().mapToLong(Candidate::price).max().orElse(referenceTick);
            round3 = filter(round3, candidate -> candidate.price() == maxPrice);
        } else if (buyPressure < 0) {
            long minPrice = round3.stream().mapToLong(Candidate::price).min().orElse(referenceTick);
            round3 = filter(round3, candidate -> candidate.price() == minPrice);
        }
        if (round3.size() == 1) {
            return selectionOf(round3.get(0));
        }

        long closest = round3.stream()
                .mapToLong(candidate -> Math.abs(candidate.price() - referenceTick))
                .min().orElse(0L);
        List<Candidate> round4 = filter(round3,
                candidate -> Math.abs(candidate.price() - referenceTick) == closest);
        if (round4.size() == 1) {
            return selectionOf(round4.get(0));
        }

        long lowerPrice = round4.stream().mapToLong(Candidate::price).min().orElse(referenceTick);
        return selectionOf(filter(round4, candidate -> candidate.price() == lowerPrice).get(0));
    }

    private static List<Candidate> filter(List<Candidate> source, java.util.function.Predicate<Candidate> predicate) {
        List<Candidate> result = new ArrayList<>();
        for (Candidate candidate : source) {
            if (predicate.test(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static Selection selectionOf(Candidate candidate) {
        return new Selection(candidate.price(), candidate.matched(),
                candidate.buyRemaining(), candidate.sellRemaining(), false);
    }

    private static VolumeAt volumeAt(List<Order> arrivalOrders, long price) {
        long buy = 0L;
        long sell = 0L;
        for (Order order : arrivalOrders) {
            if (!isExecutableAt(order, price)) {
                continue;
            }
            long open = order.getOpenQuantity();
            if (open <= 0L) {
                continue;
            }
            if (order.getSide() == Side.BUY) {
                buy += open;
            } else {
                sell += open;
            }
        }
        return new VolumeAt(buy, sell);
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
