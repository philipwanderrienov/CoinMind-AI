package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FuturesMarketDataControllerTest {
    @Test
    void defaultsToDisabledWithoutNetworkAccess() {
        var controller = new FuturesMarketDataController(false, "http://127.0.0.1:1");
        var snapshot = controller.snapshot("BTCUSDT").block();
        assertNotNull(snapshot);
        assertEquals("DISABLED", snapshot.status());
        assertNull(snapshot.markPrice());
    }

    @Test
    void rejectsUnknownSymbolsBeforeNetworkAccess() {
        var controller = new FuturesMarketDataController(true, "http://127.0.0.1:1");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.snapshot("DOGEUSDT").block());
    }
}
