package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CoinApiFixtureControllerTest {
    @Test void disabledByDefault() {
        assertThrows(ResponseStatusException.class,
                () -> new CoinApiFixtureController(false).get("BTCUSDT", "NORMAL"));
    }
    @Test void normalFixtureNeverEligibleForLiveSignals() {
        var value = new CoinApiFixtureController(true).get("BTCUSDT", "NORMAL");
        assertEquals("SYNTHETIC_FIXTURE", value.provenance());
        assertFalse(value.liveSignalEligible());
        assertEquals("COINAPI", value.upstreamProvider());
    }
    @Test void missingAndStaleAreExplicit() {
        var fixture = new CoinApiFixtureController(true);
        assertNull(fixture.get("ETHUSDT", "MISSING_OI").openInterest());
        assertEquals("STALE", fixture.get("SOLUSDT", "STALE").status());
        assertEquals("UNAVAILABLE", fixture.get("BTCUSDT", "DISCONNECTED").status());
    }
    @Test void rejectUnsupportedSymbols() {
        assertThrows(ResponseStatusException.class,
                () -> new CoinApiFixtureController(true).get("DOGEUSDT", "NORMAL"));
    }
}
