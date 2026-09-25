package com.coinmind.news.persistence;

import com.coinmind.news.model.NewsArticle;
import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Repository
@ConditionalOnProperty(
        prefix = "coinmind.persistence",
        name = "enabled",
        havingValue = "true"
)
public class NewsArticleRepository {

    private final DatabaseClient databaseClient;

    public NewsArticleRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Mono<Void> upsert(NewsArticle article) {
        return databaseClient.sql("""
                INSERT INTO news_articles (
                    id,
                    title,
                    source,
                    url,
                    summary,
                    published_at,
                    symbols,
                    sentiment_score
                ) VALUES (
                    :id,
                    :title,
                    :source,
                    :url,
                    :summary,
                    :publishedAt,
                    :symbols,
                    :sentimentScore
                )
                ON CONFLICT (id)
                DO UPDATE SET
                    title = EXCLUDED.title,
                    source = EXCLUDED.source,
                    url = EXCLUDED.url,
                    summary = EXCLUDED.summary,
                    published_at = EXCLUDED.published_at,
                    symbols = EXCLUDED.symbols,
                    sentiment_score = EXCLUDED.sentiment_score
                """)
                .bind("id", article.id())
                .bind("title", article.title())
                .bind("source", article.source())
                .bind("url", nullable(article.url()))
                .bind("summary", nullable(article.summary()))
                .bind("publishedAt", article.publishedAt())
                .bind("symbols", String.join(",", article.symbols()))
                .bind("sentimentScore", article.sentimentScore())
                .then();
    }

    public Flux<NewsArticle> findRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));

        return databaseClient.sql("""
                SELECT
                    id,
                    title,
                    source,
                    url,
                    summary,
                    published_at,
                    symbols,
                    sentiment_score
                FROM news_articles
                ORDER BY published_at DESC
                LIMIT :limit
                """)
                .bind("limit", safeLimit)
                .map((row, metadata) -> map(row))
                .all();
    }

    private NewsArticle map(Row row) {
        return new NewsArticle(
                row.get("id", String.class),
                row.get("title", String.class),
                row.get("source", String.class),
                row.get("url", String.class),
                row.get("summary", String.class),
                row.get("published_at", Instant.class),
                parseSymbols(row.get("symbols", String.class)),
                value(row.get("sentiment_score", BigDecimal.class))
        );
    }

    private List<String> parseSymbols(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }
}
