package com.github.guoyyit.callauctionmatchingjava.core.session;

import com.github.guoyyit.callauctionmatchingjava.core.command.Command;
import com.github.guoyyit.callauctionmatchingjava.core.command.LoggedCommand;
import com.github.guoyyit.callauctionmatchingjava.core.engine.Matcher;
import com.github.guoyyit.callauctionmatchingjava.core.engine.PriceSelector;
import com.github.guoyyit.callauctionmatchingjava.core.engine.Ticks;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionResult;
import com.github.guoyyit.callauctionmatchingjava.core.model.Fill;
import com.github.guoyyit.callauctionmatchingjava.core.model.Order;
import com.github.guoyyit.callauctionmatchingjava.core.model.OrderStatus;
import com.github.guoyyit.callauctionmatchingjava.core.model.Phase;

import java.util.ArrayList;
import java.util.List;

public class AuctionKernel {

    private final AuctionState state;

    public AuctionKernel(AuctionState state) {
        this.state = state;
    }

    public AuctionState getState() {
        return state;
    }

    public void apply(LoggedCommand logged) {
        state.setLastCommandSeq(logged.seq());
        Command command = logged.command();
        if (command instanceof Command.PlaceOrder place) {
            applyPlace(logged.timestampMillis(), place);
        } else if (command instanceof Command.CancelOrder cancel) {
            applyCancel(logged.timestampMillis(), cancel);
        } else if (command instanceof Command.ReplaceOrder replace) {
            applyReplace(logged.timestampMillis(), replace);
        } else if (command instanceof Command.StartAuction start) {
            applyStart(logged.timestampMillis(), start);
        } else if (command instanceof Command.FinishAuction finish) {
            applyFinish(logged.timestampMillis(), finish);
        }
    }

    private void applyPlace(long nowMillis, Command.PlaceOrder command) {
        if (state.getPhase() != Phase.COLLECTING) {
            return;
        }
        if (state.getAllOrders().containsKey(command.clientOrderId())) {
            return;
        }
        if (command.quantity() <= 0L) {
            return;
        }
        if (!command.market() && command.limitPrice() == null) {
            return;
        }
        long arrivalSeq = state.getArrivalOrderIds().size() + 1L;
        Long limitTick = command.market() ? null
                : Ticks.toTickIndex(command.limitPrice(), state.getTickSize());
        Order order = new Order(command.clientOrderId(), command.symbol(), command.side(),
                limitTick, command.market(), command.quantity(), arrivalSeq);
        if (!command.market()) {
            order.setOriginalLimitTick(limitTick);
            if (limitTick < state.getLowerLimitTick() || limitTick > state.getUpperLimitTick()) {
                long clamped = Ticks.clamp(limitTick, state.getLowerLimitTick(), state.getUpperLimitTick());
                order.setPriceClamped(true);
                order.setLimitTickInternal(clamped);
            }
        }
        order.setStatus(OrderStatus.ACTIVE);
        state.getBook().add(order);
        state.getArrivalOrderIds().add(order.getClientOrderId());
        state.getAllOrders().put(order.getClientOrderId(), order);
    }

    private void applyCancel(long nowMillis, Command.CancelOrder command) {
        if (state.getPhase() != Phase.COLLECTING) {
            return;
        }
        Order existing = state.getBook().remove(command.origClientOrderId());
        if (existing == null) {
            return;
        }
        existing.setStatus(OrderStatus.CANCELLED);
    }

    private void applyReplace(long nowMillis, Command.ReplaceOrder command) {
        if (state.getPhase() != Phase.COLLECTING) {
            return;
        }
        Order existing = state.getBook().get(command.origClientOrderId());
        if (existing == null || command.quantity() <= 0L) {
            return;
        }
        if (!command.market() && command.limitPrice() == null) {
            return;
        }
        state.getBook().remove(command.origClientOrderId());
        existing.setStatus(OrderStatus.REPLACED);
        existing.setReplacedByOrderId(command.clientOrderId());
        applyPlace(nowMillis, new Command.PlaceOrder(command.clientOrderId(), existing.getSymbol(),
                existing.getSide(), command.limitPrice(), command.market(), command.quantity()));
    }

    private void applyStart(long nowMillis, Command.StartAuction command) {
        if (state.getPhase() == Phase.COLLECTING) {
            return;
        }
        List<Order> carried = new ArrayList<>(state.getBook().activeOrders());
        state.setAuctionId(command.auctionId());
        state.setKind(command.kind());
        state.setTickSize(command.tickSize());
        long referenceTick = Ticks.toTickIndex(command.referencePrice(), command.tickSize());
        state.setReferenceTick(referenceTick);
        long lowerTick = Ticks.toTickIndex(command.lowerLimitPrice(), command.tickSize());
        long upperTick = Ticks.toTickIndex(command.upperLimitPrice(), command.tickSize());
        state.setLowerLimitTick(lowerTick);
        state.setUpperLimitTick(upperTick);
        state.setUnfilledPolicy(command.unfilledPolicy());
        state.setScheduledEndTimestampMillis(command.scheduledEndTimestampMillis());
        state.getBook().clear();
        state.getArrivalOrderIds().clear();
        for (Order order : carried) {
            order.setStatus(OrderStatus.ACTIVE);
            state.getBook().add(order);
            state.getArrivalOrderIds().add(order.getClientOrderId());
        }
        state.setPhase(Phase.COLLECTING);
    }

