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
}
