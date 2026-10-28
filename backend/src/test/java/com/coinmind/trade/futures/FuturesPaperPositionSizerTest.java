package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FuturesPaperPositionSizerTest {
    private BigDecimal d(String value) { return new BigDecimal(value); }
    private FuturesPaperPositionSizer.Request request(String source, String side, String risk) {
        return new FuturesPaperPositionSizer.Request(source, side, d("1000"),
                d("100"), d("LONG".equals(side) ? "99" : "101"),
                d(risk), d("3"), d("0.05"), d("0.05"));
    }
    @Test void longSizingStaysWithinRiskAndMarginLimits() {
        var result = FuturesPaperPositionSizer.size(request("SYNTHETIC_FIXTURE", "LONG", "1"));
        assertTrue(result.eligible());
        assertTrue(result.quantity().signum() > 0);
        assertTrue(result.estimate().estimatedStopLossUsdt().compareTo(d("10")) <= 0);
        assertTrue(result.estimate().estimatedInitialMarginUsdt().compareTo(d("200")) <= 0);
        assertEquals("PAPER_ONLY", result.status());
    }
    @Test void shortSizingHonorsSameLimits() {
        var result = FuturesPaperPositionSizer.size(request("HISTORICAL_REPLAY", "SHORT", "1"));
        assertTrue(result.eligible());
        assertTrue(result.estimate().estimatedStopLossUsdt().compareTo(d("10")) <= 0);
    }
    @Test void rejectsLiveSource() {
        assertThrows(IllegalArgumentException.class,
                () -> FuturesPaperPositionSizer.size(request("LIVE_EXCHANGE", "LONG", "1")));
    }
    @Test void rejectsExcessiveRisk() {
        assertThrows(IllegalArgumentException.class,
                () -> FuturesPaperPositionSizer.size(request("SYNTHETIC_FIXTURE", "LONG", "2")));
    }
}
