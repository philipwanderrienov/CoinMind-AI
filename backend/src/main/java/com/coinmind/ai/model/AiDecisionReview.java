package com.coinmind.ai.model;

import java.time.Instant;
import java.util.List;

public record AiDecisionReview(
        String symbol,
        String engineAction,
        String verdict,
        int confidence,
        String summary,
        List<String> confirmations,
        List<String> concerns,
        String model,
        Instant reviewedAt
) {
}
