package com.github.guoyyit.callauctionmatchingjava.config;

import com.github.guoyyit.callauctionmatchingjava.core.model.ImbalanceLevel;
import com.github.guoyyit.callauctionmatchingjava.core.model.UnfilledPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "call-auction")
public class AuctionProperties {

    private String symbol = "SECURITY";
    private BigDecimal tickSize = new BigDecimal("0.01");
    private BigDecimal limitChangePercent = new BigDecimal("0.10");
    private long closeWindowStartMillisAfterStart = 0L;
    private long closeWindowDurationMillis = 0L;
    private long randomSeed = 1L;
    private String logFile = "data/commands.log";
    private boolean resetOnStartup = false;
    private UnfilledPolicy defaultUnfilledPolicy = UnfilledPolicy.CARRY_OVER;
    private Imbalance imbalance = new Imbalance();

    public static class Imbalance {
        private ImbalanceLevel level = ImbalanceLevel.DIRECTION_ONLY;
        private long quantityBucketSize = 100L;
        private boolean discloseReferencePrice = false;

        public ImbalanceLevel getLevel() { return level; }
        public void setLevel(ImbalanceLevel level) { this.level = level; }
        public long getQuantityBucketSize() { return quantityBucketSize; }
        public void setQuantityBucketSize(long quantityBucketSize) { this.quantityBucketSize = quantityBucketSize; }
        public boolean isDiscloseReferencePrice() { return discloseReferencePrice; }
        public void setDiscloseReferencePrice(boolean discloseReferencePrice) { this.discloseReferencePrice = discloseReferencePrice; }
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public BigDecimal getTickSize() { return tickSize; }
    public void setTickSize(BigDecimal tickSize) { this.tickSize = tickSize; }
    public BigDecimal getLimitChangePercent() { return limitChangePercent; }
    public void setLimitChangePercent(BigDecimal limitChangePercent) { this.limitChangePercent = limitChangePercent; }
    public long getCloseWindowStartMillisAfterStart() { return closeWindowStartMillisAfterStart; }
    public void setCloseWindowStartMillisAfterStart(long value) { this.closeWindowStartMillisAfterStart = value; }
    public long getCloseWindowDurationMillis() { return closeWindowDurationMillis; }
    public void setCloseWindowDurationMillis(long value) { this.closeWindowDurationMillis = value; }
    public long getRandomSeed() { return randomSeed; }
    public void setRandomSeed(long randomSeed) { this.randomSeed = randomSeed; }
    public String getLogFile() { return logFile; }
    public void setLogFile(String logFile) { this.logFile = logFile; }
    public boolean isResetOnStartup() { return resetOnStartup; }
    public void setResetOnStartup(boolean resetOnStartup) { this.resetOnStartup = resetOnStartup; }
    public UnfilledPolicy getDefaultUnfilledPolicy() { return defaultUnfilledPolicy; }
    public void setDefaultUnfilledPolicy(UnfilledPolicy defaultUnfilledPolicy) { this.defaultUnfilledPolicy = defaultUnfilledPolicy; }
    public Imbalance getImbalance() { return imbalance; }
    public void setImbalance(Imbalance imbalance) { this.imbalance = imbalance; }
}
