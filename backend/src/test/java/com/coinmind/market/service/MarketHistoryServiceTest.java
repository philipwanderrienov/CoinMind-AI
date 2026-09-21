package com.coinmind.market.service;

import com.coinmind.market.model.Candlestick;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketHistoryServiceTest {

    private final MarketHistoryService service = new MarketHistoryService();

    @Test
    void storesAndReturnsCandlesInChronologicalOrder() {
        Candlestick newer = candle("2026-09-21T00:01:00Z", "82100");
        Candlestick older = candle("2026-09-21T00:00:00Z", "82000");

        service.replace("BTCUSDT", "1m", List.of(newer, older));

        assertThat(service.get("BTCUSDT", "1m", 500))
                .extracting(Candlestick::close)
                .containsExactly(new BigDecimal("82000"), new BigDecimal("82100"));
    }

    @Test
    void realtimeUpsertReplacesMatchingOpenTime() {
        Candlestick initial = candle("2026-09-21T00:00:00Z", "82000");
        Candlestick updated = candle("2026-09-21T00:00:00Z", "82200");

        service.replace("BTCUSDT", "1m", List.of(initial));
        service.upsert(updated);

        assertThat(service.get("BTCUSDT", "1m", 500))
                .containsExactly(updated);
    }

    private Candlestick candle(String openTime, String close) {
        Instant open = Instant.parse(openTime);

        return new Candlestick(
                "BTCUSDT",
                "1m",
                open,
                open.plusSeconds(59),
                new BigDecimal("81000"),
                new BigDecimal("82500"),
                new BigDecimal("80500"),
                new BigDecimal(close),
                new BigDecimal("10"),
                new BigDecimal("820000"),
                100,
                true,
                open.plusSeconds(59)
        );
    }
}
