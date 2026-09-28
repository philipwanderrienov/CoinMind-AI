package com.coinmind.ai.evaluation;

import java.math.BigDecimal;
import java.util.List;

public record AiEvaluationSummary(
        String symbol,
        String interval,
        int totalAnalyses,
        int totalEvaluations,
        List<HorizonSummary> horizons,
        List<BiasSummary> biases,
        List<ModelSummary> models
) {
    public record HorizonSummary(
            String horizon,
            int evaluated,
            int correct,
            BigDecimal accuracyPct,
            BigDecimal averageReturnPct
    ) {
    }

    public record BiasSummary(
            String marketBias,
            int evaluated,
            int correct,
            BigDecimal hitRatePct
    ) {
    }

    public record ModelSummary(
            String model,
            int evaluated,
            int correct,
            BigDecimal accuracyPct,
            BigDecimal averageReturnPct
    ) {
    }
}
