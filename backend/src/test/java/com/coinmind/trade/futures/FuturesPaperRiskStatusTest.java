package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FuturesPaperRiskStatusTest {
    private FuturesPaperRiskStatus.Input input(String source, String pnl, String equity) {
        return new FuturesPaperRiskStatus.Input(source, Instant.parse("2026-10-30T02:00:00Z"),
                new BigDecimal("1000"), new BigDecimal(equity),
                new BigDecimal(pnl), new BigDecimal("2"));
    }
    @Test void normalAllowsPaperCandidates() {
        var s = FuturesPaperRiskStatus.evaluate(input("SYNTHETIC_FIXTURE", "0", "1000"));
        assertEquals("NORMAL", s.status());
        assertTrue(s.newEntriesAllowed());
        assertFalse(s.liveSignalEligible());
    }
    @Test void moderateLossIsMonitoringNotLock() {
        var s = FuturesPaperRiskStatus.evaluate(input("HISTORICAL_REPLAY", "-8", "992"));
        assertEquals("LOSS_MONITORING", s.status());
        assertTrue(s.newEntriesAllowed());
        assertEquals(0, s.remainingLossBudgetQuote().compareTo(new BigDecimal("12")));
    }
    @Test void lossAtLimitLocksFurtherEntries() {
        var s = FuturesPaperRiskStatus.evaluate(input("SYNTHETIC_FIXTURE", "-20", "980"));
        assertEquals("DAILY_RISK_LOCK", s.status());
        assertFalse(s.newEntriesAllowed());
        assertEquals(0, s.remainingLossBudgetQuote().signum());
    }
    @Test void depletedEquityLocksEvenWithoutRealizedDailyLoss() {
        assertEquals("DAILY_RISK_LOCK",
                FuturesPaperRiskStatus.evaluate(input("SYNTHETIC_FIXTURE", "0", "0")).status());
    }
    @Test void rejectsLiveProvenance() {
        assertThrows(IllegalArgumentException.class, () ->
                FuturesPaperRiskStatus.evaluate(input("LIVE_EXCHANGE", "-5", "995")));
    }
}
