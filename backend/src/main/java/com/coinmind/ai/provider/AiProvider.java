package com.coinmind.ai.provider;

import com.coinmind.ai.model.AiAnalysisResult;
import com.coinmind.ai.model.AiDecisionReview;
import com.coinmind.ai.model.AiNewsIntelligence;
import com.coinmind.ai.model.AiPolymarketIntelligence;
import com.coinmind.ai.model.MarketContext;
import com.coinmind.news.model.NewsArticle;
import com.coinmind.polymarket.model.PolymarketIntelligence;

import java.util.List;
import com.coinmind.trade.model.TradeSetup;
import reactor.core.publisher.Mono;

public interface AiProvider {

    String name();

    Mono<AiAnalysisResult> analyze(MarketContext context, String triggerType);

    Mono<AiDecisionReview> reviewDecision(TradeSetup setup, MarketContext context);

    Mono<AiNewsIntelligence> analyzeNews(String symbol, List<NewsArticle> articles);

    Mono<AiPolymarketIntelligence> analyzePolymarket(
            PolymarketIntelligence intelligence,
            int materialityScore
    );

    default Mono<AiPolymarketIntelligence> analyzePolymarketWithModel(
            PolymarketIntelligence intelligence,
            int materialityScore,
            String model
    ) {
        return analyzePolymarket(intelligence, materialityScore);
    }

    default Mono<AiAnalysisResult> analyze(MarketContext context) {
        return analyze(context, "MANUAL");
    }
}
