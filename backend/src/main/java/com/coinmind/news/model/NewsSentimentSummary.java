package com.coinmind.news.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record NewsSentimentSummary(
        String symbol,
        int articleCount,
        BigDecimal averageSentiment,
        int positiveCount,
        int neutralCount,
        int negativeCount,
        List<NewsArticle> recentArticles,
        Instant calculatedAt
) {
}
