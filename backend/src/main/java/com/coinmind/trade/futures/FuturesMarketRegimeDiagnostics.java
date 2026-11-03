package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Observational diagnostics for already-settled, offline historical trades.
 * All market descriptors use bars fully CLOSED at the original decision time.
 * Classification is not a trading gate and never modifies the replay.
 */
public final class FuturesMarketRegimeDiagnostics {
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HALF_PERCENT = new BigDecimal("0.5");
    private static final BigDecimal ONE_PERCENT = BigDecimal.ONE;
    private FuturesMarketRegimeDiagnostics() {}

    public record Observation(Instant decisionAt, String side, String volatilityBand,
                              BigDecimal lastThreeFiveMinuteRangePercent,
                              BigDecimal fiveMinuteMomentum, BigDecimal fifteenMinuteMomentum,
                              BigDecimal oneHourMomentum, BigDecimal fourHourMomentum,
                              BigDecimal entryStrength, BigDecimal netPnl,
                              int alignedTimeframes, boolean fiveMinuteMomentumDecelerating,
                              BigDecimal grossPnl, BigDecimal tradingFees) {}

    public record Group(String volatilityBand, int count, int wins, int losses,
                        BigDecimal netPnl, BigDecimal averageNetPnl) {}

    public static List<Observation> observe(
            FuturesHistoricalWalkForwardRunner.Input input,
            FuturesHistoricalWalkForwardRunner.DynamicReport report) {
        if (input == null || report == null
                || !"HISTORICAL_REPLAY".equals(input.provenance())
                        && !"SYNTHETIC_FIXTURE".equals(input.provenance())
                || !"OFFLINE_ONLY".equals(report.mode()) || report.liveSignalEligible()
                || report.executed() != report.trades().size())
            throw new IllegalArgumentException("Complete offline replay required");
        var observations = new ArrayList<Observation>();
        for (var trade : report.trades()) {
            Instant when = trade.decisionAt();
            var five = lastClosed(input.m5(), Duration.ofMinutes(5), when);
            var fifteen = lastClosed(input.m15(), Duration.ofMinutes(15), when);
            var hourly = lastClosed(input.h1(), Duration.ofHours(1), when);
            var four = lastClosed(input.h4(), Duration.ofHours(4), when);
            var score = FuturesClosedCandleScoring.score(
                    input.provenance(), when, five, fifteen, hourly, four);
            var decision = FuturesIntradayDecisionEngine.evaluate(score);
            String expectedSide = "WATCH_LONG".equals(decision.action()) ? "LONG"
                    : "WATCH_SHORT".equals(decision.action()) ? "SHORT" : "NONE";
            if (!expectedSide.equals(trade.side()))
                throw new IllegalArgumentException("Trade/closed-candle decision mismatch");
            BigDecimal high = five.stream().map(FuturesReplayEngine.Candle::high)
                    .max(BigDecimal::compareTo).orElseThrow();
            BigDecimal low = five.stream().map(FuturesReplayEngine.Candle::low)
                    .min(BigDecimal::compareTo).orElseThrow();
            BigDecimal reference = five.getFirst().close();
            BigDecimal range = high.subtract(low).multiply(new BigDecimal("100"), MC)
                    .divide(reference, MC);
            String band = range.compareTo(HALF_PERCENT) < 0 ? "LOW"
                    : range.compareTo(ONE_PERCENT) < 0 ? "MEDIUM" : "HIGH";
            int sign = "LONG".equals(trade.side()) ? 1 : -1;
            int aligned = 0;
            for (var momentum : List.of(score.fiveMinuteScore(), score.fifteenMinuteScore(),
                    score.oneHourTrend(), score.fourHourTrend()))
                if (momentum.signum() == sign) aligned++;
            // A descriptive three-closed-candle exhaustion proxy: the signed last
            // 5m close-to-close change is weaker than the preceding 5m change.
            BigDecimal previousChange = five.get(1).close().subtract(five.get(0).close())
                    .multiply(BigDecimal.valueOf(sign));
            BigDecimal latestChange = five.get(2).close().subtract(five.get(1).close())
                    .multiply(BigDecimal.valueOf(sign));
            boolean decelerating = latestChange.compareTo(previousChange) < 0;
            observations.add(new Observation(when, trade.side(), band, range,
                    score.fiveMinuteScore(), score.fifteenMinuteScore(),
                    score.oneHourTrend(), score.fourHourTrend(),
                    decision.entryScore().abs(), trade.netPnl(), aligned, decelerating,
                    trade.grossPnl(), trade.tradingFees()));
        }
        return List.copyOf(observations);
    }

    /** Last three fully closed candles; never uses later candles or an open bar. */
    private static List<FuturesReplayEngine.Candle> lastClosed(
            List<FuturesReplayEngine.Candle> bars, Duration timeframe, Instant when) {
        if (bars == null || when == null) throw new IllegalArgumentException("Missing candle data");
        var last = new ArrayList<FuturesReplayEngine.Candle>(3);
        for (var bar : bars) {
            if (bar.time().plus(timeframe).isAfter(when)) break;
            if (last.size() == 3) last.removeFirst();
            last.add(bar);
        }
        if (last.size() != 3)
            throw new IllegalArgumentException("Insufficient closed candles for diagnostics");
        return List.copyOf(last);
    }

    public static List<Group> byVolatility(List<Observation> observations) {
        if (observations == null) throw new IllegalArgumentException("Observations required");
        var result = new ArrayList<Group>();
        for (String band : List.of("LOW", "MEDIUM", "HIGH")) {
            int count = 0, wins = 0, losses = 0;
            BigDecimal net = BigDecimal.ZERO;
            for (var row : observations) {
                if (row == null || row.netPnl() == null || !List.of("LOW", "MEDIUM", "HIGH")
                        .contains(row.volatilityBand()))
                    throw new IllegalArgumentException("Invalid observation");
                if (!band.equals(row.volatilityBand())) continue;
                count++;
                if (row.netPnl().signum() > 0) wins++;
                if (row.netPnl().signum() < 0) losses++;
                net = net.add(row.netPnl());
            }
            result.add(new Group(band, count, wins, losses, net,
                    count == 0 ? null : net.divide(BigDecimal.valueOf(count), MC)));
        }
        return List.copyOf(result);
    }
}
