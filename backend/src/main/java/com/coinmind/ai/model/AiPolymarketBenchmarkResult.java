package com.coinmind.ai.model;

import java.time.Instant;

public record AiPolymarketBenchmarkResult(
        String symbol,
        String source,
        int materialityScore,
        BenchmarkRun routine,
        BenchmarkRun deep,
        boolean directionsAgree,
        boolean deterministicDirectionMatchesRoutine,
        boolean deterministicDirectionMatchesDeep,
        String deterministicBias,
        Instant benchmarkedAt
) {
    public record BenchmarkRun(
            String route,
            String model,
            long latencyMs,
            AiPolymarketIntelligence result
    ) {
    }
}
