package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Offline-only position sizing adapter for the existing conservative risk calculator.
 * Uses a fixed reference equity; does not imply live account state or executable orders.
 */
public final class FuturesPaperPositionSizer {
    private static final MathContext MC = MathContext.DECIMAL64;
    private FuturesPaperPositionSizer() {}

    public static Result size(Request r) {
        if (r == null || !"SYNTHETIC_FIXTURE".equals(r.provenance())
                && !"HISTORICAL_REPLAY".equals(r.provenance()))
            throw new IllegalArgumentException("Offline provenance required");
        var estimate = new FuturesRiskCalculator().calculate(new FuturesRiskCalculator.Request(
                r.side(), r.equity(), r.entry(), r.stop(), r.riskPercent(), r.leverage(),
                r.feePercentPerSide(), r.slippagePercentPerSide()));
        if (estimate.quantity().signum() <= 0 || estimate.notionalUsdt().signum() <= 0)
            return new Result(false, BigDecimal.ZERO, estimate, "BELOW_MINIMUM_PRECISION");
        BigDecimal riskCap = r.equity().multiply(r.riskPercent(), MC)
                .divide(new BigDecimal("100"), MC);
        if (estimate.estimatedStopLossUsdt().compareTo(riskCap) > 0)
            throw new IllegalStateException("Estimated loss exceeds requested risk budget");
        return new Result(true, estimate.quantity(), estimate, "PAPER_ONLY");
    }

    public record Request(String provenance, String side, BigDecimal equity,
                          BigDecimal entry, BigDecimal stop, BigDecimal riskPercent,
                          BigDecimal leverage, BigDecimal feePercentPerSide,
                          BigDecimal slippagePercentPerSide) {}
    public record Result(boolean eligible, BigDecimal quantity,
                         FuturesRiskCalculator.Result estimate, String status) {}
}
