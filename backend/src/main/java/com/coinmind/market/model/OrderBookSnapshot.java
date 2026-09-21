package com.coinmind.market.model;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderBookSnapshot(
        String symbol,
        BigDecimal bestBidPrice,
        BigDecimal bestBidQuantity,
        BigDecimal bestAskPrice,
        BigDecimal bestAskQuantity,
        BigDecimal spread,
        BigDecimal midPrice,
        Instant eventTime
) {
}
