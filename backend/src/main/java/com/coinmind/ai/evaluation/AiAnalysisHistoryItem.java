package com.coinmind.ai.evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AiAnalysisHistoryItem(
        String id,
        String symbol,
        String interval,
        String triggerType,
        String marketBias,
        int confidence,
        String summary,
        String model,
        BigDecimal entryPrice,
        BigDecimal signalScore,
        Instant analyzedAt,
        List<Evaluation> evaluations
) {
    public record Evaluation(
            String horizon,
            BigDecimal exitPrice,
            BigDecimal returnPct,
            boolean directionCorrect,
            Instant evaluatedAt
    ) {
    }
}
