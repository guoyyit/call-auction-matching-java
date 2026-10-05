package com.github.guoyyit.callauctionmatchingjava.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.guoyyit.callauctionmatchingjava.config.AuctionProperties;
import com.github.guoyyit.callauctionmatchingjava.engine.ImbalancePublisher;
import com.github.guoyyit.callauctionmatchingjava.engine.MatchingEngine;
import com.github.guoyyit.callauctionmatchingjava.engine.PriceLimit;
import com.github.guoyyit.callauctionmatchingjava.engine.PriceNormalizer;
import com.github.guoyyit.callauctionmatchingjava.journal.EventJournal;
import com.github.guoyyit.callauctionmatchingjava.journal.JournalEvent;
import com.github.guoyyit.callauctionmatchingjava.model.AuctionResult;
import com.github.guoyyit.callauctionmatchingjava.model.ImbalanceSnapshot;
import com.github.guoyyit.callauctionmatchingjava.model.Order;
import com.github.guoyyit.callauctionmatchingjava.model.OrderStatus;
import com.github.guoyyit.callauctionmatchingjava.model.OrderType;
import com.github.guoyyit.callauctionmatchingjava.model.Phase;
import com.github.guoyyit.callauctionmatchingjava.model.Trade;
import com.github.guoyyit.callauctionmatchingjava.model.UnmatchedPolicy;
import com.github.guoyyit.callauctionmatchingjava.web.dto.OrderRequest;
import com.github.guoyyit.callauctionmatchingjava.web.dto.ReplaceRequest;

/**
 * 集合竞价领域服务。
 *
 * <p><b>一致性语义</b>：所有改变簿状态的操作(挂单/撤单/改单/阶段切换)
 * 都在服务实例锁上串行执行，其效果等同于委托按某个单一全局顺序串行执行；
 * 先写 WAL(flush) 后改内存态，崩溃后按日志顺序完整重放，不重放也不跳过
 * 任何一条已确认事件。</p>
 */
public class AuctionService {

    private final AuctionProperties properties;
    private final PriceNormalizer normalizer;
    private final EventJournal journal;
    private final PhaseScheduler scheduler;
    private final ImbalancePublisher imbalancePublisher;

    private final Map<Long, Order> ordersById = new LinkedHashMap<>();
    private final List<Trade> trades = new ArrayList<>();
    private final List<AuctionResult> auctionResults = new ArrayList<>();

    private long arrivalSeqCounter;
    private long tradeIdCounter;
    private Double lastMatchPrice;

    public AuctionService(AuctionProperties properties, PriceNormalizer normalizer,
                          EventJournal journal, PhaseScheduler scheduler) {
        this.properties = properties;
        this.normalizer = normalizer;
        this.journal = journal;
        this.scheduler = scheduler;
        this.imbalancePublisher = new ImbalancePublisher(properties);
    }

    public synchronized Order placeOrder(OrderRequest request) {
        validateCollectingPhase();
        validateOrderRequest(request);
        long now = System.currentTimeMillis();
        long orderId = Order.nextId();
        long arrivalSeq = ++arrivalSeqCounter;
        long priceTicks = resolveEntrantPrice(request.type(), request.price(), request.quantity());

        JournalEvent event = JournalEvent.orderNew(journal.nextSeq(), now, orderId,
                effectiveSymbol(request.symbol()), request.side(), request.type(),
                priceTicks, request.quantity(), arrivalSeq, null);
        journal.append(event);
        return applyOrderNew(event);
    }

    public synchronized Order cancelOrder(long orderId) {
        validateCollectingPhase();
        Order order = requireActiveOrder(orderId);
        JournalEvent event = JournalEvent.orderCancel(journal.nextSeq(),
                System.currentTimeMillis(), orderId, null);
        journal.append(event);
        order.setStatus(OrderStatus.CANCELLED);
        return order;
    }

