package com.github.guoyyit.callauctionmatchingjava.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.github.guoyyit.callauctionmatchingjava.config.AuctionProperties.TieBreakByPressure;
import com.github.guoyyit.callauctionmatchingjava.model.AuctionResult;
import com.github.guoyyit.callauctionmatchingjava.model.Order;
import com.github.guoyyit.callauctionmatchingjava.model.OrderType;
import com.github.guoyyit.callauctionmatchingjava.model.Phase;
import com.github.guoyyit.callauctionmatchingjava.model.Side;
import com.github.guoyyit.callauctionmatchingjava.model.Trade;

/**
 * 集合竞价撮合引擎(无状态)。
 *
 * <p>撮合价确定规则(依次使用，前一档分出胜负即停止)：</p>
 * <ol>
 *   <li>最大可成交量：在每个候选价上计算可成交的总数量，取最大者；</li>
 *   <li>最小剩余未匹配量：可成交量相同的价格中，取该价位上
 *       |剩余买量 - 剩余卖量| 最小者；</li>
 *   <li>市场压力 + 参考价距离：仍平局时，若买方剩余量大于卖方剩余量
 *       (上行压力)取离参考价较近者，参考价之上取更高价；卖方压力反之取低价；
 *       完全平衡时取不高于参考价的最高价。</li>
 * </ol>
 *
 * <p>候选价集合 = 全部有效限价委托价格的归一化集合(被涨跌停压制的委托已在
 * 入场阶段重定价到边界价)；若只有市价委托，则以参考价作为唯一候选价。
 * 撮合价最终夹持到涨跌停区间内。成交量计算时市价单视为任意价格可成交。</p>
 */
public class MatchingEngine {

    private MatchingEngine() {
    }

    /**
     * 执行一场集合竞价撮合。
     *
     * @param activeOrders     当前簿内全部有效委托(未撤/未改/仍有余量)
     * @param limit            涨跌停区间
     * @param referenceTick    参考价(tick)，无参考价时为 null
     * @param pressureRule     平局时市场压力方向规则
     * @param phase            竞价阶段
     * @param symbol           证券代码
     * @param firstTradeId     成交 ID 起始值(保证确定性分配)
     * @param matchTimeMillis  撮合时刻
     */
    public static AuctionResult match(List<Order> activeOrders,
                                      PriceLimit limit,
                                      Long referenceTick,
                                      TieBreakByPressure pressureRule,
                                      Phase phase,
                                      String symbol,
                                      long firstTradeId,
                                      long matchTimeMillis) {
        List<Order> buyers = new ArrayList<>();
        List<Order> sellers = new ArrayList<>();
        for (Order order : activeOrders) {
            if (order.getRemainingQuantity() <= 0) {
                continue;
            }
            if (order.getType() == OrderType.LIMIT && limit.isBounded()
                    && !limit.contains(order.getPriceTicks())) {
                order.setPriceTicks(limit.clamp(order.getPriceTicks()));
                order.setPriceLimited(true);
            }
            if (order.getSide() == Side.BUY) {
                buyers.add(order);
            } else {
                sellers.add(order);
            }
        }
        buyers.sort(Comparator.comparingLong(Order::getArrivalSeq));
        sellers.sort(Comparator.comparingLong(Order::getArrivalSeq));

        List<Long> candidates = collectCandidatePrices(buyers, sellers, referenceTick, limit);
        if (candidates.isEmpty()) {
            return AuctionResult.noTrade(symbol, phase);
        }

        CandidateEvaluation best = null;
        for (Long candidate : candidates) {
            CandidateEvaluation evaluation = evaluate(candidate, buyers, sellers);
            if (best == null || prefer(evaluation, best, referenceTick, pressureRule)) {
                best = evaluation;
            }
        }

        long equilibrium = best.price;
        boolean adjusted = false;
        if (limit.isBounded() && !limit.contains(equilibrium)) {
            equilibrium = limit.clamp(equilibrium);
            adjusted = true;
            best = evaluate(equilibrium, buyers, sellers);
        }

        if (best.matchedQuantity <= 0) {
            return AuctionResult.noTrade(symbol, phase);
        }

        List<Trade> trades = buildTrades(equilibrium, buyers, sellers,
                firstTradeId, matchTimeMillis, limit);
        return new AuctionResult(symbol, phase, equilibrium, best.matchedQuantity,
                best.remainingBuy, best.remainingSell, best.pressure(),
                adjusted, trades);
    }

    private static List<Long> collectCandidatePrices(List<Order> buyers, List<Order> sellers,
                                                     Long referenceTick, PriceLimit limit) {
        List<Long> candidates = new ArrayList<>();
        for (Order order : buyers) {
            addCandidate(candidates, order, limit);
        }
        for (Order order : sellers) {
            addCandidate(candidates, order, limit);
        }
        candidates.sort(Long::compareTo);
        if (candidates.isEmpty() && referenceTick != null) {
            long bounded = limit.isBounded() ? limit.clamp(referenceTick) : referenceTick;
            candidates.add(bounded);
        }
        return candidates;
    }

    private static void addCandidate(List<Long> candidates, Order order, PriceLimit limit) {
        if (order.getType() != OrderType.LIMIT) {
            return;
        }
        long price = order.getPriceTicks();
        if (limit.isBounded() && !limit.contains(price)) {
            price = limit.clamp(price);
        }
        if (!candidates.contains(price)) {
            candidates.add(price);
        }
    }

