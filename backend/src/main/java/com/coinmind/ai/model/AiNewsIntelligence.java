package com.coinmind.ai.model;

import java.time.Instant;
import java.util.List;

public record AiNewsIntelligence(
        String symbol,
        String direction,
        int importance,
        int confidence,
        String horizon,
        String summary,
        List<String> catalysts,
        List<String> risks,
        List<ArticleImpact> articleImpacts,
        String model,
        Instant analyzedAt
) {
    public record ArticleImpact(
            String articleId,
            String title,
            String direction,
            int importance,
            int confidence,
            String horizon,
            String reason
    ) {
    }
}
