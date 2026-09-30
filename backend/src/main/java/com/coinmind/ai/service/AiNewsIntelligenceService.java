package com.coinmind.ai.service;

import com.coinmind.ai.model.AiNewsIntelligence;
import com.coinmind.ai.provider.AiProvider;
import com.coinmind.news.model.NewsArticle;
import com.coinmind.news.service.NewsService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class AiNewsIntelligenceService {

    private final NewsService newsService;
    private final AiProvider aiProvider;

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

        return aiProvider.analyzeNews(symbol.toUpperCase(), articles);
    }
}
