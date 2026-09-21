package com.coinmind.indicator.service;

import com.coinmind.indicator.model.TechnicalIndicators;
import com.coinmind.market.model.Candlestick;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalIndicatorServiceTest {

    private final TechnicalIndicatorService service = new TechnicalIndicatorService();

    @Test
    void calculatesCoreIndicators() {
        List<Candlestick> candles = new ArrayList<>();

        for (int i = 0; i < 250; i++) {
            BigDecimal price = BigDecimal.valueOf(50_000L + (i * 20L));
            Instant open = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 60L);

            candles.add(new Candlestick(
                    "BTCUSDT",
                    "1m",
                    open,
                    open.plusSeconds(59),
                    price,
                    price.add(BigDecimal.valueOf(50)),
                    price.subtract(BigDecimal.valueOf(50)),
                    price.add(BigDecimal.valueOf(10)),
                    BigDecimal.valueOf(100 + i),
                    BigDecimal.valueOf(1_000_000L + i),
                    100 + i,
                    true,
                    open.plusSeconds(59)
            ));
        }

        TechnicalIndicators result = service.calculate("BTCUSDT", "1m", candles);

        assertThat(result.ema20()).isNotNull();
        assertThat(result.ema50()).isNotNull();
        assertThat(result.ema200()).isNotNull();
        assertThat(result.rsi14()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
        assertThat(result.bollingerUpper()).isGreaterThan(result.bollingerLower());
        assertThat(result.trendScore()).isPositive();
    }
}
