package com.coinmind.ai.service;

import com.coinmind.ai.model.MarketContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSignalScoringServiceTest {

    private final MarketSignalScoringService service =
            new MarketSignalScoringService();

    @Test
    void scoresAlignedBullishInputsAsEligible() {
        var technical = technical("100", "80", "25");
        var micro = micro("1.80");
        var news = news("0.60");

        var result = service.score(
                technical,
                micro,
                news,
                BigDecimal.valueOf(45)
        );

        assertEquals("BULLISH", result.bias());
        assertTrue(result.score().compareTo(BigDecimal.valueOf(45)) > 0);
        assertTrue(result.triggerEligible());
    }

    @Test
    void keepsMixedInputsBelowTriggerThreshold() {
        var technical = technical("25", "-20", "10");
        var micro = micro("1.05");
        var news = news("-0.10");

        var result = service.score(
                technical,
                micro,
                news,
                BigDecimal.valueOf(45)
        );

        assertTrue(result.strength().compareTo(BigDecimal.valueOf(45)) < 0);
        assertTrue(!result.triggerEligible());
    }

    private MarketContext.TechnicalContext technical(
            String trend,
            String momentum,
            String volatility
    ) {
        BigDecimal zero = BigDecimal.ZERO;

        return new MarketContext.TechnicalContext(
                zero, zero, zero, zero, zero, zero, zero, zero,
                zero, zero, zero, BigDecimal.ONE,
                new BigDecimal(trend),
                new BigDecimal(momentum),
                new BigDecimal(volatility)
        );
    }

    private MarketContext.MicrostructureContext micro(String ratio) {
        BigDecimal zero = BigDecimal.ZERO;

        return new MarketContext.MicrostructureContext(
                zero, zero, zero, zero, zero, zero, zero,
                new BigDecimal(ratio)
        );
    }

    private MarketContext.NewsContext news(String sentiment) {
        return new MarketContext.NewsContext(
                1,
                new BigDecimal(sentiment),
                1,
                0,
                0,
                List.of()
        );
    }
}
