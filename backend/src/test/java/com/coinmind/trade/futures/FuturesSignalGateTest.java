package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FuturesSignalGateTest {
    private final Instant now = Instant.parse("2026-10-28T05:00:00Z");
    private FuturesSignalGate.Request input(String provenance, Instant event, boolean rules) {
        return new FuturesSignalGate.Request("BTCUSDT", "VENUE", "VENUE-BTCUSDT-PERP",
                provenance, event, now, new BigDecimal("80000"),
                new BigDecimal("79999"), new BigDecimal("1234"), BigDecimal.ZERO,
                true, true, rules, true);
    }
    @Test void syntheticAlwaysBlocked() {
        assertEquals("NON_LIVE_DATA", FuturesSignalGate.evaluate(
                input("SYNTHETIC_FIXTURE", now, true), now).reason());
    }
    @Test void staleAlwaysBlocked() {
        assertEquals("STALE_OR_INVALID_TIMESTAMP", FuturesSignalGate.evaluate(
                input("LIVE_EXCHANGE", now.minusSeconds(31), true), now).reason());
    }
    @Test void unknownVenueContractRulesBlock() {
        assertEquals("EXECUTION_DATA_INCOMPLETE", FuturesSignalGate.evaluate(
                input("LIVE_EXCHANGE", now, false), now).reason());
    }
    @Test void fullyValidatedDataOnlyPassesDataGate() {
        var result = FuturesSignalGate.evaluate(input("LIVE_EXCHANGE", now, true), now);
        assertTrue(result.eligible());
        assertEquals("DATA_VALIDATED_ONLY", result.reason());
    }
}
