package com.github.guoyyit.callauctionmatchingjava.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 涨跌停价格区间。
 *
 * <p>以参考价为基础：涨停价 = roundDown(refPrice * (1+upRatio)) 到合法价位，
 * 跌停价 = roundUp(refPrice * (1-downRatio))，保证区间不会因舍入而放宽。
 * 参考价为 {@code null} 时表示不设限制。</p>
 */
public class PriceLimit {

    private final PriceNormalizer normalizer;
    private final long lowerTick;
    private final long upperTick;

    private PriceLimit(PriceNormalizer normalizer, long lowerTick, long upperTick) {
        this.normalizer = normalizer;
        this.lowerTick = lowerTick;
        this.upperTick = upperTick;
    }

    /** 基于货币参考价构造涨跌停区间。 */
    public static PriceLimit of(PriceNormalizer normalizer, Double referencePrice,
                                double downRatio, double upRatio) {
        if (referencePrice == null) {
            return unbounded(normalizer);
        }
        BigDecimal ref = BigDecimal.valueOf(referencePrice);
        long upper = normalizer.toTicks(
                ref.multiply(BigDecimal.valueOf(1.0d + upRatio)).doubleValue(),
                RoundingMode.DOWN);
        long lower = normalizer.toTicks(
                ref.multiply(BigDecimal.valueOf(1.0d - downRatio)).doubleValue(),
                RoundingMode.UP);
        if (lower > upper) {
            throw new IllegalStateException("computed price limit inverted: " + lower + " > " + upper);
        }
        return new PriceLimit(normalizer, lower, upper);
    }

    /** 不设涨跌停限制。 */
    public static PriceLimit unbounded(PriceNormalizer normalizer) {
        return new PriceLimit(normalizer, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    public boolean isBounded() {
        return lowerTick != Long.MIN_VALUE || upperTick != Long.MAX_VALUE;
    }

    public long getLowerTick() {
        return lowerTick;
    }

    public long getUpperTick() {
        return upperTick;
    }

    /** 价格是否在区间内(含边界)。 */
    public boolean contains(long priceTicks) {
        return priceTicks >= lowerTick && priceTicks <= upperTick;
    }

    /** 将越界价格夹持到区间内的合法价位，返回夹持后的 tick 数。 */
    public long clamp(long priceTicks) {
        if (!isBounded()) {
            return priceTicks;
        }
        return Math.max(lowerTick, Math.min(upperTick, priceTicks));
    }

    /**
     * 判断一笔限价委托的价格是否被区间压制。
     *
     * @return 0=未被压制；1=买入价高于涨停(视为涨停价买入)；-1=卖出价低于跌停
     */
    public int suppressionFlag(long priceTicks) {
        if (!isBounded()) {
            return 0;
        }
        if (priceTicks > upperTick) {
            return 1;
        }
        if (priceTicks < lowerTick) {
            return -1;
        }
        return 0;
    }

    /** 将被压制的委托价重新归类到区间边界价。 */
    public long repriceSuppressed(long priceTicks) {
        return clamp(priceTicks);
    }

    @Override
    public String toString() {
        if (!isBounded()) {
            return "PriceLimit[unbounded]";
        }
        return "PriceLimit[" + normalizer.toPriceDouble(lowerTick)
                + " .. " + normalizer.toPriceDouble(upperTick) + "]";
    }
}
