package com.coinmind.trade.service;

import com.coinmind.ai.model.MarketContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeSetupServiceTest {

    private final TradeSetupService service = new TradeSetupService(null, null, null);

    @Test
    void createsBuySetupForStrongBullishContext() {
        var setup = service.build(context(
                "72",
                "BULLISH",
                "55",
                "62",
                "1.60",
                "1.30",
                "MEDIUM"
        ));

        assertEquals("BUY", setup.action());
        assertEquals("LONG", setup.side());
        assertNotNull(setup.entryLow());
        assertNotNull(setup.invalidationPrice());
        assertTrue(setup.target1().compareTo(setup.entryHigh()) > 0);
        assertTrue(setup.target2().compareTo(setup.target1()) > 0);
        assertEquals(BigDecimal.valueOf(1.5), setup.riskReward1());
        assertEquals(BigDecimal.valueOf(2.5), setup.riskReward2());
    }

    @Test
    void waitsWhenSignalIsWeak() {
        var setup = service.build(context(
                "20",
                "BULLISH",
                "52",
                "10",
                "1.05",
                "0.90",
                "LOW"
        ));

        assertEquals("WAIT", setup.action());
        assertEquals("NONE", setup.side());
        assertNull(setup.entryLow());
        assertNull(setup.target1());
    }

    @Test
    void downgradesExtremeRsiToWatch() {
        var setup = service.build(context(
                "80",
                "BULLISH",
                "82",
                "70",
                "1.80",
                "1.40",
                "MEDIUM"
        ));

        assertEquals("WATCH_BUY", setup.action());
    }

    private MarketContext context(
            String score,
            String bias,
            String rsi,
            String momentum,
            String buySellRatio,
            String volumeRatio,
            String risk
    ) {
        BigDecimal price = BigDecimal.valueOf(100_000);
        BigDecimal zero = BigDecimal.ZERO;

        return new MarketContext(
                "BTCUSDT",
                "1h",
                Instant.now(),
                new MarketContext.PriceContext(
                        price,
                        BigDecimal.valueOf(99_000),
                        BigDecimal.valueOf(101_500),
                        BigDecimal.valueOf(98_500),
                        BigDecimal.valueOf(1_000_000)
                ),
                new MarketContext.TechnicalContext(
                        BigDecimal.valueOf(99_500),
                        BigDecimal.valueOf(99_000),
                        BigDecimal.valueOf(97_000),
                        new BigDecimal(rsi),
                        BigDecimal.ONE,
                        BigDecimal.valueOf(0.8),
                        BigDecimal.valueOf(0.2),
                        BigDecimal.valueOf(900),
                        BigDecimal.valueOf(100_000),
                        BigDecimal.valueOf(102_000),
                        BigDecimal.valueOf(98_000),
                        new BigDecimal(volumeRatio),
                        new BigDecimal(score),
                        new BigDecimal(momentum),
                        BigDecimal.valueOf(30)
                ),
                new MarketContext.MicrostructureContext(
                        BigDecimal.valueOf(99_990),
                        BigDecimal.valueOf(100_010),
                        BigDecimal.valueOf(20),
                        BigDecimal.valueOf(2),
                        price,
                        BigDecimal.valueOf(500),
                        BigDecimal.valueOf(300),
                        new BigDecimal(buySellRatio)
                ),
                new MarketContext.NewsContext(
                        1,
                        BigDecimal.valueOf(0.30),
                        1,
                        0,
                        0,
                        List.of()
                ),
                new MarketContext.SignalContext(
                        new BigDecimal(score),
                        bias,
                        new BigDecimal(score).abs(),
                        new BigDecimal(score).abs().compareTo(BigDecimal.valueOf(45)) >= 0,
                        risk,
                        List.of("Trend +" + score, "Momentum +" + momentum)
                )
        );
    }
}
