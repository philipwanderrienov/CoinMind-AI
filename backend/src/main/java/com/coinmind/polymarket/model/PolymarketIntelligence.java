package com.coinmind.polymarket.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PolymarketIntelligence(
        String symbol,
        String bias,
        int confidence,
        int importance,
        int marketCount,
        BigDecimal averageProbabilityChange,
        String topEvent,
        List<MarketSignal> signals,
        Instant updatedAt
) {
    public record MarketSignal(
            String eventId,
            String marketId,
            String question,
            BigDecimal yesProbability,
            BigDecimal oneDayProbabilityChange,
            BigDecimal liquidity,
            BigDecimal volume24h,
            String directionalBias,
            int relevanceScore
    ) {
    }
}
