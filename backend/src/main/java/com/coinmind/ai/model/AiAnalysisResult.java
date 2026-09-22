package com.coinmind.ai.model;

import java.time.Instant;
import java.util.List;

public record AiAnalysisResult(
        String symbol,
        String interval,
        String marketBias,
        int confidence,
        String summary,
        List<String> supportingFactors,
        List<String> riskFactors,
        String model,
        Instant analyzedAt
) {
}
