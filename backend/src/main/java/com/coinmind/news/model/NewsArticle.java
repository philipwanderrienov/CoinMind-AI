package com.coinmind.news.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record NewsArticle(
        String id,
        String title,
        String source,
        String url,
        String summary,
        Instant publishedAt,
        List<String> symbols,
        BigDecimal sentimentScore
) {
}