    private void applyFinish(long nowMillis, Command.FinishAuction command) {
        if (state.getPhase() != Phase.COLLECTING) {
            return;
        }
        long lowerTick = state.getLowerLimitTick();
        long upperTick = state.getUpperLimitTick();
        long referenceTick = state.getReferenceTick();
        List<Order> arrivalOrders = new ArrayList<>();
        for (String orderId : state.getArrivalOrderIds()) {
            Order order = state.getAllOrders().get(orderId);
            if (order != null && state.getBook().get(orderId) != null) {
                arrivalOrders.add(order);
            }
        }

        PriceSelector.Selection selection = PriceSelector.select(
                arrivalOrders, lowerTick, upperTick, referenceTick);
        Long equilibriumPrice = selection.priceTick();
        boolean adjustedByLimit = false;
        long executionPrice;
        long matchedQuantity = 0L;
        long buyRemaining = selection.buyRemaining();
        long sellRemaining = selection.sellRemaining();
        List<Fill> fills = List.of();
        String note = null;

        if (equilibriumPrice != null) {
            executionPrice = Ticks.clamp(equilibriumPrice, lowerTick, upperTick);
            adjustedByLimit = executionPrice != equilibriumPrice;
            Matcher.Outcome outcome = Matcher.match(state.getAuctionId(),
                    arrivalOrders, executionPrice, fillSequenceStart());
            fills = outcome.fills();
            matchedQuantity = outcome.matchedQuantity();
            buyRemaining = residualQuantity(arrivalOrders, com.github.guoyyit.callauctionmatchingjava.core.model.Side.BUY, outcome);
            sellRemaining = residualQuantity(arrivalOrders, com.github.guoyyit.callauctionmatchingjava.core.model.Side.SELL, outcome);
            for (var entry : outcome.filledQuantityByOrder().entrySet()) {
                Order order = state.getAllOrders().get(entry.getKey());
                if (order != null) {
                    order.setFilledQuantity(entry.getValue());
                    if (entry.getValue() >= order.getQuantity()) {
                        order.setStatus(OrderStatus.FILLED);
                        state.getBook().remove(order.getClientOrderId());
                    } else {
                        order.setStatus(OrderStatus.PARTIALLY_FILLED);
                    }
                }
            }
        } else {
            executionPrice = referenceTick;
            note = "NO_MATCH";
            buyRemaining = totalOpen(arrivalOrders, com.github.guoyyit.callauctionmatchingjava.core.model.Side.BUY);
            sellRemaining = totalOpen(arrivalOrders, com.github.guoyyit.callauctionmatchingjava.core.model.Side.SELL);
        }

        state.setReferenceTick(equilibriumPrice == null ? referenceTick : executionPrice);
        applyUnfilledPolicy(arrivalOrders);

        AuctionResult result = new AuctionResult(
                state.getAuctionId(), state.getKind(), referenceTick,
                equilibriumPrice == null ? null : executionPrice,
                matchedQuantity, buyRemaining, sellRemaining,
                lowerTick, upperTick, adjustedByLimit, nowMillis, fills, note);
        state.getResults().add(result);
        state.setScheduledEndTimestampMillis(null);
        state.setPhase(Phase.FINISHED);
    }

    private long residualQuantity(List<Order> orders, com.github.guoyyit.callauctionmatchingjava.core.model.Side side,
                                  Matcher.Outcome outcome) {
        long total = 0L;
        for (Order order : orders) {
            if (order.getSide() != side) {
                continue;
            }
            total += order.getOpenQuantity()
                    - outcome.filledQuantityByOrder().getOrDefault(order.getClientOrderId(), 0L);
        }
        return total;
    }

    private long totalOpen(List<Order> orders, com.github.guoyyit.callauctionmatchingjava.core.model.Side side) {
        long total = 0L;
        for (Order order : orders) {
            if (order.getSide() == side) {
                total += order.getOpenQuantity();
            }
        }
        return total;
    }

    private long fillSequenceStart() {
        AuctionResult previous = state.lastResult();
        long nextSeq = 1L;
        if (previous != null && !previous.fills().isEmpty()) {
            nextSeq = previous.fills().get(previous.fills().size() - 1).sequence() + 1L;
        }
        return nextSeq;
    }

    private void applyUnfilledPolicy(List<Order> arrivalOrders) {
        for (Order order : arrivalOrders) {
            if (order.getOpenQuantity() <= 0L) {
                continue;
            }
            switch (state.getUnfilledPolicy()) {
                case CANCEL -> {
                    order.setStatus(OrderStatus.EXPIRED);
                    state.getBook().remove(order.getClientOrderId());
                }
                case CONVERT_TO_LIMIT -> {
                    if (order.isMarket()) {
                        order.setLimitTickInternal(state.getReferenceTick());
                        order.setMarket(false);
                    }
                    order.setStatus(OrderStatus.CONVERTED);
                    state.getBook().remove(order.getClientOrderId());
                }
                case CARRY_OVER -> {
                    if (order.getStatus() == OrderStatus.ACTIVE
                            || order.getStatus() == OrderStatus.PARTIALLY_FILLED) {
                        order.setStatus(OrderStatus.ACTIVE);
                    }
                }
            }
        }
    }
}
