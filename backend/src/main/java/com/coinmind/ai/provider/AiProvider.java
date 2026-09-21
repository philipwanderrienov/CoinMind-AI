package com.coinmind.ai.provider;

import com.coinmind.ai.model.AiAnalysisResult;
import com.coinmind.ai.model.MarketContext;
import reactor.core.publisher.Mono;

public interface AiProvider {

    String name();

    Mono<AiAnalysisResult> analyze(MarketContext context);
}
