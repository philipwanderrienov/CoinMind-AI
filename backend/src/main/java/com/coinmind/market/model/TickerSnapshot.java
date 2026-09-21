package com.coinmind.market.model;

import java.math.BigDecimal;
import java.time.Instant;

public record TickerSnapshot(
        String symbol,
        BigDecimal closePrice,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal baseVolume,
        BigDecimal quoteVolume,
        Instant eventTime
) {
}