    /**
     * 评估某个候选价上的供需。
     * 买入有效量：市价单全部 + 限价 >= 候选价；卖出有效量：市价单全部 + 限价 <= 候选价。
     */
    private static CandidateEvaluation evaluate(long price, List<Order> buyers, List<Order> sellers) {
        long buyDemand = 0L;
        long sellSupply = 0L;
        for (Order order : buyers) {
            if (isBuyTradableAt(order, price)) {
                buyDemand += order.getRemainingQuantity();
            }
        }
        for (Order order : sellers) {
            if (isSellTradableAt(order, price)) {
                sellSupply += order.getRemainingQuantity();
            }
        }
        long matched = Math.min(buyDemand, sellSupply);
        long remainingBuy = Math.max(0L, buyDemand - matched);
        long remainingSell = Math.max(0L, sellSupply - matched);
        long residual = Math.abs(buyDemand - sellSupply);
        return new CandidateEvaluation(price, matched, residual, remainingBuy, remainingSell);
    }

    private static boolean isBuyTradableAt(Order order, long price) {
        return order.getType() == OrderType.MARKET || order.getPriceTicks() >= price;
    }

    private static boolean isSellTradableAt(Order order, long price) {
        return order.getType() == OrderType.MARKET || order.getPriceTicks() <= price;
    }

    /**
     * 三档平局消解：返回 true 表示 candidate 优于 current。
     */
    private static boolean prefer(CandidateEvaluation candidate, CandidateEvaluation current,
                                  Long referenceTick, TieBreakByPressure pressureRule) {
        if (candidate.matchedQuantity != current.matchedQuantity) {
            return candidate.matchedQuantity > current.matchedQuantity;
        }
        if (candidate.residual != current.residual) {
            return candidate.residual < current.residual;
        }
        return resolveByPressureAndDistance(candidate, current, referenceTick, pressureRule);
    }

    private static boolean resolveByPressureAndDistance(CandidateEvaluation candidate,
                                                        CandidateEvaluation current,
                                                        Long referenceTick,
                                                        TieBreakByPressure pressureRule) {
        int pressure = current.pressure();
        if (pressureRule == TieBreakByPressure.BUY_PRESSURE_HIGH && pressure > 0) {
            return candidate.price > current.price;
        }
        if (pressureRule == TieBreakByPressure.SELL_PRESSURE_LOW && pressure < 0) {
            return candidate.price < current.price;
        }

        if (pressure > 0) {
            Long reference = referenceTick != null ? referenceTick : current.price;
            long candidateDistance = Math.abs(candidate.price - reference);
            long currentDistance = Math.abs(current.price - reference);
            if (candidateDistance != currentDistance) {
                return candidateDistance < currentDistance;
            }
            return candidate.price > current.price;
        }
        if (pressure < 0) {
            Long reference = referenceTick != null ? referenceTick : current.price;
            long candidateDistance = Math.abs(candidate.price - reference);
            long currentDistance = Math.abs(current.price - reference);
            if (candidateDistance != currentDistance) {
                return candidateDistance < currentDistance;
            }
            return candidate.price < current.price;
        }
        if (referenceTick != null) {
            boolean candidateBelow = candidate.price <= referenceTick;
            boolean currentBelow = current.price <= referenceTick;
            if (candidateBelow != currentBelow) {
                return candidateBelow;
            }
            if (candidateBelow) {
                return candidate.price > current.price;
            }
            return candidate.price < current.price;
        }
        return candidate.price < current.price;
    }

    /**
     * 按到达顺序生成逐笔成交：买、卖双方各自的指针顺序消费，
     * 只有在撮合价上可成交的委托参与。被涨跌停边界压制的委托同样参与。
     */
    private static List<Trade> buildTrades(long price, List<Order> buyers, List<Order> sellers,
                                           long firstTradeId, long matchTimeMillis,
                                           PriceLimit limit) {
        List<Order> tradableBuyers = new ArrayList<>();
        List<Order> tradableSellers = new ArrayList<>();
        for (Order order : buyers) {
            if (isBuyTradableAt(order, price)) {
                tradableBuyers.add(order);
            }
        }
        for (Order order : sellers) {
            if (isSellTradableAt(order, price)) {
                tradableSellers.add(order);
            }
        }

        List<Trade> trades = new ArrayList<>();
        long tradeId = firstTradeId;
        int buyIndex = 0;
        int sellIndex = 0;
        while (buyIndex < tradableBuyers.size() && sellIndex < tradableSellers.size()) {
            Order buyer = tradableBuyers.get(buyIndex);
            Order seller = tradableSellers.get(sellIndex);
            long buyRemaining = buyer.getRemainingQuantity();
            long sellRemaining = seller.getRemainingQuantity();
            if (buyRemaining <= 0) {
                buyIndex++;
                continue;
            }
            if (sellRemaining <= 0) {
                sellIndex++;
                continue;
            }
            long cross = Math.min(buyRemaining, sellRemaining);
            Trade trade = new Trade();
            trade.setId(tradeId++);
            trade.setBuyerOrderId(buyer.getId());
            trade.setSellerOrderId(seller.getId());
            trade.setPriceTicks(price);
            trade.setQuantity(cross);
            trade.setTimeMillis(matchTimeMillis);
            trades.add(trade);

            buyer.setFilledQuantity(buyer.getFilledQuantity() + cross);
            seller.setFilledQuantity(seller.getFilledQuantity() + cross);
            if (buyer.getRemainingQuantity() == 0) {
                buyIndex++;
            }
            if (seller.getRemainingQuantity() == 0) {
                sellIndex++;
            }
        }
        return trades;
    }

    private record CandidateEvaluation(long price, long matchedQuantity, long residual,
                                       long remainingBuy, long remainingSell) {

        int pressure() {
            return Long.compare(remainingBuy, remainingSell);
        }
    }
}
