package com.coinmind.ai.service;

import com.coinmind.ai.model.AiAnalysisResult;
import com.coinmind.ai.provider.AiProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AiAnalysisService {

    private final MarketContextBuilder contextBuilder;
    private final AiProvider aiProvider;

    public AiAnalysisService(
            MarketContextBuilder contextBuilder,
            AiProvider aiProvider
    ) {
        this.contextBuilder = contextBuilder;
        this.aiProvider = aiProvider;
    }

    public Mono<AiAnalysisResult> analyze(String symbol, String interval) {
        return Mono.fromSupplier(() -> contextBuilder.build(symbol, interval))
                .flatMap(aiProvider::analyze);
    }
}
