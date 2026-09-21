package com.coinmind.ai.model;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketContext(
        String symbol,
        String interval,
        Instant generatedAt,
        PriceContext price,
        TechnicalContext technical,
        MicrostructureContext microstructure
) {

    public record PriceContext(
            BigDecimal lastPrice,
            BigDecimal open24h,
            BigDecimal high24h,
            BigDecimal low24h,
            BigDecimal quoteVolume24h
    ) {
    }

    public record TechnicalContext(
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
            BigDecimal volatilityScore
    ) {
    }

    public record MicrostructureContext(
            BigDecimal bestBid,
            BigDecimal bestAsk,
            BigDecimal spread,
            BigDecimal spreadBps,
            BigDecimal midPrice,
            BigDecimal buyVolume,
            BigDecimal sellVolume,
            BigDecimal buySellRatio
    ) {
    }
}
