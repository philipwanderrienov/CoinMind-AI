package com.coinmind.ai.model;

import java.time.Instant;
import java.util.List;

public record AiPolymarketIntelligence(
        String symbol,
        String analysisState,
        String direction,
        int importance,
        int confidence,
        String horizon,
        String summary,
        List<String> catalysts,
        List<String> risks,
        List<String> contradictions,
        int materialityScore,
        String deterministicBias,
        int sourceMarketCount,
        String model,
        String reason,
        Instant analyzedAt
) {
}
