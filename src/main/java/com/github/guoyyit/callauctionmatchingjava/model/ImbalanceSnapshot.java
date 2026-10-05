package com.github.guoyyit.callauctionmatchingjava.model;

/**
 * 买卖不平衡指示快照。
 *
 * <p>披露内容严格受配置粒度控制：{@code disclosed} 为 false 时表示当前
 * 不允许发布，调用方不得展示任何簿内数量与价格信息。数量在发布前
 * 已按配置步长取整，绝不暴露精确的未成交委托量。</p>
 */
public record ImbalanceSnapshot(
        String symbol,
        Phase phase,
        boolean disclosed,
        String direction,
        long roundedImbalanceQuantity,
        long roundedReferenceBuyQuantity,
        long roundedReferenceSellQuantity,
        Long referencePriceTicks,
        long snapshotSeq
) {

    public static ImbalanceSnapshot hidden(String symbol, Phase phase) {
        return new ImbalanceSnapshot(symbol, phase, false, "NONE", 0L, 0L, 0L, null, 0L);
    }
}
