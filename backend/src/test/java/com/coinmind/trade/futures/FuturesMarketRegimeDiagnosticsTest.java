package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FuturesMarketRegimeDiagnosticsTest {
    private static final Instant START = Instant.parse("2026-10-26T00:00:00Z");

    private List<FuturesReplayEngine.Candle> bars(Duration frame, int count) {
        return IntStream.range(0, count).mapToObj(i -> {
            BigDecimal price = BigDecimal.valueOf(100L + i);
            return new FuturesReplayEngine.Candle(START.plus(frame.multipliedBy(i)),
                    price, price, price, price);
        }).toList();
    }

    private FuturesHistoricalWalkForwardRunner.Input fixture() {
        return new FuturesHistoricalWalkForwardRunner.Input("SYNTHETIC_FIXTURE",
                bars(Duration.ofMinutes(5), 325), bars(Duration.ofMinutes(15), 109),
                bars(Duration.ofHours(1), 28), bars(Duration.ofHours(4), 7),
                2, BigDecimal.ONE, new BigDecimal("2"), BigDecimal.ONE,
                new BigDecimal("0.05"), new BigDecimal("0.05"));
    }

    @Test void regimeGroupsReconcileAndUseOnlyClosedMarketCandles() {
        var input = fixture();
        var report = FuturesHistoricalWalkForwardRunner.runWithDynamicEquity(
                input, new FuturesHistoricalWalkForwardRunner.PaperSizing(
                        new BigDecimal("1000"), BigDecimal.ONE, new BigDecimal("3")),
                new BigDecimal("2"));
        assertTrue(report.executed() > 0);
        var observations = FuturesMarketRegimeDiagnostics.observe(input, report);
        var groups = FuturesMarketRegimeDiagnostics.byVolatility(observations);
        assertEquals(report.executed(), observations.size());
        assertEquals(report.executed(), groups.stream().mapToInt(
                FuturesMarketRegimeDiagnostics.Group::count).sum());
        assertEquals(0, report.finalEquity().subtract(report.initialEquity())
                .compareTo(groups.stream().map(FuturesMarketRegimeDiagnostics.Group::netPnl)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertTrue(observations.stream().allMatch(o -> o.lastThreeFiveMinuteRangePercent().signum() >= 0));
        assertFalse(report.liveSignalEligible());
    }

    @Test void failsClosedWhenTradeDecisionCannotBeReconstructed() {
        var input = fixture();
        var report = FuturesHistoricalWalkForwardRunner.runWithDynamicEquity(
                input, new FuturesHistoricalWalkForwardRunner.PaperSizing(
                        new BigDecimal("1000"), BigDecimal.ONE, new BigDecimal("3")),
                new BigDecimal("2"));
        var invalid = new FuturesHistoricalWalkForwardRunner.Input("HISTORICAL_REPLAY",
                input.m5(), input.m15(), input.h1(), input.h4(), 2,
                input.stopPercent(), input.targetPercent(), input.quantity(),
                input.feePercentPerSide(), input.slippagePercentPerSide());
        // Offline historical provenance remains accepted with identical closed candles.
        assertEquals(report.executed(),
                FuturesMarketRegimeDiagnostics.observe(invalid, report).size());
        var flat = input.m5().stream().map(c ->
                new FuturesReplayEngine.Candle(c.time(), new BigDecimal("100"),
                        new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"))).toList();
        var mismatched = new FuturesHistoricalWalkForwardRunner.Input("SYNTHETIC_FIXTURE",
                flat, input.m15(), input.h1(), input.h4(), 2,
                input.stopPercent(), input.targetPercent(), input.quantity(),
                input.feePercentPerSide(), input.slippagePercentPerSide());
        assertThrows(IllegalArgumentException.class,
                () -> FuturesMarketRegimeDiagnostics.observe(mismatched, report));
        var live = new FuturesHistoricalWalkForwardRunner.Input("LIVE_EXCHANGE",
                input.m5(), input.m15(), input.h1(), input.h4(), 2,
                input.stopPercent(), input.targetPercent(), input.quantity(),
                input.feePercentPerSide(), input.slippagePercentPerSide());
        assertThrows(IllegalArgumentException.class,
                () -> FuturesMarketRegimeDiagnostics.observe(live, report));
    }
}
