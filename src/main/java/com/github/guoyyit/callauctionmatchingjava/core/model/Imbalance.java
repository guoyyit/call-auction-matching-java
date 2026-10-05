package com.github.guoyyit.callauctionmatchingjava.core.model;

public record Imbalance(
        ImbalanceLevel level,
        Side dominantSide,
        long indicativeMatchedQuantity,
        long imbalanceQuantity,
        long disclosedBuyQuantity,
        long disclosedSellQuantity,
        boolean referencePriceDisclosed,
        Long referencePriceTick
) {
}
