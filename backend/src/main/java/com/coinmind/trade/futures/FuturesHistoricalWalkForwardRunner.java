package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Experimental offline chronological walk-forward runner using 5m execution candles.
 * Candle timestamps are OPEN times. Decision scores use only bars closed by decision time.
 * Fixed replay horizon skips overlapping decisions; no order routing or live signals.
 */
public final class FuturesHistoricalWalkForwardRunner {
    private static final MathContext MC = MathContext.DECIMAL64;
    private FuturesHistoricalWalkForwardRunner() {}

    public static Report run(Input in) {
        return runInternal(in, null);
    }

    /** Opt-in PAPER_ONLY sizing with fixed reference equity, not an equity curve. */
    public static Report runWithPaperSizing(Input in, PaperSizing sizing) {
        if (sizing == null || sizing.equityUsdt() == null || sizing.equityUsdt().signum() <= 0
                || sizing.riskPercent() == null || sizing.riskPercent().signum() <= 0
                || sizing.riskPercent().compareTo(BigDecimal.ONE) > 0
                || sizing.leverage() == null || sizing.leverage().signum() <= 0
                || sizing.leverage().compareTo(new BigDecimal("5")) > 0)
            throw new IllegalArgumentException("Invalid paper sizing configuration");
        return runInternal(in, sizing);
    }

