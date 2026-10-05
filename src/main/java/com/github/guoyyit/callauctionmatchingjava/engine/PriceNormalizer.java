package com.github.guoyyit.callauctionmatchingjava.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 价格归一化：货币价格与最小变动价位整数倍(tick)之间的确定性转换。
 *
 * <p>撮合引擎内部只使用 long 型 tick 数，彻底避免 double 浮点误差。
 * 委托价归一化采用四舍五入({@link RoundingMode#HALF_UP})到最近的合法价位。</p>
 */
public class PriceNormalizer {

    private final BigDecimal tickSize;

    public PriceNormalizer(double tickSize) {
        if (tickSize <= 0) {
            throw new IllegalArgumentException("tickSize must be positive: " + tickSize);
        }
        this.tickSize = BigDecimal.valueOf(tickSize);
    }

    /** 货币价格 -> tick 数(四舍五入到最近合法价位)。 */
    public long toTicks(double price) {
        if (price < 0) {
            throw new IllegalArgumentException("price must not be negative: " + price);
        }
        return BigDecimal.valueOf(price)
                .divide(tickSize, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** 货币价格 -> tick 数，指定舍入方式(涨跌停截断时使用)。 */
    public long toTicks(double price, RoundingMode mode) {
        return BigDecimal.valueOf(price)
                .divide(tickSize, 0, mode)
                .longValueExact();
    }

    /** tick 数 -> 货币价格。 */
    public BigDecimal toPrice(long ticks) {
        return tickSize.multiply(BigDecimal.valueOf(ticks));
    }

    /** tick 数 -> double 货币价格(仅用于对外展示)。 */
    public double toPriceDouble(long ticks) {
        return toPrice(ticks).doubleValue();
    }

    public BigDecimal getTickSize() {
        return tickSize;
    }

    /** 判断给定 tick 数是否落在合法价位上(始终为非负整数，构造时已保证)。 */
    public boolean isValidTick(long ticks) {
        return ticks >= 0;
    }
}
