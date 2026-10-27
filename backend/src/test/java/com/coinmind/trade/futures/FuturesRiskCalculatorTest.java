package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FuturesRiskCalculatorTest {
    private final FuturesRiskCalculator calculator = new FuturesRiskCalculator();
    private BigDecimal d(String value) { return new BigDecimal(value); }

    @Test
    void sizesLongWithRoundTripCostsAndMarginCap() {
        var result = calculator.calculate(new FuturesRiskCalculator.Request(
                "LONG", d("1000"), d("100"), d("99"), d("1"), d("3"), d("0.04"), d("0.05")));
        assertTrue(result.estimatedStopLossUsdt().compareTo(d("10")) <= 0);
        assertTrue(result.estimatedInitialMarginUsdt().compareTo(d("200")) <= 0);
        assertEquals("PAPER_ONLY", result.readiness());
    }

    @Test
    void rejectsWrongStopForShort() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(
                new FuturesRiskCalculator.Request("SHORT", d("1000"), d("100"), d("99"),
                        d("1"), d("3"), d("0.04"), d("0.05"))));
    }

    @Test
    void rejectsAggressiveLeverage() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(
                new FuturesRiskCalculator.Request("LONG", d("1000"), d("100"), d("99"),
                        d("1"), d("20"), d("0.04"), d("0.05"))));
    }
}
