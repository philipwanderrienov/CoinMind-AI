package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FuturesOfflineValidationPipelineTest {
    private BigDecimal d(String s) { return new BigDecimal(s); }
    private FuturesReplayEngine.Request trade(String source, String side, String time, String low, String high) {
        var candle = new FuturesReplayEngine.Candle(Instant.parse(time), d("100"), d(high), d(low), d("100"));
        return new FuturesReplayEngine.Request(source, side, d("100"),
                d(side.equals("LONG") ? "99" : "101"), d(side.equals("LONG") ? "102" : "98"),
                d("1"), d("0.04"), d("0.05"), List.of(candle));
    }
    @Test void aggregatesMultipleSyntheticTradesWithoutLiveEligibility() {
        var report = FuturesOfflineValidationPipeline.evaluate(List.of(
                trade("SYNTHETIC_FIXTURE","LONG","2026-10-28T00:00:00Z","98","103"),
                trade("SYNTHETIC_FIXTURE","SHORT","2026-10-28T00:01:00Z","99","102")),
                Instant.parse("2026-10-28T01:00:00Z"));
        assertEquals("OFFLINE_ONLY", report.mode());
        assertFalse(report.liveSignalEligible());
        assertEquals(2, report.performance().executed());
        assertEquals(2, report.performance().losses());
        assertTrue(report.performance().totalNetPnl().signum() < 0);
    }
    @Test void refusesMixedLiveInputs() {
        assertThrows(IllegalArgumentException.class, () -> FuturesOfflineValidationPipeline.evaluate(
                List.of(trade("LIVE_EXCHANGE","LONG","2026-10-28T00:00:00Z","98","103")), Instant.now()));
    }
    @Test void emptyReportDoesNotClaimPerformance() {
        var report = FuturesOfflineValidationPipeline.evaluate(List.of(), Instant.now());
        assertNull(report.performance().expectancyPerTrade());
        assertEquals(0, report.performance().executed());
        assertFalse(report.liveSignalEligible());
    }
}
