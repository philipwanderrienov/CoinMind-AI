package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FuturesOfflineCsvBundleTest {
    private static String csv(Duration timeframe) {
        var out = new StringBuilder("open_time_utc,open,high,low,close\n");
        Instant at = Instant.parse("2026-11-02T00:00:00Z");
        for (int i=0;i<3;i++) {
            out.append(at.plus(timeframe.multipliedBy(i))).append(",100,110,90,100\n");
        }
        return out.toString();
    }

    @Test void readsFourSynchronizedFixtureStreams() throws Exception {
        var input = FuturesOfflineCsvBundle.read("SYNTHETIC_FIXTURE",
                new StringReader(csv(Duration.ofMinutes(5))),
                new StringReader(csv(Duration.ofMinutes(15))),
                new StringReader(csv(Duration.ofHours(1))),
                new StringReader(csv(Duration.ofHours(4))), 2,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(3, input.m5().size());
        assertEquals(3, input.h4().size());
    }

    @Test void rejectsLiveSourceWithoutReadingFiles() {
        assertThrows(IllegalArgumentException.class, () ->
                FuturesOfflineCsvBundle.read("LIVE_EXCHANGE", null,null,null,null,2,
                        BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,
                        BigDecimal.ZERO,BigDecimal.ZERO));
    }
}
