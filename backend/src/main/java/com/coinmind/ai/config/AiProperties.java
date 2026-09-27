package com.coinmind.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
        List<String> triggerIntervals
) {
}
