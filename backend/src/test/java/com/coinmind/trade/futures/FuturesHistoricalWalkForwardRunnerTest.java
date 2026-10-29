package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FuturesHistoricalWalkForwardRunnerTest {
    private static final Instant START = Instant.parse("2026-10-26T00:00:00Z");
    private List<FuturesReplayEngine.Candle> bars(Duration frame, int count, boolean trend) {
        return IntStream.range(0, count).mapToObj(i -> {
            BigDecimal price = BigDecimal.valueOf(trend ? 100L + i : 100L);
            return new FuturesReplayEngine.Candle(START.plus(frame.multipliedBy(i)),
                    price, price, price, price);
        }).toList();
    }
    private FuturesHistoricalWalkForwardRunner.Input input(String source, boolean trend) {
        return new FuturesHistoricalWalkForwardRunner.Input(source,
                bars(Duration.ofMinutes(5), 325, trend),
                bars(Duration.ofMinutes(15), 109, trend),
                bars(Duration.ofHours(1), 28, trend),
                bars(Duration.ofHours(4), 7, trend),
                2, new BigDecimal("1"), new BigDecimal("2"), BigDecimal.ONE,
                new BigDecimal("0.05"), new BigDecimal("0.05"));
    }
    @Test void chronologicalWalkForwardIsOfflineAndAggregated() {
        var result = FuturesHistoricalWalkForwardRunner.run(input("SYNTHETIC_FIXTURE", true));
        assertEquals("OFFLINE_ONLY", result.mode());
        assertFalse(result.liveSignalEligible());
        assertTrue(result.candidates() > 0);
        assertEquals(result.candidates(), result.outcomes().size());
        assertTrue(result.busySkipped() > 0);
    }
    @Test void entryExecutionOccursStrictlyAfterDecisionUnderTimestampContract() {
        // A trending fixture produces real candidates; regression for execution
        // candle opening at the exact decision timestamp (previously rejected).
        var report = FuturesHistoricalWalkForwardRunner.run(input("SYNTHETIC_FIXTURE", true));
        assertTrue(report.candidates() > 0);
        assertFalse(report.liveSignalEligible());
    }
    @Test void flatPricesProduceWaitAndZeroExecutedTrades() {
        var result = FuturesHistoricalWalkForwardRunner.run(input("HISTORICAL_REPLAY", false));
        assertEquals(0, result.candidates());
        assertEquals(0, result.performance().executed());
        assertTrue(result.waits() > 0);
    }
    @Test void rejectsLiveProvenance() {
        assertThrows(IllegalArgumentException.class,
                () -> FuturesHistoricalWalkForwardRunner.run(input("LIVE_EXCHANGE", true)));
    }
    @Test void rejectsMissingExecutionCandle() {
        var old = input("SYNTHETIC_FIXTURE", true);
        var missing = new java.util.ArrayList<>(old.m5());
        missing.remove(10);
        var broken = new FuturesHistoricalWalkForwardRunner.Input(old.provenance(), missing,
                old.m15(), old.h1(), old.h4(), old.horizonBars(), old.stopPercent(),
                old.targetPercent(), old.quantity(), old.feePercentPerSide(), old.slippagePercentPerSide());
        assertThrows(IllegalArgumentException.class,
                () -> FuturesHistoricalWalkForwardRunner.run(broken));
    }
    @Test void rejectsMisalignedHourlyCandleDespiteRegularIntervals() {
        var original = input("SYNTHETIC_FIXTURE", true);
        var offset = original.h1().stream()
                .map(c -> new FuturesReplayEngine.Candle(c.time().plusSeconds(60),
                        c.open(), c.high(), c.low(), c.close())).toList();
        var broken = new FuturesHistoricalWalkForwardRunner.Input(original.provenance(),
                original.m5(), original.m15(), offset, original.h4(),
                original.horizonBars(), original.stopPercent(), original.targetPercent(),
                original.quantity(), original.feePercentPerSide(), original.slippagePercentPerSide());
        var error = assertThrows(IllegalArgumentException.class,
                () -> FuturesHistoricalWalkForwardRunner.run(broken));
        assertTrue(error.getMessage().contains("boundary"));
    }

    @Test void rejectsSubsecondFiveMinuteCandleTimestamp() {
        var original = input("SYNTHETIC_FIXTURE", true);
        var shifted = new java.util.ArrayList<>(original.m5());
        var c = shifted.get(10);
        shifted.set(10, new FuturesReplayEngine.Candle(c.time().plusNanos(1),
                c.open(), c.high(), c.low(), c.close()));
        var broken = new FuturesHistoricalWalkForwardRunner.Input(original.provenance(),
                shifted, original.m15(), original.h1(), original.h4(),
                original.horizonBars(), original.stopPercent(), original.targetPercent(),
                original.quantity(), original.feePercentPerSide(), original.slippagePercentPerSide());
        assertThrows(IllegalArgumentException.class,
                () -> FuturesHistoricalWalkForwardRunner.run(broken));
    }

    @Test void optInSizingUsesRiskBudgetInsteadOfFixedQuantity() {
        var data = input("SYNTHETIC_FIXTURE", true);
        var legacy = FuturesHistoricalWalkForwardRunner.run(data);
        var sized = FuturesHistoricalWalkForwardRunner.runWithPaperSizing(data,
                new FuturesHistoricalWalkForwardRunner.PaperSizing(
                        new BigDecimal("1000"), new BigDecimal("1"), new BigDecimal("3")));
        assertEquals(legacy.candidates(), sized.candidates());
        assertEquals("OFFLINE_ONLY", sized.mode());
        assertFalse(sized.liveSignalEligible());
        assertTrue(sized.outcomes().size() > 0);
        assertNotEquals(0, legacy.performance().totalNetPnl().compareTo(sized.performance().totalNetPnl()));
    }

    @Test void rejectsUnsafeSizingConfiguration() {
        var data = input("SYNTHETIC_FIXTURE", true);
        assertThrows(IllegalArgumentException.class, () ->
                FuturesHistoricalWalkForwardRunner.runWithPaperSizing(data,
                        new FuturesHistoricalWalkForwardRunner.PaperSizing(
                                new BigDecimal("1000"), new BigDecimal("2"), new BigDecimal("3"))));
    }

    @Test void dynamicEquityModeCompoundsAndRemainsOffline() {
        var data = input("SYNTHETIC_FIXTURE", true);
        var sizing = new FuturesHistoricalWalkForwardRunner.PaperSizing(
                new BigDecimal("1000"), new BigDecimal("1"), new BigDecimal("3"));
        var report = FuturesHistoricalWalkForwardRunner.runWithDynamicEquity(
                data, sizing, new BigDecimal("2"));
        assertEquals("OFFLINE_ONLY", report.mode());
        assertFalse(report.liveSignalEligible());
        assertTrue(report.executed() > 0);
        assertEquals(report.executed(), report.equityCurve().size());
        assertEquals(0, report.finalEquity().compareTo(
                report.equityCurve().get(report.equityCurve().size()-1).equityUsdt()));
    }

    @Test void dynamicEquityRejectsUnsafeDailyRisk() {
        assertThrows(IllegalArgumentException.class, () ->
                FuturesHistoricalWalkForwardRunner.runWithDynamicEquity(
                        input("SYNTHETIC_FIXTURE", true),
                        new FuturesHistoricalWalkForwardRunner.PaperSizing(
                                new BigDecimal("1000"), new BigDecimal("1"), new BigDecimal("3")),
                        new BigDecimal("101")));
    }

    @Test void dynamicEquityRejectsLiveProvenance() {
        assertThrows(IllegalArgumentException.class, () ->
                FuturesHistoricalWalkForwardRunner.runWithDynamicEquity(
                        input("LIVE_EXCHANGE", true),
                        new FuturesHistoricalWalkForwardRunner.PaperSizing(
                                new BigDecimal("1000"), new BigDecimal("1"), new BigDecimal("3")),
                        new BigDecimal("2")));
    }

    @Test void dynamicEquitySettlesOnlyAfterFullFinalCandleClose() {
        var report = FuturesHistoricalWalkForwardRunner.runWithDynamicEquity(
                input("SYNTHETIC_FIXTURE", true),
                new FuturesHistoricalWalkForwardRunner.PaperSizing(
                        new BigDecimal("1000"), new BigDecimal("1"), new BigDecimal("3")),
                new BigDecimal("2"));
        assertTrue(report.equityCurve().size() > 1);
        for (var point : report.equityCurve()) {
            assertEquals(0, Math.floorMod(point.realizedAt().getEpochSecond(), 300));
            assertEquals(0, point.realizedAt().getNano());
        }
        for (int i = 1; i < report.equityCurve().size(); i++) {
            assertTrue(report.equityCurve().get(i).realizedAt()
                    .isAfter(report.equityCurve().get(i-1).realizedAt()));
        }
    }

}
