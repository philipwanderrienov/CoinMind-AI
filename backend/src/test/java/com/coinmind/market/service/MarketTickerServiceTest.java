package com.coinmind.market.service;

import com.coinmind.market.model.TickerSnapshot;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MarketTickerServiceTest {

    private final MarketTickerService service = new MarketTickerService();

    @Test
    void storesLatestTickerBySymbol() {
        TickerSnapshot snapshot = snapshot("BTCUSDT", "82000");

        service.publish(snapshot);

        assertThat(service.getLatest("btcusdt")).contains(snapshot);
        assertThat(service.getAllLatest()).containsExactly(snapshot);
    }

    @Test
    void publishesRealtimeUpdates() {
        TickerSnapshot snapshot = snapshot("ETHUSDT", "3000");

        StepVerifier.create(service.stream().take(1))
                .then(() -> service.publish(snapshot))
                .expectNext(snapshot)
                .verifyComplete();
    }

    private TickerSnapshot snapshot(String symbol, String close) {
        return new TickerSnapshot(
                symbol,
                new BigDecimal(close),
                new BigDecimal("1"),
                new BigDecimal("1"),
                new BigDecimal("1"),
                new BigDecimal("1"),
                new BigDecimal("1"),
                Instant.parse("2026-09-21T00:00:00Z")
        );
    }
}
