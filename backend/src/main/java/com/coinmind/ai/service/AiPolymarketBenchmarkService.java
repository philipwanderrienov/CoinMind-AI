package com.coinmind.ai.service;

import com.coinmind.ai.config.AiProperties;
import com.coinmind.ai.model.AiPolymarketBenchmarkResult;
import com.coinmind.ai.model.AiPolymarketIntelligence;
import com.coinmind.ai.provider.AiProvider;
import com.coinmind.polymarket.model.PolymarketIntelligence;
import com.coinmind.polymarket.service.PolymarketIntelligenceService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class AiPolymarketBenchmarkService {

    private final PolymarketIntelligenceService predictionService;
    private final AiProvider aiProvider;
    private final AiProperties properties;

    public AiPolymarketBenchmarkService(
            PolymarketIntelligenceService predictionService,
            AiProvider aiProvider,
            AiProperties properties
    ) {
        this.predictionService = predictionService;
        this.aiProvider = aiProvider;
        this.properties = properties;
    }

    public Mono<AiPolymarketBenchmarkResult> benchmark(String symbol) {
        if (!properties.enabled()) {
            return Mono.error(new IllegalStateException(
                    "AI benchmark requires AI_ENABLED=true."
            ));
        }

        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return Mono.error(new IllegalStateException(
                    "AI benchmark requires a configured API key."
            ));
        }

        PolymarketIntelligence cached = predictionService.latestFresh(symbol);
        Mono<PolymarketIntelligence> context = cached == null
                ? predictionService.refresh(symbol)
                : Mono.just(cached);

        return context.flatMap(intelligence -> {
            if (intelligence.marketCount() <= 0) {
                return Mono.error(new IllegalStateException(
                        "No forecast-market context is available for benchmark."
                ));
            }

            int materiality = materialityScore(intelligence);
            String routineModel = properties.effectiveRoutineModel();
            String deepModel = properties.effectiveDeepModel();

            return timedRun(
                    "ROUTINE",
                    routineModel,
                    intelligence,
                    materiality
            ).flatMap(routine ->
                    timedRun(
                            "DEEP",
                            deepModel,
                            intelligence,
                            materiality
                    ).map(deep -> summarize(
                            intelligence,
                            materiality,
                            routine,
                            deep
                    ))
            );
        });
    }

    private Mono<AiPolymarketBenchmarkResult.BenchmarkRun> timedRun(
            String route,
            String model,
            PolymarketIntelligence intelligence,
            int materiality
    ) {
        return Mono.defer(() -> {
            long startedAt = System.nanoTime();

            return aiProvider.analyzePolymarketWithModel(
                            intelligence,
                            materiality,
                            model
                    )
                    .map(result -> new AiPolymarketBenchmarkResult.BenchmarkRun(
                            route,
                            model,
                            (System.nanoTime() - startedAt) / 1_000_000L,
                            result
                    ));
        });
    }

    private AiPolymarketBenchmarkResult summarize(
            PolymarketIntelligence intelligence,
            int materiality,
            AiPolymarketBenchmarkResult.BenchmarkRun routine,
            AiPolymarketBenchmarkResult.BenchmarkRun deep
    ) {
        String deterministic = intelligence.bias();
        String routineDirection = routine.result().direction();
        String deepDirection = deep.result().direction();

        return new AiPolymarketBenchmarkResult(
                intelligence.symbol(),
                intelligence.source(),
                materiality,
                routine,
                deep,
                routineDirection.equals(deepDirection),
                deterministic.equals(routineDirection),
                deterministic.equals(deepDirection),
                deterministic,
                Instant.now()
        );
    }

    private int materialityScore(PolymarketIntelligence intelligence) {
        int maxRelevance = intelligence.signals().stream()
                .mapToInt(PolymarketIntelligence.MarketSignal::relevanceScore)
                .max()
                .orElse(0);

        int probabilityScore = intelligence.averageProbabilityChange()
                .abs()
                .multiply(BigDecimal.valueOf(2000))
                .intValue();

        int breadthScore = Math.min(40, intelligence.marketCount() * 8);

        return Math.min(
                100,
                Math.max(
                        Math.max(intelligence.importance(), maxRelevance),
                        Math.max(probabilityScore, breadthScore)
                )
        );
    }
}
