package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/** Offline replay statistics. Only closed, explicitly non-live results are accepted. */
public final class FuturesPerformanceAnalytics {
    private static final MathContext MC = MathContext.DECIMAL64;
    private FuturesPerformanceAnalytics() {}

    public static Summary summarize(List<FuturesReplayEngine.Result> results) {
        if (results == null) throw new IllegalArgumentException("results required");
        int wins = 0, losses = 0, flats = 0, skipped = 0;
        BigDecimal grossProfit = BigDecimal.ZERO;
        BigDecimal grossLoss = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal equity = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (var result : results) {
            if (result == null || (!"SYNTHETIC_FIXTURE".equals(result.provenance())
                    && !"HISTORICAL_REPLAY".equals(result.provenance()))) {
                throw new IllegalArgumentException("Only replay results are accepted");
            }
            if ("NO_ENTRY".equals(result.exitReason())) {
                skipped++;
                continue;
            }
            if (!List.of("STOP", "TARGET", "TIME_EXIT").contains(result.exitReason())
                    || result.netPnl() == null || result.tradingFees() == null) {
                throw new IllegalArgumentException("Incomplete replay result");
            }
            BigDecimal pnl = result.netPnl();
            net = net.add(pnl, MC);
            if (pnl.signum() > 0) { wins++; grossProfit = grossProfit.add(pnl, MC); }
            else if (pnl.signum() < 0) { losses++; grossLoss = grossLoss.add(pnl.abs(), MC); }
            else flats++;
            equity = equity.add(pnl, MC);
            peak = peak.max(equity);
            maxDrawdown = maxDrawdown.max(peak.subtract(equity, MC));
        }
        int executed = wins + losses + flats;
        BigDecimal expectancy = executed == 0 ? null :
                net.divide(BigDecimal.valueOf(executed), MC);
        BigDecimal profitFactor = grossLoss.signum() == 0 ? null :
                grossProfit.divide(grossLoss, MC);
        BigDecimal winRate = executed == 0 ? null :
                BigDecimal.valueOf(wins * 100L).divide(BigDecimal.valueOf(executed), MC);
        return new Summary(results.size(), executed, skipped, wins, losses, flats,
                net, expectancy, winRate, profitFactor, maxDrawdown,
                grossLoss.signum() == 0 ? "UNDEFINED_NO_LOSSES" : "DEFINED",
                "Absolute drawdown in quote currency, not percentage. No funding/liquidation/venue impact; not real-world PnL.");
    }

    public record Summary(int candidates, int executed, int noEntry, int wins, int losses,
                          int breakEven, BigDecimal totalNetPnl, BigDecimal expectancyPerTrade,
                          BigDecimal winRatePercent, BigDecimal profitFactor,
                          BigDecimal maxDrawdownQuote, String profitFactorStatus,
                          String limitations) {}
}
