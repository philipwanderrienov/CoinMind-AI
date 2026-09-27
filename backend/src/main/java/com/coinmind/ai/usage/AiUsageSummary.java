package com.coinmind.ai.usage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AiUsageSummary(
        long todayCalls,
        long todayInputTokens,
        long todayCachedInputTokens,
        long todayOutputTokens,
        long todayReasoningTokens,
        long todayTotalTokens,
        BigDecimal todayEstimatedCostUsd,
        long monthCalls,
        long monthInputTokens,
        long monthOutputTokens,
        BigDecimal monthEstimatedCostUsd,
        List<DailyUsage> daily
) {
    public record DailyUsage(
            LocalDate date,
            long calls,
            long inputTokens,
            long outputTokens,
            BigDecimal estimatedCostUsd
    ) {
    }
}
