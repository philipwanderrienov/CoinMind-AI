package com.coinmind.news.service;

import com.coinmind.news.model.NewsArticle;
import com.coinmind.news.model.NewsSentimentSummary;
import com.coinmind.news.persistence.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final int MAX_ARTICLES = 1000;

    private final NewsSentimentService sentimentService;
    private final ObjectProvider<NewsArticleRepository> repositoryProvider;
    private final Map<String, NewsArticle> articles = new LinkedHashMap<>();

    public NewsService(
            NewsSentimentService sentimentService,
            ObjectProvider<NewsArticleRepository> repositoryProvider
    ) {
        this.sentimentService = sentimentService;
        this.repositoryProvider = repositoryProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restorePersistedNews() {
        repositoryProvider.ifAvailable(repository ->
                repository.findRecent(MAX_ARTICLES)
                        .collectList()
                        .subscribe(
                                persisted -> {
                                    synchronized (this) {
                                        persisted.stream()
                                                .sorted(Comparator.comparing(NewsArticle::publishedAt))
                                                .forEach(article -> articles.put(article.id(), article));
                                        trimToLimit();
                                    }

                                    log.info("Persisted news restored. count={}", persisted.size());
                                },
                                error -> log.warn("Unable to restore persisted news", error)
                        )
        );
    }

    public synchronized void ingest(
            String id,
            String title,
            String source,
            String url,
            String summary,
            Instant publishedAt
    ) {
        if (id == null || id.isBlank() || title == null || title.isBlank()) {
            return;
        }

        NewsArticle article = new NewsArticle(
                id,
                title,
                source == null ? "unknown" : source,
                url,
                summary,
                publishedAt == null ? Instant.now() : publishedAt,
                detectSymbols(title + " " + (summary == null ? "" : summary)),
                sentimentService.score(title, summary)
        );

        articles.put(id, article);
        trimToLimit();

        repositoryProvider.ifAvailable(repository ->
                repository.upsert(article)
                        .subscribe(
                                ignored -> { },
                                error -> log.warn(
                                        "Unable to persist news article. id={}",
                                        article.id(),
                                        error
                                )
                        )
        );
    }

    public synchronized List<NewsArticle> recent(int limit) {
        return articles.values().stream()
                .sorted(Comparator.comparing(NewsArticle::publishedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 100)))
                .toList();
    }

    public synchronized List<NewsArticle> recentForSymbol(String symbol, int limit) {
        String normalized = normalizeBaseSymbol(symbol);

        return articles.values().stream()
                .filter(article -> article.symbols().contains(normalized))
                .sorted(Comparator.comparing(NewsArticle::publishedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 100)))
                .toList();
    }

    public synchronized NewsSentimentSummary summarize(
            String symbol,
            Duration lookback,
            int articleLimit
    ) {
        String normalized = normalizeBaseSymbol(symbol);
        Instant cutoff = Instant.now().minus(lookback);

        List<NewsArticle> relevant = articles.values().stream()
                .filter(article -> article.symbols().contains(normalized))
                .filter(article -> !article.publishedAt().isBefore(cutoff))
                .sorted(Comparator.comparing(NewsArticle::publishedAt).reversed())
                .limit(Math.max(1, Math.min(articleLimit, 100)))
                .toList();

        int positive = 0;
        int neutral = 0;
        int negative = 0;
        BigDecimal total = BigDecimal.ZERO;

        for (NewsArticle article : relevant) {
            total = total.add(article.sentimentScore());

            if (article.sentimentScore().compareTo(BigDecimal.valueOf(0.15)) > 0) {
                positive++;
            } else if (article.sentimentScore().compareTo(BigDecimal.valueOf(-0.15)) < 0) {
                negative++;
            } else {
                neutral++;
            }
        }

        BigDecimal average = relevant.isEmpty()
                ? BigDecimal.ZERO
                : total.divide(
                        BigDecimal.valueOf(relevant.size()),
                        4,
                        RoundingMode.HALF_UP
                );

        return new NewsSentimentSummary(
                normalized,
                relevant.size(),
                average,
                positive,
                neutral,
                negative,
                relevant,
                Instant.now()
        );
    }

    private void trimToLimit() {
        while (articles.size() > MAX_ARTICLES) {
            String oldest = articles.values().stream()
                    .min(Comparator.comparing(NewsArticle::publishedAt))
                    .map(NewsArticle::id)
                    .orElse(null);

            if (oldest == null) {
                break;
            }

            articles.remove(oldest);
        }
    }

    private List<String> detectSymbols(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> symbols = new ArrayList<>();

        if (containsAny(normalized, Set.of("bitcoin", " btc ", "btcusdt"))) {
            symbols.add("BTC");
        }
        if (containsAny(normalized, Set.of("ethereum", " ether ", " eth ", "ethusdt"))) {
            symbols.add("ETH");
        }
        if (containsAny(normalized, Set.of("solana", " sol ", "solusdt"))) {
            symbols.add("SOL");
        }

        return List.copyOf(symbols);
    }

    private boolean containsAny(String text, Set<String> terms) {
        String padded = " " + text + " ";
        return terms.stream().anyMatch(term -> padded.contains(term));
    }

    private String normalizeBaseSymbol(String symbol) {
        return symbol.toUpperCase(Locale.ROOT)
                .replace("USDT", "")
                .replace("USDC", "")
                .replace("USD", "");
    }
}
