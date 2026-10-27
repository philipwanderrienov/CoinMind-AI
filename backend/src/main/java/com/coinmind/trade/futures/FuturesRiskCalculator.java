package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Educational, fee-aware isolated-margin sizing for a perpetual futures advisor.
 * Does NOT estimate exchange-specific liquidation and does NOT place orders.
 */
@Service
public class FuturesRiskCalculator {
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public Result calculate(Request r) {
        positive(r.equityUsdt(), "equityUsdt");
        positive(r.entryPrice(), "entryPrice");
        positive(r.stopPrice(), "stopPrice");
        positive(r.leverage(), "leverage");
        nonnegative(r.riskPercent(), "riskPercent");
        nonnegative(r.feeRatePercentPerSide(), "feeRatePercentPerSide");
        nonnegative(r.slippagePercentPerSide(), "slippagePercentPerSide");
        if (r.riskPercent().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("riskPercent cannot exceed 1% in initial paper mode");
        }
        if (r.leverage().compareTo(new BigDecimal("5")) > 0) {
            throw new IllegalArgumentException("leverage cannot exceed 5x in initial paper mode");
        }
        boolean isLong = switch (r.side()) {
            case "LONG" -> true;
            case "SHORT" -> false;
            default -> throw new IllegalArgumentException("side must be LONG or SHORT");
        };
        if ((isLong && r.stopPrice().compareTo(r.entryPrice()) >= 0)
                || (!isLong && r.stopPrice().compareTo(r.entryPrice()) <= 0)) {
            throw new IllegalArgumentException("stop must be on the loss side of entry");
        }
        BigDecimal distance = r.entryPrice().subtract(r.stopPrice()).abs()
                .divide(r.entryPrice(), MC);
        BigDecimal costs = r.feeRatePercentPerSide().add(r.slippagePercentPerSide())
                .multiply(new BigDecimal("2"), MC).divide(HUNDRED, MC);
        BigDecimal perNotionalLoss = distance.add(costs);
        BigDecimal riskBudget = r.equityUsdt().multiply(r.riskPercent(), MC)
                .divide(HUNDRED, MC);
        BigDecimal riskBasedNotional = riskBudget.divide(perNotionalLoss, MC);
        // Conservatively cap margin to 20% of equity to avoid excessive concentration.
        BigDecimal marginCapNotional = r.equityUsdt()
                .multiply(new BigDecimal("0.20"), MC).multiply(r.leverage(), MC);
        BigDecimal notional = riskBasedNotional.min(marginCapNotional);
        BigDecimal margin = notional.divide(r.leverage(), MC);
        BigDecimal estimatedLoss = notional.multiply(perNotionalLoss, MC);
        return new Result(
                round(riskBudget), round(notional), round(margin),
                round(estimatedLoss), round(notional.divide(r.entryPrice(), MC)),
                "PAPER_ONLY",
                "Not a liquidation estimate. Funding, maintenance margin, adverse gaps and exchange rules are excluded.");
    }
    private void positive(BigDecimal n, String key) {
        if (n == null || n.signum() <= 0) throw new IllegalArgumentException(key + " must be positive");
    }
    private void nonnegative(BigDecimal n, String key) {
        if (n == null || n.signum() < 0) throw new IllegalArgumentException(key + " must be nonnegative");
    }
    private BigDecimal round(BigDecimal n) { return n.setScale(6, RoundingMode.HALF_UP); }

    public record Request(String side, BigDecimal equityUsdt, BigDecimal entryPrice,
                          BigDecimal stopPrice, BigDecimal riskPercent, BigDecimal leverage,
                          BigDecimal feeRatePercentPerSide, BigDecimal slippagePercentPerSide) {}
    public record Result(BigDecimal riskBudgetUsdt, BigDecimal notionalUsdt,
                         BigDecimal estimatedInitialMarginUsdt, BigDecimal estimatedStopLossUsdt,
                         BigDecimal quantity, String readiness, String limitations) {}
}
