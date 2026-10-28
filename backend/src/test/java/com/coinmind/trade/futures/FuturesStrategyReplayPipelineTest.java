package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FuturesStrategyReplayPipelineTest {
    private BigDecimal d(int v) { return BigDecimal.valueOf(v); }
    private FuturesStrategyReplayPipeline.Candidate candidate(int score, String side, Instant executionTime) {
        var decisionTime = Instant.parse("2026-10-28T00:00:00Z");
        var input = new FuturesIntradayDecisionEngine.Input("SYNTHETIC_FIXTURE", true,
                d(score), d(score), d(score), d(score));
        var candle = new FuturesReplayEngine.Candle(executionTime, d(100), d(103), d(98), d(100));
        var replay = new FuturesReplayEngine.Request("SYNTHETIC_FIXTURE", side,
                d(100), d(side.equals("LONG") ? 99 : 101),
                d(side.equals("LONG") ? 102 : 98), d(1),
                new BigDecimal("0.05"), new BigDecimal("0.05"), List.of(candle));
        return new FuturesStrategyReplayPipeline.Candidate(decisionTime, input, replay);
    }

    @Test void integratesDecisionReplayAndNetPerformance() {
        var report = FuturesStrategyReplayPipeline.evaluate(List.of(
                candidate(80, "LONG", Instant.parse("2026-10-28T00:05:00Z"))));
        assertEquals("OFFLINE_ONLY", report.mode());
        assertFalse(report.liveSignalEligible());
        assertEquals(1, report.performance().executed());
        assertTrue(report.performance().totalNetPnl().signum() < 0);
    }

    @Test void waitDoesNotCreateSyntheticTrade() {
        var report = FuturesStrategyReplayPipeline.evaluate(List.of(
                candidate(10, "LONG", Instant.parse("2026-10-28T00:05:00Z"))));
        assertEquals(1, report.waits());
        assertEquals(0, report.performance().executed());
    }

    @Test void rejectsSameCandleExecutionAndLookahead() {
        assertThrows(IllegalArgumentException.class, () -> FuturesStrategyReplayPipeline.evaluate(
                List.of(candidate(80, "LONG", Instant.parse("2026-10-28T00:00:00Z")))));
    }

    @Test void rejectsWrongTradeSide() {
        assertThrows(IllegalArgumentException.class, () -> FuturesStrategyReplayPipeline.evaluate(
                List.of(candidate(80, "SHORT", Instant.parse("2026-10-28T00:05:00Z")))));
    }
}
