package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FuturesPaperRiskReportTest {
    private static BigDecimal d(String v) { return new BigDecimal(v); }
    private static FuturesHistoricalWalkForwardRunner.EquityPoint point(String at, String balance, String pnl) {
        return new FuturesHistoricalWalkForwardRunner.EquityPoint(Instant.parse(at), d(balance), d(pnl));
    }
    private static FuturesHistoricalWalkForwardRunner.DynamicReport report() {
        return new FuturesHistoricalWalkForwardRunner.DynamicReport("OFFLINE_ONLY", false,
                d("1000"), d("985"), d("20"), 0, 1, 3,
                List.of(point("2026-10-29T22:00:00Z", "990", "-10"),
                        point("2026-10-30T01:00:00Z", "980", "-10"),
                        point("2026-10-30T02:00:00Z", "985", "5")),
                null, "TEST");
    }
    @Test void reconstructsCurrentUtcDayFromOpeningEquity() {
        var snapshot = FuturesPaperRiskReport.fromReplay("SYNTHETIC_FIXTURE",
                report(), d("2"), Instant.parse("2026-10-30T03:00:00Z"));
        assertEquals("LOSS_MONITORING", snapshot.status());
        assertEquals(0, snapshot.dailyLossLimitQuote().compareTo(d("19.8")));
        assertEquals(0, snapshot.remainingLossBudgetQuote().compareTo(d("14.8")));
        assertEquals(0, snapshot.currentEquity().compareTo(d("985")));
        assertFalse(snapshot.liveSignalEligible());
    }
    @Test void newUtcDayStartsFreshRiskBudget() {
        var snapshot = FuturesPaperRiskReport.fromReplay("HISTORICAL_REPLAY",
                report(), d("2"), Instant.parse("2026-10-31T01:00:00Z"));
        assertEquals("NORMAL", snapshot.status());
        assertEquals(0, snapshot.remainingLossBudgetQuote().compareTo(d("19.70")));
    }
    @Test void rejectsEquityEventsFromFuture() {
        assertThrows(IllegalArgumentException.class, () ->
                FuturesPaperRiskReport.fromReplay("SYNTHETIC_FIXTURE", report(),
                        d("2"), Instant.parse("2026-10-30T01:30:00Z")));
    }
    @Test void rejectsLiveProvenance() {
        assertThrows(IllegalArgumentException.class, () ->
                FuturesPaperRiskReport.fromReplay("LIVE_EXCHANGE", report(),
                        d("2"), Instant.parse("2026-10-30T03:00:00Z")));
    }
}
