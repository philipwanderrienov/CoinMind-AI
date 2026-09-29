package com.coinmind.ai.provider;

import com.coinmind.ai.model.AiAnalysisResult;
import com.coinmind.ai.model.AiDecisionReview;
import com.coinmind.ai.model.MarketContext;
import com.coinmind.trade.model.TradeSetup;
import reactor.core.publisher.Mono;

public interface AiProvider {

    String name();

    Mono<AiAnalysisResult> analyze(MarketContext context, String triggerType);

    Mono<AiDecisionReview> reviewDecision(TradeSetup setup, MarketContext context);

    default Mono<AiAnalysisResult> analyze(MarketContext context) {
        return analyze(context, "MANUAL");
    }
}
