package com.coinmind.ai.service;

import com.coinmind.ai.model.AiNewsIntelligence;
import com.coinmind.ai.provider.AiProvider;
import com.coinmind.news.model.NewsArticle;
import com.coinmind.news.service.NewsService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiNewsIntelligenceService {

    private static final Duration FRESHNESS = Duration.ofHours(2);

    private final NewsService newsService;
    private final AiProvider aiProvider;
    private final Map<String, AiNewsIntelligence> latestBySymbol = new ConcurrentHashMap<>();

    public AiNewsIntelligenceService(
            NewsService newsService,
            AiProvider aiProvider
    ) {
        this.newsService = newsService;
        this.aiProvider = aiProvider;
    }

    public Mono<AiNewsIntelligence> analyze(String symbol) {
        List<NewsArticle> articles = newsService.recentForSymbol(symbol, 5);

        if (articles.isEmpty()) {
            return Mono.just(new AiNewsIntelligence(
                    symbol.toUpperCase(),
                    "NEUTRAL",
                    0,
                    0,
                    "NONE",
                    "Belum ada berita relevan yang cukup untuk dianalisis.",
                    List.of(),
                    List.of(),
                    List.of(),
                    aiProvider.name(),
                    java.time.Instant.now()
            ));
        }

        String normalized = symbol.toUpperCase();

        return aiProvider.analyzeNews(normalized, articles)
                .doOnNext(result -> latestBySymbol.put(normalized, result));
    }

    public AiNewsIntelligence latestFresh(String symbol) {
        AiNewsIntelligence intelligence = latestBySymbol.get(symbol.toUpperCase());

        if (intelligence == null || intelligence.analyzedAt() == null) {
            return null;
        }

        return intelligence.analyzedAt()
                .plus(FRESHNESS)
                .isAfter(Instant.now())
                ? intelligence
                : null;
    }
}
