package com.coinmind.ai.event;

import com.coinmind.news.model.NewsArticle;

public record HighRelevanceNewsEvent(
        NewsArticle article
) {
}
