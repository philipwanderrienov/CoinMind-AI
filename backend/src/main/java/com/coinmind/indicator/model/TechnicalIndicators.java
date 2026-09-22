package com.coinmind.indicator.model;

import java.math.BigDecimal;
import java.time.Instant;

public record TechnicalIndicators(
        String symbol,
        String interval,
        BigDecimal lastPrice,
        BigDecimal ema20,
        BigDecimal ema50,
        BigDecimal ema200,
        BigDecimal rsi14,
        BigDecimal macd,
        BigDecimal macdSignal,
        BigDecimal macdHistogram,
        BigDecimal atr14,
        BigDecimal bollingerMiddle,
        BigDecimal bollingerUpper,
        BigDecimal bollingerLower,
        BigDecimal volumeRatio,
        BigDecimal trendScore,
        BigDecimal momentumScore,
        BigDecimal volatilityScore,
        Instant calculatedAt
) {
}
