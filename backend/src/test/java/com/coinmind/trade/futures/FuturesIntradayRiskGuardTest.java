package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FuturesIntradayRiskGuardTest {
    private FuturesIntradayRiskGuard.Request state(String source, boolean fresh, String pnl,
            int positions, int streak) {
        return new FuturesIntradayRiskGuard.Request(source, fresh, true,
                new BigDecimal("1000"), new BigDecimal(pnl), new BigDecimal("2"),
                positions, 2, streak, 3);
    }

    @Test void syntheticMustNeverPass() {
        var result = FuturesIntradayRiskGuard.evaluate(state("SYNTHETIC_FIXTURE", true, "0", 0, 0));
        assertFalse(result.allowed());
        assertEquals("NO_DATA", result.code());
    }
    @Test void staleFuturesDataMustNeverPass() {
        assertEquals("NO_DATA",
            FuturesIntradayRiskGuard.evaluate(state("LIVE_EXCHANGE", false, "0", 0, 0)).code());
    }
    @Test void dailyLossLimitBlocks() {
        assertEquals("DAILY_LOSS_LIMIT",
            FuturesIntradayRiskGuard.evaluate(state("LIVE_EXCHANGE", true, "-20", 0, 0)).code());
    }
    @Test void positionLimitBlocks() {
        assertEquals("MAX_POSITIONS",
            FuturesIntradayRiskGuard.evaluate(state("LIVE_EXCHANGE", true, "0", 2, 0)).code());
    }
    @Test void lossStreakBlocks() {
        assertEquals("LOSS_COOLDOWN",
            FuturesIntradayRiskGuard.evaluate(state("LIVE_EXCHANGE", true, "0", 0, 3)).code());
    }
    @Test void safePreflightPassesWithoutExecutingAnything() {
        var result = FuturesIntradayRiskGuard.evaluate(state("LIVE_EXCHANGE", true, "-5", 0, 0));
        assertTrue(result.allowed());
        assertEquals("PASS", result.code());
    }
}
