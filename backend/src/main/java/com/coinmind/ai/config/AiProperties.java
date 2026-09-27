package com.coinmind.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
        long outputPriceMicrosPerMillion
) {
}
