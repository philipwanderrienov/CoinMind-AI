package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FuturesPerformanceAnalyticsTest {
    private FuturesReplayEngine.Result r(String reason, String pnl) {
        BigDecimal value = pnl == null ? null : new BigDecimal(pnl);
        return new FuturesReplayEngine.Result("SYNTHETIC_FIXTURE", reason,
                value == null ? null : BigDecimal.ONE,
                value == null ? null : BigDecimal.ONE,
                value == null ? null : value.add(new BigDecimal("0.1")),
                value == null ? null : new BigDecimal("0.1"), value, "fixture");
    }

    @Test void computesNetExpectancyAndDrawdown() {
        var summary = FuturesPerformanceAnalytics.summarize(
                List.of(r("TARGET", "3"), r("STOP", "-2"), r("STOP", "-4"), r("NO_ENTRY", null)));
        assertEquals(3, summary.executed());
        assertEquals(1, summary.noEntry());
        assertEquals(0, summary.totalNetPnl().compareTo(new BigDecimal("-3")));
        assertEquals(0, summary.expectancyPerTrade().compareTo(new BigDecimal("-1")));
        assertEquals(0, summary.maxDrawdownQuote().compareTo(new BigDecimal("6")));
        assertEquals(0, summary.profitFactor().compareTo(new BigDecimal("0.5")));
    }

    @Test void emptyHistoryHasUndefinedStatistics() {
        var summary = FuturesPerformanceAnalytics.summarize(List.of());
        assertEquals(0, summary.executed());
        assertNull(summary.profitFactor());
        assertNull(summary.winRatePercent());
        assertNull(summary.expectancyPerTrade());
    }

    @Test void noLossesDoNotProduceFakeInfiniteProfitFactor() {
        var summary = FuturesPerformanceAnalytics.summarize(List.of(r("TARGET", "3")));
        assertNull(summary.profitFactor());
        assertEquals("UNDEFINED_NO_LOSSES", summary.profitFactorStatus());
    }

    @Test void refusesLiveResults() {
        var invalid = new FuturesReplayEngine.Result("LIVE", "TARGET", BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, "invalid");
        assertThrows(IllegalArgumentException.class,
                () -> FuturesPerformanceAnalytics.summarize(List.of(invalid)));
    }
}
