package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FuturesPaperEquityLedgerTest {
    private FuturesPaperEquityLedger.Trade trade(String time, String pnl) {
        return new FuturesPaperEquityLedger.Trade("SYNTHETIC_FIXTURE",
                Instant.parse(time), new BigDecimal(pnl));
    }
    @Test void evolvesEquityAndDrawdownFromClosedTradePnl() {
        var result = FuturesPaperEquityLedger.evaluate(new BigDecimal("1000"),
                new BigDecimal("5"), List.of(
                        trade("2026-10-28T00:00:00Z","30"),
                        trade("2026-10-28T00:10:00Z","-20")));
        assertEquals(0, result.finalEquity().compareTo(new BigDecimal("1010")));
        assertEquals(0, result.maxDrawdownQuote().compareTo(new BigDecimal("20")));
        assertEquals(2, result.acceptedTrades());
        assertFalse(result.liveSignalEligible());
    }
    @Test void stopsAfterDailyLossAndResetsOnNextUtcDay() {
        var result = FuturesPaperEquityLedger.evaluate(new BigDecimal("1000"),
                new BigDecimal("2"), List.of(
                        trade("2026-10-28T00:00:00Z","-20"),
                        trade("2026-10-28T01:00:00Z","10"),
                        trade("2026-10-29T00:00:00Z","5")));
        assertEquals(2, result.acceptedTrades());
        assertEquals(1, result.blockedTrades());
        assertEquals(0, result.finalEquity().compareTo(new BigDecimal("985")));
        assertEquals("DAILY_STOP_OR_DEPLETED", result.equityCurve().get(1).reason());
    }
    @Test void rejectsOutOfOrderTrades() {
        assertThrows(IllegalArgumentException.class, () -> FuturesPaperEquityLedger.evaluate(
                new BigDecimal("1000"), new BigDecimal("2"), List.of(
                        trade("2026-10-29T01:00:00Z","10"),
                        trade("2026-10-28T01:00:00Z","10"))));
    }
    @Test void rejectsLiveInput() {
        var live = new FuturesPaperEquityLedger.Trade("LIVE_EXCHANGE",
                Instant.parse("2026-10-28T00:00:00Z"), BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> FuturesPaperEquityLedger.evaluate(
                new BigDecimal("1000"), BigDecimal.ONE, List.of(live)));
    }
}