    public synchronized Order replaceOrder(ReplaceRequest request) {
        validateCollectingPhase();
        if (request.originalOrderId() == null) {
            throw new IllegalArgumentException("originalOrderId is required");
        }
        Order original = requireActiveOrder(request.originalOrderId());
        validateReplacement(request);

        long now = System.currentTimeMillis();
        long newId = Order.nextId();
        long arrivalSeq = ++arrivalSeqCounter;
        long priceTicks = resolveEntrantPrice(request.type(), request.price(), request.quantity());

        JournalEvent replaceEvent = JournalEvent.orderReplace(journal.nextSeq(), now,
                original.getId(), newId, null);
        JournalEvent newEvent = JournalEvent.orderNew(journal.nextSeq(), now, newId,
                original.getSymbol(), request.side(), request.type(), priceTicks,
                request.quantity(), arrivalSeq, original.getId());
        journal.append(replaceEvent);
        journal.append(newEvent);

        original.setStatus(OrderStatus.REPLACED);
        original.setReplacedBy(newId);
        return applyOrderNew(newEvent);
    }

    /** 结束当前竞价阶段并执行撮合。 */
    public synchronized AuctionResult endCurrentAuction() {
        Phase phase = scheduler.getPhase();
        if (phase != Phase.OPEN_AUCTION && phase != Phase.CLOSE_AUCTION) {
            throw new IllegalStateException("current phase is not an auction: " + phase);
        }
        return runAuctionMatch(phase);
    }

    /** 进入收盘竞价：采样随机结束时刻并写日志。 */
    public synchronized Phase startCloseAuction() {
        if (scheduler.getPhase() != Phase.INTRADAY) {
            throw new IllegalStateException(
                    "close auction can only start from intraday: " + scheduler.getPhase());
        }
        long offset = scheduler.sampleCloseEndOffsetMillis();
        JournalEvent event = JournalEvent.phaseChange(journal.nextSeq(),
                System.currentTimeMillis(), Phase.CLOSE_AUCTION,
                "closeEndOffsetMillis=" + offset);
        journal.append(event);
        scheduler.transitionTo(Phase.CLOSE_AUCTION);
        return scheduler.getPhase();
    }

    /** 收盘随机结束时刻到达则自动撮合；未触发返回 null。 */
    public synchronized AuctionResult maybeExpireCloseAuction() {
        if (!scheduler.isCloseAuctionDue()) {
            return null;
        }
        return runAuctionMatch(Phase.CLOSE_AUCTION);
    }

    /** 开盘竞价不撮合直接进入盘中(管理/测试用)。 */
    public synchronized Phase openAuctionToIntraday() {
        if (scheduler.getPhase() != Phase.OPEN_AUCTION) {
            throw new IllegalStateException("not in open auction: " + scheduler.getPhase());
        }
        JournalEvent event = JournalEvent.phaseChange(journal.nextSeq(),
                System.currentTimeMillis(), Phase.INTRADAY, null);
        journal.append(event);
        scheduler.transitionTo(Phase.INTRADAY);
        return scheduler.getPhase();
    }

    private AuctionResult runAuctionMatch(Phase phase) {
        long now = System.currentTimeMillis();
        Long referenceTick = currentReferenceTick();
        PriceLimit limit = buildPriceLimit();

        List<Order> active = activeOrders();
        AuctionResult result = MatchingEngine.match(new ArrayList<>(active), limit, referenceTick,
                properties.getTieBreakByPressure(), phase, properties.getSymbol(),
                tradeIdCounter + 1, now);

        for (Trade trade : result.trades()) {
            trades.add(trade);
            tradeIdCounter = Math.max(tradeIdCounter, trade.getId());
        }
        for (Order order : active) {
            if (order.getRemainingQuantity() == 0 && order.getFilledQuantity() > 0) {
                order.setStatus(OrderStatus.FILLED);
            } else if (order.getFilledQuantity() > 0) {
                order.setStatus(OrderStatus.PARTIALLY_FILLED);
            }
        }

        JournalEvent matchEvent = JournalEvent.auctionMatch(journal.nextSeq(), now,
                properties.getSymbol(), phase,
                result.equilibriumPriceTicks(), result.matchedQuantity());
        journal.append(matchEvent);

        if (result.equilibriumPriceTicks() >= 0) {
            lastMatchPrice = normalizer.toPriceDouble(result.equilibriumPriceTicks());
        }
        auctionResults.add(result);
        applyUnmatchedPolicy(now);

        Phase nextPhase = phase == Phase.OPEN_AUCTION ? Phase.INTRADAY : Phase.CLOSED;
        JournalEvent phaseEvent = JournalEvent.phaseChange(journal.nextSeq(), now, nextPhase, null);
        journal.append(phaseEvent);
        scheduler.transitionTo(nextPhase);
        return result;
    }

