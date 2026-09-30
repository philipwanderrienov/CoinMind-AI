package com.coinmind.ai.provider;

import com.coinmind.ai.model.AiAnalysisResult;
import com.coinmind.ai.model.AiDecisionReview;
import com.coinmind.ai.model.AiNewsIntelligence;
import com.coinmind.ai.model.MarketContext;
import com.coinmind.news.model.NewsArticle;
import com.coinmind.trade.model.TradeSetup;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Component
public class NoOpAiProvider implements AiProvider {

    @Override
    public String name() {
        return "none";
    }

    @Override
    public Mono<AiAnalysisResult> analyze(MarketContext context, String triggerType) {
        return Mono.just(new AiAnalysisResult(
                context.symbol(),
                context.interval(),
                "UNAVAILABLE",
                0,
                "AI provider is not configured yet.",
                List.of(),
                List.of("No AI provider configured"),
                name(),
                Instant.now()
        ));
    }
    @Override
    public Mono<AiDecisionReview> reviewDecision(
            TradeSetup setup,
            MarketContext context
    ) {
        return Mono.just(new AiDecisionReview(
                setup.symbol(),
                setup.action(),
                "UNAVAILABLE",
                0,
                "AI provider is not configured yet.",
                List.of(),
                List.of("No AI provider configured"),
                name(),
                Instant.now()
        ));
    }

    @Override
    public Mono<AiNewsIntelligence> analyzeNews(
            String symbol,
            List<NewsArticle> articles
    ) {
        return Mono.just(new AiNewsIntelligence(
                symbol,
                "NEUTRAL",
                0,
                0,
                "NONE",
                "AI provider is not configured yet.",
                List.of(),
                List.of("No AI provider configured"),
                List.of(),
                name(),
                Instant.now()
        ));
    }

}
