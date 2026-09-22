package com.coinmind.ai.provider;

import com.coinmind.ai.model.AiAnalysisResult;
import com.coinmind.ai.model.MarketContext;
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
    public Mono<AiAnalysisResult> analyze(MarketContext context) {
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
}
