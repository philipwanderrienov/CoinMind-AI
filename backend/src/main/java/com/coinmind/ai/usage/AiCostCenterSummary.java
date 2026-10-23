package com.coinmind.ai.usage;

import java.math.BigDecimal;
import java.util.List;

public record AiCostCenterSummary(
        BigDecimal internalCreditBudgetUsd,
        BigDecimal estimatedLifetimeSpendUsd,
        BigDecimal estimatedRemainingBudgetUsd,
        BigDecimal todayInputCostUsd,
        BigDecimal todayOutputCostUsd,
        BigDecimal todayTotalCostUsd,
        BigDecimal monthInputCostUsd,
        BigDecimal monthOutputCostUsd,
        BigDecimal monthTotalCostUsd,
        long todayInputTokens,
        long todayCachedInputTokens,
        long todayOutputTokens,
        long todayReasoningTokens,
        long monthInputTokens,
        long monthCachedInputTokens,
        long monthOutputTokens,
        long monthReasoningTokens,
        List<AiUsageRepository.CostBreakdown> byModel,
        List<AiUsageRepository.CostBreakdown> byTrigger,
        String balanceSourceNote
) {
}
