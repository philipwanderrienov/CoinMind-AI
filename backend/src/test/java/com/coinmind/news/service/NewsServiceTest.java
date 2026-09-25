package com.coinmind.news.service;

import com.coinmind.news.persistence.NewsArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NewsServiceTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<NewsArticleRepository> repositoryProvider =
            mock(ObjectProvider.class);

    private final NewsService service = new NewsService(
            new NewsSentimentService(),
            repositoryProvider
    );

    @Test
    void tagsBitcoinAndCalculatesPositiveSentiment() {
        service.ingest(
                "1",
                "Bitcoin rally gains momentum after strong inflows",
                "test",
                "https://example.com/1",
                "BTC adoption growth continues",
                Instant.now()
        );

        var summary = service.summarize("BTCUSDT", Duration.ofHours(6), 20);

        assertThat(summary.articleCount()).isEqualTo(1);
        assertThat(summary.positiveCount()).isEqualTo(1);
        assertThat(summary.averageSentiment()).isPositive();
    }

    @Test
    void deduplicatesArticlesById() {
        service.ingest(
                "same",
                "Ethereum gains",
                "test",
                "https://example.com/a",
                "",
                Instant.now()
        );
        service.ingest(
                "same",
                "Ethereum hack warning",
                "test",
                "https://example.com/b",
                "",
                Instant.now()
        );

        assertThat(service.recent(20)).hasSize(1);
        assertThat(service.recentForSymbol("ETHUSDT", 20)).hasSize(1);
    }
}
