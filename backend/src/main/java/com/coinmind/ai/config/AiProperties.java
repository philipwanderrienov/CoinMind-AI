package com.coinmind.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@ConfigurationProperties(prefix = "coinmind.ai")
public record AiProperties(
        boolean enabled,
        String provider,
        String model,
        String routineModel,
        String deepModel,
        String apiKey,
        String baseUrl,
        String reasoningEffort,
        int maxOutputTokens,
        long inputPriceMicrosPerMillion,
        long cachedInputPriceMicrosPerMillion,
        long outputPriceMicrosPerMillion,
        long routineInputPriceMicrosPerMillion,
        long routineCachedInputPriceMicrosPerMillion,
        long routineOutputPriceMicrosPerMillion,
        long deepInputPriceMicrosPerMillion,
        long deepCachedInputPriceMicrosPerMillion,
        long deepOutputPriceMicrosPerMillion,
        BigDecimal creditBudgetUsd,
        BigDecimal maxMonthlySpendUsd,
        String usageTimezone,
        List<String> triggerIntervals,
        BigDecimal minimumAutomaticSignalScore,
        int maxAutomaticCallsPerHour,
        int maxAutomaticCallsPerDay,
        boolean polymarketEnabled,
        BigDecimal polymarketMinProbabilityChange,
        int polymarketMinImportance,
        int polymarketDeepMaterialityScore,
        int polymarketCacheMinutes
) {
    public String effectiveRoutineModel() {
        return routineModel == null || routineModel.isBlank() ? model : routineModel;
    }

    public String effectiveDeepModel() {
        return deepModel == null || deepModel.isBlank() ? model : deepModel;
    }
}
