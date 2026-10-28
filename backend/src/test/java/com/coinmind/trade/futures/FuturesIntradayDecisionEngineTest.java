package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FuturesIntradayDecisionEngineTest {
    private BigDecimal d(int v) { return BigDecimal.valueOf(v); }
    private FuturesIntradayDecisionEngine.Input input(String source, boolean closed, int m5, int m15, int h1, int h4) {
        return new FuturesIntradayDecisionEngine.Input(source, closed, d(m5), d(m15), d(h1), d(h4));
    }
    @Test void bullishAlignmentIsOnlyOfflineWatch() {
        var result = FuturesIntradayDecisionEngine.evaluate(input("HISTORICAL_REPLAY",true,75,65,30,35));
        assertEquals("WATCH_LONG",result.action());
    }
    @Test void bearishAlignmentIsOnlyOfflineWatch() {
        assertEquals("WATCH_SHORT",FuturesIntradayDecisionEngine.evaluate(
            input("SYNTHETIC_FIXTURE",true,-75,-65,-30,-35)).action());
    }
    @Test void conflictingHigherTimeframeReturnsWait() {
        assertEquals("WAIT",FuturesIntradayDecisionEngine.evaluate(
            input("HISTORICAL_REPLAY",true,80,70,-30,35)).action());
    }
    @Test void noUnclosedCandleSignal() {
        assertEquals("UNCONFIRMED_CANDLES",FuturesIntradayDecisionEngine.evaluate(
            input("HISTORICAL_REPLAY",false,80,80,50,50)).reason());
    }
    @Test void liveDataNotAcceptedInExperimentalEngine() {
        assertEquals("NON_OFFLINE_DATA",FuturesIntradayDecisionEngine.evaluate(
            input("LIVE_EXCHANGE",true,80,80,50,50)).reason());
    }
}