    private void applyUnmatchedPolicy(long now) {
        UnmatchedPolicy policy = properties.getUnmatchedPolicy();
        for (Order order : activeOrders()) {
            switch (policy) {
                case CANCEL -> order.setStatus(OrderStatus.CANCELLED);
                case CONVERT_LIMIT -> {
                    if (order.getType() == OrderType.MARKET) {
                        order.setType(OrderType.LIMIT);
                        Long reference = currentReferenceTick();
                        order.setPriceTicks(reference != null ? reference : 0L);
                    }
                    order.setStatus(OrderStatus.CONVERTED);
                }
                case CARRY -> {
                    // 保持可成交状态进入下一场竞价，不改变状态。
                }
            }
        }
        if (policy != UnmatchedPolicy.CARRY) {
            JournalEvent event = JournalEvent.phaseChange(journal.nextSeq(), now,
                    scheduler.getPhase(), "unmatchedPolicy=" + policy.name());
            journal.append(event);
        }
    }

    public synchronized List<Order> getOrders() {
        return new ArrayList<>(ordersById.values());
    }

    public synchronized List<Trade> getTrades() {
        return new ArrayList<>(trades);
    }

    public synchronized List<AuctionResult> getAuctionResults() {
        return new ArrayList<>(auctionResults);
    }

    public synchronized Phase getPhase() {
        return scheduler.getPhase();
    }

    public synchronized Double getLastMatchPrice() {
        return lastMatchPrice;
    }

    public PriceNormalizer getNormalizer() {
        return normalizer;
    }

    public synchronized ImbalanceSnapshot getImbalance() {
        Long indicative = computeIndicativePrice();
        return imbalancePublisher.publish(properties.getSymbol(), scheduler.getPhase(),
                activeOrders(), indicative);
    }

    /**
     * 从 WAL 完整重放。严格按事件序号顺序逐条执行状态迁移；
     * 已成交结果、未完成委托与当前阶段均被重建，不产生新的撮合副作用，
     * 既不重放也不跳过任何已落盘事件。
     */
    public synchronized void recover() {
        List<JournalEvent> events = journal.readAll();
        Long closeEndOffset = null;
        long maxOrderId = 0L;
        Phase lastPhase = Phase.OPEN_AUCTION;

        for (JournalEvent event : events) {
            switch (event.type()) {
                case ORDER_NEW -> {
                    Order order = applyOrderNew(event);
                    maxOrderId = Math.max(maxOrderId, order.getId());
                }
                case ORDER_CANCEL -> {
                    Order order = ordersById.get(event.orderId());
                    if (order != null) {
                        order.setStatus(OrderStatus.CANCELLED);
                    }
                }
                case ORDER_REPLACE -> {
                    Order original = ordersById.get(event.orderId());
                    long newId = Long.parseLong(event.detail());
                    if (original != null) {
                        original.setStatus(OrderStatus.REPLACED);
                        original.setReplacedBy(newId);
                    }
                    maxOrderId = Math.max(maxOrderId, newId);
                }
                case AUCTION_MATCH -> replayMatch(event);
                case PHASE_CHANGE -> {
                    lastPhase = event.phase();
                    if (event.detail() != null
                            && event.detail().startsWith("closeEndOffsetMillis=")) {
                        closeEndOffset = Long.parseLong(
                                event.detail().substring("closeEndOffsetMillis=".length()));
                    } else if (event.detail() != null
                            && event.detail().startsWith("unmatchedPolicy=")) {
                        applyPolicyByName(
                                event.detail().substring("unmatchedPolicy=".length()));
                    }
                }
            }
        }
        Order.resetIdSeq(maxOrderId);
        long maxEventSeq = events.isEmpty() ? 0L : events.get(events.size() - 1).seq();
        while (journal.lastSeq() < maxEventSeq) {
            journal.nextSeq();
        }
        scheduler.restore(lastPhase, scheduler.getSessionStartMillis(), closeEndOffset, 0L);
    }

