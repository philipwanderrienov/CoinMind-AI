package com.coinmind.market.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Candlestick(
        String symbol,
        String interval,
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal baseVolume,
        BigDecimal quoteVolume,
        long tradeCount,
        boolean closed,
        Instant eventTime
) {
    public String key() {
        return symbol + ":" + interval;
    }
}
