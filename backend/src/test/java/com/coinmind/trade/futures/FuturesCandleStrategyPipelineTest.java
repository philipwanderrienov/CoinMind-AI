package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FuturesCandleStrategyPipelineTest {
    private final Instant decision = Instant.parse("2026-10-28T12:00:00Z");
    private BigDecimal d(int n) { return BigDecimal.valueOf(n); }

    private List<FuturesReplayEngine.Candle> history(Duration duration, int lastClose) {
        return IntStream.range(0, 3).mapToObj(i -> {
            var close = d(i == 2 ? lastClose : 100);
            return new FuturesReplayEngine.Candle(decision.minus(duration.multipliedBy(3 - i)),
                    d(100), close.max(d(100)), close.min(d(100)), close);
        }).toList();
    }

    private FuturesCandleStrategyPipeline.Candidate candidate(int close, String provenance) {
        var execution = new FuturesReplayEngine.Candle(
                decision.plus(Duration.ofMinutes(5)), d(100), d(101), d(98), d(99));
        var replay = new FuturesReplayEngine.Request(provenance, "LONG",
                d(100), d(99), d(102), d(1),
                new BigDecimal("0.05"), new BigDecimal("0.05"), List.of(execution));
        return new FuturesCandleStrategyPipeline.Candidate(decision,
                history(Duration.ofMinutes(5), close), history(Duration.ofMinutes(15), close),
                history(Duration.ofHours(1), close), history(Duration.ofHours(4), close), replay);
    }

    @Test void closedCandlesDriveReplayAndNetPerformance() {
        var report = FuturesCandleStrategyPipeline.evaluate(
                List.of(candidate(102, "SYNTHETIC_FIXTURE")));
        assertEquals("OFFLINE_ONLY", report.mode());
        assertFalse(report.liveSignalEligible());
        assertEquals(1, report.performance().executed());
        assertEquals("STOP", report.results().get(0).exitReason());
        assertTrue(report.performance().totalNetPnl().signum() < 0);
    }

    @Test void weakMomentumWaitsWithoutTrade() {
        var report = FuturesCandleStrategyPipeline.evaluate(
                List.of(candidate(100, "HISTORICAL_REPLAY")));
        assertEquals(1, report.waits());
        assertEquals(0, report.performance().executed());
    }

    @Test void rejectsFutureScoringCandle() {
        var original = candidate(102, "SYNTHETIC_FIXTURE");
        var history = original.fiveMinutes();
        var last = history.get(2);
        var future = new FuturesReplayEngine.Candle(decision.minusSeconds(60),
                last.open(), last.high(), last.low(), last.close());
        var tampered = new FuturesCandleStrategyPipeline.Candidate(decision,
                List.of(history.get(0), history.get(1), future),
                original.fifteenMinutes(), original.oneHour(), original.fourHours(),
                original.replayRequest());
        assertThrows(IllegalArgumentException.class,
                () -> FuturesCandleStrategyPipeline.evaluate(List.of(tampered)));
    }

    @Test void rejectsLiveInput() {
        assertThrows(IllegalArgumentException.class,
                () -> FuturesCandleStrategyPipeline.evaluate(
                        List.of(candidate(102, "LIVE_EXCHANGE"))));
    }

    @Test void rejectsExecutionAtDecisionTimestamp() {
        var c = candidate(102, "SYNTHETIC_FIXTURE");
        var candle = new FuturesReplayEngine.Candle(decision, d(100), d(101), d(98), d(99));
        var replay = new FuturesReplayEngine.Request("SYNTHETIC_FIXTURE", "LONG",
                d(100), d(99), d(102), d(1),
                new BigDecimal("0.05"), new BigDecimal("0.05"), List.of(candle));
        var invalid = new FuturesCandleStrategyPipeline.Candidate(decision, c.fiveMinutes(),
                c.fifteenMinutes(), c.oneHour(), c.fourHours(), replay);
        assertThrows(IllegalArgumentException.class,
                () -> FuturesCandleStrategyPipeline.evaluate(List.of(invalid)));
    }
}