    /**
     * Opt-in realized-equity compounding, applying a UTC daily stop before each entry.
     * Uses the complete fixed replay horizon as a conservative occupancy window.
     */
    public static DynamicReport runWithDynamicEquity(Input in, PaperSizing sizing,
                                                       BigDecimal dailyLossLimitPercent) {
        if (sizing == null || sizing.equityUsdt() == null || sizing.equityUsdt().signum() <= 0
                || sizing.riskPercent() == null || sizing.riskPercent().signum() <= 0
                || sizing.riskPercent().compareTo(BigDecimal.ONE) > 0
                || sizing.leverage() == null || sizing.leverage().signum() <= 0
                || sizing.leverage().compareTo(new BigDecimal("5")) > 0
                || dailyLossLimitPercent == null || dailyLossLimitPercent.signum() <= 0
                || dailyLossLimitPercent.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Invalid dynamic paper configuration");
        // Reuse the existing ingestion/chronology validation without changing legacy outputs.
        runInternal(in, null);
        BigDecimal equity = sizing.equityUsdt(), peak = equity, maxDrawdown = BigDecimal.ZERO;
        BigDecimal dayStart = equity, dayPnl = BigDecimal.ZERO;
        java.time.LocalDate day = null;
        Instant busyUntil = Instant.MIN;
        int waits = 0, blocked = 0, executed = 0;
        var points = new ArrayList<EquityPoint>();
        var results = new ArrayList<FuturesReplayEngine.Result>();
        for (int i = 2; i + 2 + in.horizonBars() <= in.m5().size(); i++) {
            Instant decisionTime = in.m5().get(i).time().plus(Duration.ofMinutes(5));
            if (!decisionTime.isAfter(busyUntil)) continue;
            var fifteen = closed(in.m15(), Duration.ofMinutes(15), decisionTime);
            var hourly = closed(in.h1(), Duration.ofHours(1), decisionTime);
            var four = closed(in.h4(), Duration.ofHours(4), decisionTime);
            if (fifteen.size() < 3 || hourly.size() < 3 || four.size() < 3) continue;
            var five = List.copyOf(in.m5().subList(i - 2, i + 1));
            var decision = FuturesIntradayDecisionEngine.evaluate(
                    FuturesClosedCandleScoring.score(in.provenance(), decisionTime, five, fifteen, hourly, four));
            if ("WAIT".equals(decision.action())) { waits++; continue; }
            java.time.LocalDate currentDay = decisionTime.atZone(java.time.ZoneOffset.UTC).toLocalDate();
            if (!currentDay.equals(day)) {
                day = currentDay;
                dayStart = equity;
                dayPnl = BigDecimal.ZERO;
            }
            BigDecimal maxDailyLoss = dayStart.multiply(dailyLossLimitPercent, MC)
                    .divide(new BigDecimal("100"), MC);
            if (equity.signum() <= 0 || dayPnl.negate().compareTo(maxDailyLoss) >= 0) {
                blocked++;
                continue;
            }
            String side = "WATCH_LONG".equals(decision.action()) ? "LONG" : "SHORT";
            BigDecimal entry = in.m5().get(i + 2).open();
            BigDecimal stopFraction = in.stopPercent().divide(new BigDecimal("100"), MC);
            BigDecimal targetFraction = in.targetPercent().divide(new BigDecimal("100"), MC);
            BigDecimal stop = entry.multiply(side.equals("LONG")
                    ? BigDecimal.ONE.subtract(stopFraction) : BigDecimal.ONE.add(stopFraction), MC);
            BigDecimal target = entry.multiply(side.equals("LONG")
                    ? BigDecimal.ONE.add(targetFraction) : BigDecimal.ONE.subtract(targetFraction), MC);
            if (stop.signum() <= 0 || target.signum() <= 0)
                throw new IllegalArgumentException("Invalid stop/target");
            var size = FuturesPaperPositionSizer.size(new FuturesPaperPositionSizer.Request(
                    in.provenance(), side, equity, entry, stop, sizing.riskPercent(), sizing.leverage(),
                    in.feePercentPerSide(), in.slippagePercentPerSide()));
            if (!size.eligible()) { blocked++; continue; }
            var execution = List.copyOf(in.m5().subList(i + 2, i + 2 + in.horizonBars()));
            var result = FuturesReplayEngine.run(new FuturesReplayEngine.Request(
                    in.provenance(), side, entry, stop, target, size.quantity(),
                    in.feePercentPerSide(), in.slippagePercentPerSide(), execution));
            // All horizons reserved even if an early exit occurs. No equity marked while open.
            busyUntil = execution.get(execution.size() - 1).time();
            if (result.netPnl() != null) {
                equity = equity.add(result.netPnl(), MC);
                dayPnl = dayPnl.add(result.netPnl(), MC);
                peak = peak.max(equity);
                maxDrawdown = maxDrawdown.max(peak.subtract(equity, MC));
                executed++;
                points.add(new EquityPoint(busyUntil.plus(Duration.ofMinutes(5)), equity,
                        result.netPnl()));
            }
            results.add(result);
        }
        return new DynamicReport("OFFLINE_ONLY", false, sizing.equityUsdt(), equity,
                maxDrawdown, waits, blocked, executed, List.copyOf(points),
                FuturesPerformanceAnalytics.summarize(results),
                "Experimental realized-only simulated equity, UTC daily loss stop checked before entry. "
                + "Profits/losses conservatively accounted at end of full horizon. "
                + "No funding, liquidation, gap/margin realism, or live trading.");
    }

    private static Report runInternal(Input in, PaperSizing sizing) {
        if (in == null || (!"SYNTHETIC_FIXTURE".equals(in.provenance())
                && !"HISTORICAL_REPLAY".equals(in.provenance())))
            throw new IllegalArgumentException("Offline input required");
        if (in.horizonBars() < 1 || in.horizonBars() > 48
                || in.stopPercent() == null || in.stopPercent().signum() <= 0
                || in.targetPercent() == null || in.targetPercent().signum() <= 0
                || in.quantity() == null || in.quantity().signum() <= 0
                || in.feePercentPerSide() == null || in.feePercentPerSide().signum() < 0
                || in.slippagePercentPerSide() == null || in.slippagePercentPerSide().signum() < 0)
            throw new IllegalArgumentException("Invalid replay parameters");
        validate(in.m5(), Duration.ofMinutes(5));
        validate(in.m15(), Duration.ofMinutes(15));
        validate(in.h1(), Duration.ofHours(1));
        validate(in.h4(), Duration.ofHours(4));
        var candidates = new ArrayList<FuturesCandleStrategyPipeline.Candidate>();
        int waits = 0;
        int busy = 0;
        Instant reservedUntil = Instant.MIN;
        for (int i = 2; i + 2 + in.horizonBars() <= in.m5().size(); i++) {
            Instant decisionTime = in.m5().get(i).time().plus(Duration.ofMinutes(5));
            if (!decisionTime.isAfter(reservedUntil)) { busy++; continue; }
            var five = List.copyOf(in.m5().subList(i - 2, i + 1));
            var fifteen = closed(in.m15(), Duration.ofMinutes(15), decisionTime);
            var oneHour = closed(in.h1(), Duration.ofHours(1), decisionTime);
            var fourHour = closed(in.h4(), Duration.ofHours(4), decisionTime);
            if (fifteen.size() < 3 || oneHour.size() < 3 || fourHour.size() < 3) continue;
            var score = FuturesClosedCandleScoring.score(in.provenance(), decisionTime,
                    five, fifteen, oneHour, fourHour);
            var decision = FuturesIntradayDecisionEngine.evaluate(score);
            if ("WAIT".equals(decision.action())) { waits++; continue; }
            String side = "WATCH_LONG".equals(decision.action()) ? "LONG" : "SHORT";
            // Strict replay timestamps require execution to start AFTER decisionTime.
            // Decision at close(i) equals open(i+1), so enter at open(i+2).
            // This conservatively adds one full 5m candle of execution delay.
            BigDecimal entry = in.m5().get(i + 2).open();
            BigDecimal stopFactor = in.stopPercent().divide(new BigDecimal("100"), MC);
            BigDecimal targetFactor = in.targetPercent().divide(new BigDecimal("100"), MC);
            BigDecimal stop = side.equals("LONG")
                    ? entry.multiply(BigDecimal.ONE.subtract(stopFactor), MC)
                    : entry.multiply(BigDecimal.ONE.add(stopFactor), MC);
            BigDecimal target = side.equals("LONG")
                    ? entry.multiply(BigDecimal.ONE.add(targetFactor), MC)
                    : entry.multiply(BigDecimal.ONE.subtract(targetFactor), MC);
            if (stop.signum() <= 0 || target.signum() <= 0)
                throw new IllegalArgumentException("Stop/target outside positive price domain");
            var execution = List.copyOf(in.m5().subList(i + 2, i + 2 + in.horizonBars()));
            BigDecimal quantity = in.quantity();
            if (sizing != null) {
                var sized = FuturesPaperPositionSizer.size(new FuturesPaperPositionSizer.Request(
                        in.provenance(), side, sizing.equityUsdt(), entry, stop,
                        sizing.riskPercent(), sizing.leverage(),
                        in.feePercentPerSide(), in.slippagePercentPerSide()));
                if (!sized.eligible()) continue;
                quantity = sized.quantity();
            }
            var request = new FuturesReplayEngine.Request(in.provenance(), side,
                    entry, stop, target, quantity, in.feePercentPerSide(),
                    in.slippagePercentPerSide(), execution);
            candidates.add(new FuturesCandleStrategyPipeline.Candidate(decisionTime,
                    five, fifteen, oneHour, fourHour, request));
            reservedUntil = execution.get(execution.size() - 1).time();
        }
        var evaluated = FuturesCandleStrategyPipeline.evaluate(candidates);
        return new Report("OFFLINE_ONLY", false, waits, busy, candidates.size(),
                evaluated.performance(), evaluated.results(),
                "Conservative one-bar-delayed open entry and fixed-horizon replay, not a realistic limit order fill. "
                + (sizing == null ? "Fixed quantity mode. " : "Fixed reference equity risk-sizing mode. ")
                + "No dynamic equity curve, funding, liquidation, margin tiers, contract rules, or out-of-sample validation.");
    }

    private static List<FuturesReplayEngine.Candle> closed(
            List<FuturesReplayEngine.Candle> bars, Duration frame, Instant at) {
        int end = 0;
        while (end < bars.size() && !bars.get(end).time().plus(frame).isAfter(at)) end++;
        return List.copyOf(bars.subList(Math.max(0, end - 3), end));
    }

    private static void validate(List<FuturesReplayEngine.Candle> candles, Duration frame) {
        if (candles == null || candles.isEmpty()) throw new IllegalArgumentException("Candles required");
        Instant previous = null;
        for (var c : candles) {
            if (c == null || c.time() == null || (previous != null && !c.time().isAfter(previous))
                    || c.open() == null || c.high() == null || c.low() == null || c.close() == null
                    || c.low().signum() <= 0 || c.low().compareTo(c.high()) > 0
                    || c.open().compareTo(c.low()) < 0 || c.open().compareTo(c.high()) > 0
                    || c.close().compareTo(c.low()) < 0 || c.close().compareTo(c.high()) > 0)
                throw new IllegalArgumentException("Invalid or unordered OHLC");
            if (c.time().getNano() != 0 || Math.floorMod(c.time().getEpochSecond(), frame.getSeconds()) != 0)
                throw new IllegalArgumentException("Candle open must align to UTC timeframe boundary");
            if (previous != null && !c.time().equals(previous.plus(frame)))
                throw new IllegalArgumentException("Missing or misaligned timeframe candle");
            previous = c.time();
        }
    }

    public record EquityPoint(Instant realizedAt, BigDecimal equityUsdt, BigDecimal netPnlUsdt) {}
    public record DynamicReport(String mode, boolean liveSignalEligible, BigDecimal initialEquity,
                                BigDecimal finalEquity, BigDecimal maxDrawdownQuote, int waits,
                                int riskBlocked, int executed, List<EquityPoint> equityCurve,
                                FuturesPerformanceAnalytics.Summary performance, String limitations) {}

    public record PaperSizing(BigDecimal equityUsdt, BigDecimal riskPercent, BigDecimal leverage) {}

    public record Input(String provenance, List<FuturesReplayEngine.Candle> m5,
            List<FuturesReplayEngine.Candle> m15, List<FuturesReplayEngine.Candle> h1,
            List<FuturesReplayEngine.Candle> h4, int horizonBars, BigDecimal stopPercent,
            BigDecimal targetPercent, BigDecimal quantity, BigDecimal feePercentPerSide,
            BigDecimal slippagePercentPerSide) {}
    public record Report(String mode, boolean liveSignalEligible, int waits, int busySkipped,
            int candidates, FuturesPerformanceAnalytics.Summary performance,
            List<FuturesReplayEngine.Result> outcomes, String limitations) {}
}
