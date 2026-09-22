package com.coinmind.market.model;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeSnapshot(
        String symbol,
        long tradeId,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal quoteQuantity,
        boolean buyerIsMaker,
        String aggressorSide,
        Instant tradeTime,
        Instant eventTime
) {
}
