package com.coinmind.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@ConfigurationProperties(prefix = "coinmind.ai")
public record AiProperties(
        boolean enabled,
        String provider,
        String model,
        String apiKey,
        String baseUrl,
        String reasoningEffort,
        int maxOutputTokens,
        long inputPriceMicrosPerMillion,
        long cachedInputPriceMicrosPerMillion,
        long outputPriceMicrosPerMillion,
        String usageTimezone,
        List<String> triggerIntervals,
        BigDecimal minimumAutomaticSignalScore,
        int maxAutomaticCallsPerHour,
        int maxAutomaticCallsPerDay
) {
}