    private void replayMatch(JournalEvent event) {
        Long referenceTick = currentReferenceTick();
        PriceLimit limit = buildPriceLimit();
        List<Order> snapshot = activeOrders();
        AuctionResult result = MatchingEngine.match(new ArrayList<>(snapshot), limit, referenceTick,
                properties.getTieBreakByPressure(), event.phase(), properties.getSymbol(),
                tradeIdCounter + 1, event.timeMillis());
        for (Trade trade : result.trades()) {
            trades.add(trade);
            tradeIdCounter = Math.max(tradeIdCounter, trade.getId());
        }
        for (Order order : snapshot) {
            if (order.getRemainingQuantity() == 0 && order.getFilledQuantity() > 0) {
                order.setStatus(OrderStatus.FILLED);
            } else if (order.getFilledQuantity() > 0) {
                order.setStatus(OrderStatus.PARTIALLY_FILLED);
            }
        }
        if (result.equilibriumPriceTicks() >= 0) {
            lastMatchPrice = normalizer.toPriceDouble(result.equilibriumPriceTicks());
        }
        auctionResults.add(result);
    }

    private void applyPolicyByName(String name) {
        UnmatchedPolicy policy = UnmatchedPolicy.valueOf(name);
        for (Order order : activeOrders()) {
            switch (policy) {
                case CANCEL -> order.setStatus(OrderStatus.CANCELLED);
                case CONVERT_LIMIT -> {
                    if (order.getType() == OrderType.MARKET) {
                        order.setType(OrderType.LIMIT);
                        Long reference = currentReferenceTick();
                        order.setPriceTicks(reference != null ? reference : 0L);
                    }
                    order.setStatus(OrderStatus.CONVERTED);
                }
                case CARRY -> {
                }
            }
        }
    }

    private Order applyOrderNew(JournalEvent event) {
        Order order = new Order();
        order.setId(event.orderId());
        order.setSymbol(event.symbol());
        order.setSide(event.side());
        order.setType(event.orderType());
        order.setPriceTicks(event.priceTicks() == null ? 0L : event.priceTicks());
        order.setQuantity(event.quantity());
        order.setFilledQuantity(0L);
        order.setArrivalSeq(event.arrivalSeq());
        order.setArrivalTimeMillis(event.timeMillis());
        order.setStatus(OrderStatus.NEW);
        order.setReplaces(event.replacesOrderId());

        PriceLimit limit = buildPriceLimit();
        if (order.getType() == OrderType.LIMIT && limit.isBounded()
                && !limit.contains(order.getPriceTicks())) {
            order.setPriceLimited(true);
            order.setPriceTicks(limit.clamp(order.getPriceTicks()));
        }
        ordersById.put(order.getId(), order);
        arrivalSeqCounter = Math.max(arrivalSeqCounter, event.arrivalSeq());
        if (event.replacesOrderId() != null) {
            Order predecessor = ordersById.get(event.replacesOrderId());
            if (predecessor != null && predecessor.getReplacedBy() == null) {
                predecessor.setReplacedBy(order.getId());
            }
        }
        return order;
    }

