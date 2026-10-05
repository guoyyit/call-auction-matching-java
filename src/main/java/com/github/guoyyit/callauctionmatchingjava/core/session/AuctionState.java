package com.github.guoyyit.callauctionmatchingjava.core.session;

import com.github.guoyyit.callauctionmatchingjava.core.engine.OrderBook;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionKind;
import com.github.guoyyit.callauctionmatchingjava.core.model.AuctionResult;
import com.github.guoyyit.callauctionmatchingjava.core.model.Order;
import com.github.guoyyit.callauctionmatchingjava.core.model.Phase;
import com.github.guoyyit.callauctionmatchingjava.core.model.UnfilledPolicy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuctionState {

    private Phase phase = Phase.IDLE;
    private AuctionKind kind;
    private String auctionId;
    private long auctionCount;
    private BigDecimal tickSize;
    private Long referenceTick;
    private Long lowerLimitTick;
    private Long upperLimitTick;
    private UnfilledPolicy unfilledPolicy;
    private Long scheduledEndTimestampMillis;
    private long lastCommandSeq;

    private final OrderBook book = new OrderBook();
    private final List<String> arrivalOrderIds = new ArrayList<>();
    private final Map<String, Order> allOrders = new LinkedHashMap<>();
    private final List<AuctionResult> results = new ArrayList<>();

    public Phase getPhase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }
    public AuctionKind getKind() { return kind; }
    public void setKind(AuctionKind kind) { this.kind = kind; }
    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }
    public long getAuctionCount() { return auctionCount; }
    public void setAuctionCount(long auctionCount) { this.auctionCount = auctionCount; }
    public BigDecimal getTickSize() { return tickSize; }
    public void setTickSize(BigDecimal tickSize) { this.tickSize = tickSize; }
    public Long getReferenceTick() { return referenceTick; }
    public void setReferenceTick(Long referenceTick) { this.referenceTick = referenceTick; }
    public Long getLowerLimitTick() { return lowerLimitTick; }
    public void setLowerLimitTick(Long lowerLimitTick) { this.lowerLimitTick = lowerLimitTick; }
    public Long getUpperLimitTick() { return upperLimitTick; }
    public void setUpperLimitTick(Long upperLimitTick) { this.upperLimitTick = upperLimitTick; }
    public UnfilledPolicy getUnfilledPolicy() { return unfilledPolicy; }
    public void setUnfilledPolicy(UnfilledPolicy unfilledPolicy) { this.unfilledPolicy = unfilledPolicy; }
    public Long getScheduledEndTimestampMillis() { return scheduledEndTimestampMillis; }
    public void setScheduledEndTimestampMillis(Long scheduledEndTimestampMillis) { this.scheduledEndTimestampMillis = scheduledEndTimestampMillis; }
    public long getLastCommandSeq() { return lastCommandSeq; }
    public void setLastCommandSeq(long lastCommandSeq) { this.lastCommandSeq = lastCommandSeq; }
    public OrderBook getBook() { return book; }
    public List<String> getArrivalOrderIds() { return arrivalOrderIds; }
    public Map<String, Order> getAllOrders() { return allOrders; }
    public List<AuctionResult> getResults() { return results; }

    public AuctionResult lastResult() {
        return results.isEmpty() ? null : results.get(results.size() - 1);
    }
}
