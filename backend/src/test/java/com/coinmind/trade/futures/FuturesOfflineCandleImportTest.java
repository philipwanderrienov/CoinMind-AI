package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FuturesOfflineCandleImportTest {
    private static final String HEADER = "open_time_utc,open,high,low,close\n";
    private static final String VALID = HEADER
            + "2026-11-02T00:00:00Z,100,110,90,105\n"
            + "2026-11-02T00:05:00Z,105,112,102,111\n"
            + "2026-11-02T00:10:00Z,111,115,109,110\n";

    @Test void importsThreeConsecutiveFiveMinuteCandles() throws Exception {
        var points = FuturesOfflineCandleImport.parse(new StringReader(VALID), Duration.ofMinutes(5));
        assertEquals(3, points.size());
        assertEquals("110", points.get(2).close().toPlainString());
    }

    @Test void rejectsMissingCandles() {
        String csv = VALID.replace("2026-11-02T00:05:00Z,105,112,102,111\n", "");
        assertThrows(IllegalArgumentException.class,
                () -> FuturesOfflineCandleImport.parse(new StringReader(csv), Duration.ofMinutes(5)));
    }

    @Test void rejectsWrongTimeframeAndInvalidOHLC() {
        assertThrows(IllegalArgumentException.class,
                () -> FuturesOfflineCandleImport.parse(new StringReader(VALID), Duration.ofMinutes(15)));
        assertThrows(IllegalArgumentException.class,
                () -> FuturesOfflineCandleImport.parse(new StringReader(
                        VALID.replace("100,110,90,105", "100,90,110,105")), Duration.ofMinutes(5)));
    }

    @Test void rejectsIncorrectHeaderAndInvalidOrigin() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> FuturesOfflineCandleImport.parse(new StringReader(VALID.replace("open_time_utc", "timestamp")),
                        Duration.ofMinutes(5)));
        var bars = FuturesOfflineCandleImport.parse(new StringReader(VALID), Duration.ofMinutes(5));
        assertThrows(IllegalArgumentException.class, () ->
                FuturesOfflineCandleImport.assemble("LIVE_EXCHANGE", bars, bars, bars, bars, 2,
                        java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, java.math.BigDecimal.ONE,
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO));
    }
    @Test void acceptsMatchingAggregatedFifteenMinuteBars() throws Exception {
        var m5 = FuturesOfflineCandleImport.parse(new StringReader(VALID), Duration.ofMinutes(5));
        var higher = java.util.List.of(new FuturesReplayEngine.Candle(
                java.time.Instant.parse("2026-11-02T00:00:00Z"),
                new java.math.BigDecimal("100"), new java.math.BigDecimal("115"),
                new java.math.BigDecimal("90"), new java.math.BigDecimal("110")));
        assertDoesNotThrow(() -> FuturesOfflineCandleImport.validateCrossTimeframe(
                m5, higher, Duration.ofMinutes(15)));
    }

    @Test void rejectsMismatchedAggregatedFifteenMinuteBars() throws Exception {
        var m5 = FuturesOfflineCandleImport.parse(new StringReader(VALID), Duration.ofMinutes(5));
        var higher = java.util.List.of(new FuturesReplayEngine.Candle(
                java.time.Instant.parse("2026-11-02T00:00:00Z"),
                new java.math.BigDecimal("100"), new java.math.BigDecimal("116"),
                new java.math.BigDecimal("90"), new java.math.BigDecimal("110")));
        assertThrows(IllegalArgumentException.class, () -> FuturesOfflineCandleImport.validateCrossTimeframe(
                m5, higher, Duration.ofMinutes(15)));
    }

}