    private List<Order> activeOrders() {
        List<Order> active = new ArrayList<>();
        for (Order order : ordersById.values()) {
            if (order.getStatus() == OrderStatus.CANCELLED
                    || order.getStatus() == OrderStatus.REPLACED
                    || order.getStatus() == OrderStatus.FILLED) {
                continue;
            }
            if (order.getRemainingQuantity() <= 0) {
                continue;
            }
            active.add(order);
        }
        return active;
    }

    private Order requireActiveOrder(long orderId) {
        Order order = ordersById.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("order not found: " + orderId);
        }
        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.REPLACED) {
            throw new IllegalStateException("order not active: " + orderId + " ("
                    + order.getStatus() + ")");
        }
        if (order.getRemainingQuantity() <= 0) {
            throw new IllegalStateException("order fully filled: " + orderId);
        }
        return order;
    }

    private void validateCollectingPhase() {
        Phase phase = scheduler.getPhase();
        if (phase != Phase.OPEN_AUCTION && phase != Phase.CLOSE_AUCTION) {
            throw new IllegalStateException(
                    "orders are only accepted during auctions, current phase: " + phase);
        }
    }

    private void validateOrderRequest(OrderRequest request) {
        if (request.side() == null) {
            throw new IllegalArgumentException("side is required");
        }
        if (request.type() == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (request.type() == OrderType.LIMIT && request.price() == null) {
            throw new IllegalArgumentException("limit order requires price");
        }
    }

    private void validateReplacement(ReplaceRequest request) {
        if (request.side() == null || request.type() == null) {
            throw new IllegalArgumentException("side and type are required");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (request.type() == OrderType.LIMIT && request.price() == null) {
            throw new IllegalArgumentException("limit order requires price");
        }
    }

    private long resolveEntrantPrice(OrderType type, Double price, Long quantity) {
        if (type == OrderType.MARKET) {
            return 0L;
        }
        return normalizer.toTicks(price);
    }

    private String effectiveSymbol(String requestSymbol) {
        return requestSymbol == null || requestSymbol.isBlank()
                ? properties.getSymbol() : requestSymbol;
    }

    private Double referencePrice() {
        if (properties.getReferencePrice() != null) {
            return properties.getReferencePrice();
        }
        return lastMatchPrice;
    }

    private Long currentReferenceTick() {
        Double reference = referencePrice();
        return reference == null ? null : normalizer.toTicks(reference);
    }

    private PriceLimit buildPriceLimit() {
        return PriceLimit.of(normalizer, referencePrice(),
                properties.getLimitDownRatio(), properties.getLimitUpRatio());
    }

    /** 指示性均衡价：按撮合规则试算但不产生成交/副作用。 */
    private Long computeIndicativePrice() {
        Phase phase = scheduler.getPhase();
        if (phase != Phase.OPEN_AUCTION && phase != Phase.CLOSE_AUCTION) {
            return currentReferenceTick();
        }
        AuctionResult trial = MatchingEngine.match(cloneForTrial(), buildPriceLimit(),
                currentReferenceTick(), properties.getTieBreakByPressure(), phase,
                properties.getSymbol(), tradeIdCounter + 1, System.currentTimeMillis());
        return trial.equilibriumPriceTicks() < 0 ? currentReferenceTick()
                : trial.equilibriumPriceTicks();
    }

    private List<Order> cloneForTrial() {
        List<Order> copies = new ArrayList<>();
        for (Order order : activeOrders()) {
            Order copy = new Order();
            copy.setId(order.getId());
            copy.setSymbol(order.getSymbol());
            copy.setSide(order.getSide());
            copy.setType(order.getType());
            copy.setPriceTicks(order.getPriceTicks());
            copy.setQuantity(order.getQuantity());
            copy.setFilledQuantity(order.getFilledQuantity());
            copy.setArrivalSeq(order.getArrivalSeq());
            copy.setArrivalTimeMillis(order.getArrivalTimeMillis());
            copy.setStatus(order.getStatus());
            copies.add(copy);
        }
        return copies;
    }
}
