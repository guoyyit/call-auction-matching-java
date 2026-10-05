package com.github.guoyyit.callauctionmatchingjava.service;

import java.util.Random;

import com.github.guoyyit.callauctionmatchingjava.config.AuctionProperties;
import com.github.guoyyit.callauctionmatchingjava.model.Phase;

/**
 * 竞价阶段调度器。
 *
 * <p>阶段：开盘竞价({@link Phase#OPEN_AUCTION}) → 连续交易占位
 * ({@link Phase#INTRADAY}) → 收盘竞价({@link Phase#CLOSE_AUCTION}) →
 * 已关闭({@link Phase#CLOSED})。收盘竞价的结束时刻在配置窗口
 * {@code [closeWindowStart, closeWindowStart+closeWindowLength)} 内
 * 使用可注入种子的 {@link Random} 均匀采样，相同种子逐次一致。</p>
 *
 * <p>为便于本地确定性验证，时钟可注入({@link ClockMillis})；生产实现
 * 返回系统墙钟，测试可固定时间线。</p>
 */
public class PhaseScheduler {

    @FunctionalInterface
    public interface ClockMillis {
        long now();
    }

    private final AuctionProperties properties;
    private final Random closeRandom;
    private final ClockMillis clock;

    private volatile Phase phase = Phase.OPEN_AUCTION;
    private volatile long sessionStartMillis;
    private volatile Long scheduledCloseEndOffsetMillis;

    public PhaseScheduler(AuctionProperties properties, ClockMillis clock) {
        this.properties = properties;
        this.closeRandom = new Random(properties.getRandomSeed());
        this.clock = clock;
        this.sessionStartMillis = clock.now();
    }

    /** 恢复阶段状态：不重新采样收盘结束时刻，由恢复数据注入。 */
    public synchronized void restore(Phase restoredPhase, long sessionStartMillis,
                                     Long closeEndOffsetMillis, long randomSeedAdvanceCount) {
        this.phase = restoredPhase;
        this.sessionStartMillis = sessionStartMillis;
        this.scheduledCloseEndOffsetMillis = closeEndOffsetMillis;
        for (long i = 0; i < randomSeedAdvanceCount; i++) {
            this.closeRandom.nextLong();
        }
    }

    public synchronized Phase getPhase() {
        return phase;
    }

    public synchronized long getSessionStartMillis() {
        return sessionStartMillis;
    }

    /**
     * 采样本场收盘竞价的结束时刻偏移量(相对会话起点的毫秒数)。
     * 仅在首次进入收盘竞价时调用一次；窗口长度为 0 时退化为窗口起点。
     */
    public synchronized long sampleCloseEndOffsetMillis() {
        long start = properties.getCloseWindowStartMillis();
        long length = properties.getCloseWindowLengthMillis();
        if (length <= 0) {
            scheduledCloseEndOffsetMillis = start;
        } else {
            long offset = (closeRandom.nextLong() & Long.MAX_VALUE) % length;
            scheduledCloseEndOffsetMillis = start + offset;
        }
        return scheduledCloseEndOffsetMillis;
    }

    public synchronized Long getScheduledCloseEndOffsetMillis() {
        return scheduledCloseEndOffsetMillis;
    }

    /** 收盘竞价是否已到达其随机结束时刻。 */
    public synchronized boolean isCloseAuctionDue() {
        if (phase != Phase.CLOSE_AUCTION || scheduledCloseEndOffsetMillis == null) {
            return false;
        }
        return elapsedMillis() >= scheduledCloseEndOffsetMillis;
    }

    /** 距收盘竞价结束的剩余毫秒(可能为负，表示已到期)；未安排时返回 null。 */
    public synchronized Long remainingCloseMillis() {
        if (scheduledCloseEndOffsetMillis == null) {
            return null;
        }
        return scheduledCloseEndOffsetMillis - elapsedMillis();
    }

    public synchronized long elapsedMillis() {
        return clock.now() - sessionStartMillis;
    }

    /** 显式推进阶段。阶段边界处理由 {@code AuctionService} 完成，
     * 本方法只负责状态切换本身，保证边界行为确定。 */
    synchronized Phase transitionTo(Phase next) {
        if (next.ordinal() < phase.ordinal()) {
            throw new IllegalStateException(
                    "cannot move phase backwards: " + phase + " -> " + next);
        }
        this.phase = next;
        return phase;
    }

    public synchronized void resetSession(long newSessionStartMillis) {
        this.sessionStartMillis = newSessionStartMillis;
        this.scheduledCloseEndOffsetMillis = null;
        this.phase = Phase.OPEN_AUCTION;
    }
}
