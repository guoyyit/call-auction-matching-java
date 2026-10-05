package com.github.guoyyit.callauctionmatchingjava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.github.guoyyit.callauctionmatchingjava.model.UnmatchedPolicy;

/**
 * 集合竞价服务全部配置项，前缀 {@code call-auction}。
 */
@ConfigurationProperties(prefix = "call-auction")
public class AuctionProperties {

    /** 证券代码。 */
    private String symbol = "600000";

    /** 最小变动价位(货币单位)，所有委托价与撮合价必须是其整数倍。 */
    private double tickSize = 0.01;

    /** 涨停幅度(小数，如 0.10 表示 10%)。 */
    private double limitUpRatio = 0.10;

    /** 跌停幅度(小数)。 */
    private double limitDownRatio = 0.10;

    /** 外部提供的参考价(货币单位)；为空时上一场竞价成交价作为参考，再无则不限制。 */
    private Double referencePrice = null;

    /** 收盘竞价结束时间窗口起点(相对开盘竞价开始的毫秒数)。 */
    private long closeWindowStartMillis = 10_000L;

    /** 收盘竞价结束时间窗口长度(毫秒)，实际结束时刻在 [start, start+length) 内均匀随机。 */
    private long closeWindowLengthMillis = 5_000L;

    /** 收盘结束时刻随机种子；相同种子产生相同结束时刻。 */
    private long randomSeed = 42L;

    /** 撮合价平局时的市场压力方向规则：BUY_PRESSURE 买方压力取高价，SELL_PRESSURE 取低价。 */
    private TieBreakByPressure tieBreakByPressure = TieBreakByPressure.STANDARD;

    /** 未成交委托在竞价结束后的处理策略。 */
    private UnmatchedPolicy unmatchedPolicy = UnmatchedPolicy.CARRY;

    /** 不平衡信息披露配置。 */
    private Imbalance imbalance = new Imbalance();

    /** 持久化(WAL)目录。 */
    private String journalDir = "data/journal";

    /** 是否在启动时从 WAL 恢复状态。 */
    private boolean recoveryEnabled = true;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getTickSize() {
        return tickSize;
    }

    public void setTickSize(double tickSize) {
        this.tickSize = tickSize;
    }

    public double getLimitUpRatio() {
        return limitUpRatio;
    }

    public void setLimitUpRatio(double limitUpRatio) {
        this.limitUpRatio = limitUpRatio;
    }

    public double getLimitDownRatio() {
        return limitDownRatio;
    }

    public void setLimitDownRatio(double limitDownRatio) {
        this.limitDownRatio = limitDownRatio;
    }

    public Double getReferencePrice() {
        return referencePrice;
    }

    public void setReferencePrice(Double referencePrice) {
        this.referencePrice = referencePrice;
    }

    public long getCloseWindowStartMillis() {
        return closeWindowStartMillis;
    }

    public void setCloseWindowStartMillis(long closeWindowStartMillis) {
        this.closeWindowStartMillis = closeWindowStartMillis;
    }

    public long getCloseWindowLengthMillis() {
        return closeWindowLengthMillis;
    }

    public void setCloseWindowLengthMillis(long closeWindowLengthMillis) {
        this.closeWindowLengthMillis = closeWindowLengthMillis;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public void setRandomSeed(long randomSeed) {
        this.randomSeed = randomSeed;
    }

    public TieBreakByPressure getTieBreakByPressure() {
        return tieBreakByPressure;
    }

    public void setTieBreakByPressure(TieBreakByPressure tieBreakByPressure) {
        this.tieBreakByPressure = tieBreakByPressure;
    }

    public UnmatchedPolicy getUnmatchedPolicy() {
        return unmatchedPolicy;
    }

    public void setUnmatchedPolicy(UnmatchedPolicy unmatchedPolicy) {
        this.unmatchedPolicy = unmatchedPolicy;
    }

    public Imbalance getImbalance() {
        return imbalance;
    }

    public void setImbalance(Imbalance imbalance) {
        this.imbalance = imbalance;
    }

    public String getJournalDir() {
        return journalDir;
    }

    public void setJournalDir(String journalDir) {
        this.journalDir = journalDir;
    }

    public boolean isRecoveryEnabled() {
        return recoveryEnabled;
    }

    public void setRecoveryEnabled(boolean recoveryEnabled) {
        this.recoveryEnabled = recoveryEnabled;
    }

    public enum TieBreakByPressure {
        /** 标准规则：买方剩余多取离参考价近的高价，卖方剩余多取低价。 */
        STANDARD,
        /** 买方压力方向优先取候选集合中的较高价。 */
        BUY_PRESSURE_HIGH,
        /** 卖方压力方向优先取候选集合中的较低价。 */
        SELL_PRESSURE_LOW
    }

    public static class Imbalance {

        /** 是否允许发布不平衡信息。 */
        private boolean publishEnabled = true;

        /** 披露的数量步长(股)，真实不平衡量向下取整到该步长的整数倍；0 表示只披露方向。 */
        private long quantityBucketSize = 100L;

        /** 是否允许披露参考价(tick 数)。 */
        private boolean discloseReferencePrice = true;

        /** 开盘竞价期间是否发布。 */
        private boolean publishInOpenAuction = true;

        /** 收盘竞价期间是否发布。 */
        private boolean publishInCloseAuction = true;

        public boolean isPublishEnabled() {
            return publishEnabled;
        }

        public void setPublishEnabled(boolean publishEnabled) {
            this.publishEnabled = publishEnabled;
        }

        public long getQuantityBucketSize() {
            return quantityBucketSize;
        }

        public void setQuantityBucketSize(long quantityBucketSize) {
            this.quantityBucketSize = quantityBucketSize;
        }

        public boolean isDiscloseReferencePrice() {
            return discloseReferencePrice;
        }

        public void setDiscloseReferencePrice(boolean discloseReferencePrice) {
            this.discloseReferencePrice = discloseReferencePrice;
        }

        public boolean isPublishInOpenAuction() {
            return publishInOpenAuction;
        }

        public void setPublishInOpenAuction(boolean publishInOpenAuction) {
            this.publishInOpenAuction = publishInOpenAuction;
        }

        public boolean isPublishInCloseAuction() {
            return publishInCloseAuction;
        }

        public void setPublishInCloseAuction(boolean publishInCloseAuction) {
            this.publishInCloseAuction = publishInCloseAuction;
        }
    }
}
