package com.coinmind.market.service;

import com.coinmind.market.model.Candlestick;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MarketCandlestickServiceTest {

    private final MarketCandlestickService service = new MarketCandlestickService();

    @Test
    void storesLatestCandlePerSymbolAndInterval() {
        Candlestick candle = candle("BTCUSDT", "1m", "82000");

        service.publish(candle);

        assertThat(service.getLatest("btcusdt", "1M")).contains(candle);
        assertThat(service.getLatestBySymbol("btcusdt")).containsExactly(candle);
    }

    @Test
    void replacesCurrentCandleForSameSymbolAndInterval() {
        service.publish(candle("BTCUSDT", "1m", "82000"));
        Candlestick latest = candle("BTCUSDT", "1m", "82100");

        service.publish(latest);

        assertThat(service.getAllLatest()).containsExactly(latest);
    }

    @Test
    void publishesRealtimeUpdates() {
        Candlestick candle = candle("ETHUSDT", "5m", "3000");

        StepVerifier.create(service.stream().take(1))
                .then(() -> service.publish(candle))
                .expectNext(candle)
                .verifyComplete();
    }

    private Candlestick candle(String symbol, String interval, String close) {
        return new Candlestick(
                symbol,
                interval,
                Instant.parse("2026-09-21T00:00:00Z"),
                Instant.parse("2026-09-21T00:00:59Z"),
                new BigDecimal("81000"),
                new BigDecimal("82500"),
                new BigDecimal("80500"),
                new BigDecimal(close),
                new BigDecimal("10"),
                new BigDecimal("820000"),
                100,
                false,
                Instant.parse("2026-09-21T00:00:30Z")
        );
    }
}
