package com.coinmind.ai.provider;

import com.coinmind.ai.model.AiAnalysisResult;
import com.coinmind.ai.model.MarketContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Component
@Primary
@ConditionalOnProperty(
        prefix = "coinmind.ai",
        name = "provider",
        havingValue = "mock"
)
public class MockAiProvider implements AiProvider {

    @Override
    public String name() {
        return "mock-signal-engine";
    }

    @Override
    public Mono<AiAnalysisResult> analyze(
            MarketContext context,
            String triggerType
    ) {
        var signal = context.signal();
        int confidence = Math.max(
                35,
                Math.min(95, signal.strength().intValue() + 35)
        );

        List<String> supporting = signal.reasons().stream()
                .limit(4)
                .toList();

        List<String> risks = "HIGH".equals(signal.riskLevel())
                ? List.of("Elevated volatility may invalidate the directional signal")
                : List.of("Mock analysis: validate with live Luna before production use");

        String summary = "Mock analysis generated from CoinMind deterministic signal score "
                + signal.score()
                + " with "
                + signal.bias().toLowerCase()
                + " bias.";

        return Mono.just(new AiAnalysisResult(
                context.symbol(),
                context.interval(),
                signal.bias(),
                confidence,
                summary,
                supporting,
                risks,
                name(),
                Instant.now()
        ));
    }
}
