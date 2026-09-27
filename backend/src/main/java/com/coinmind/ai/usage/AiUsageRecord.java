package com.coinmind.ai.usage;

import java.math.BigDecimal;
import java.time.Instant;

public record AiUsageRecord(
        String id,
        String requestId,
        String symbol,
        String interval,
        String triggerType,
        String model,
        long inputTokens,
        long cachedInputTokens,
        long outputTokens,
        long reasoningTokens,
        long totalTokens,
        BigDecimal estimatedInputCostUsd,
        BigDecimal estimatedOutputCostUsd,
        BigDecimal estimatedTotalCostUsd,
        Instant createdAt
) {
}
