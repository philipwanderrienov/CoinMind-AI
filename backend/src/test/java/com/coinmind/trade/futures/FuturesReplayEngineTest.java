package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FuturesReplayEngineTest {
    private BigDecimal d(String x) { return new BigDecimal(x); }
    private FuturesReplayEngine.Candle bar(String at, String open, String high, String low, String close) {
        return new FuturesReplayEngine.Candle(Instant.parse(at), d(open), d(high), d(low), d(close));
    }
    private FuturesReplayEngine.Request request(String side, List<FuturesReplayEngine.Candle> candles) {
        return new FuturesReplayEngine.Request("SYNTHETIC_FIXTURE", side, d("100"),
                d(side.equals("LONG")?"99":"101"), d(side.equals("LONG")?"102":"98"),
                d("1"), d("0.05"), d("0.05"), candles);
    }
    @Test void conservativelyStopsIfBothTargetAndStopTouched() {
        var result = FuturesReplayEngine.run(request("LONG", List.of(
                bar("2026-10-28T00:00:00Z","100","103","98","101"))));
        assertEquals("STOP", result.exitReason());
        assertTrue(result.netPnl().signum() < 0);
        assertTrue(result.tradingFees().signum() > 0);
    }
    @Test void shortTargetHasPositiveNetPnl() {
        var result = FuturesReplayEngine.run(request("SHORT", List.of(
                bar("2026-10-28T00:00:00Z","100","100","99","99"),
                bar("2026-10-28T00:01:00Z","99","99","97","98"))));
        assertEquals("TARGET", result.exitReason());
        assertTrue(result.netPnl().signum() > 0);
    }
    @Test void rejectsLookaheadOutOfOrderCandles() {
        assertThrows(IllegalArgumentException.class, () -> FuturesReplayEngine.run(
                request("LONG", List.of(
                        bar("2026-10-28T00:01:00Z","101","101","101","101"),
                        bar("2026-10-28T00:00:00Z","100","100","100","100")))));
    }
    @Test void noEntryReturnsNoSyntheticProfit() {
        var result = FuturesReplayEngine.run(request("LONG", List.of(
                bar("2026-10-28T00:00:00Z","110","111","109","110"))));
        assertEquals("NO_ENTRY", result.exitReason());
        assertNull(result.netPnl());
    }

    @Test void sameCandleTargetCannotBeClaimedAsWin() {
        var result = FuturesReplayEngine.run(request("LONG", List.of(
                bar("2026-10-28T00:00:00Z","100","103","100","101"))));
        assertEquals("TIME_EXIT", result.exitReason());
    }
    @Test void invalidTrailingCandleIsRejectedEvenAfterEarlyStop() {
        assertThrows(IllegalArgumentException.class, () -> FuturesReplayEngine.run(
                request("LONG", List.of(
                        bar("2026-10-28T00:00:00Z","100","100","98","99"),
                        bar("2026-10-28T00:01:00Z","100","99","98","99")))));
    }
    @Test void rejectsUnknownSource() {
        var input = request("LONG", List.of(bar("2026-10-28T00:00:00Z","100","101","99","100")));
        assertThrows(IllegalArgumentException.class, () -> FuturesReplayEngine.run(
                new FuturesReplayEngine.Request("LIVE", input.side(), input.entry(), input.stop(),
                    input.target(), input.quantity(), input.feePercentPerSide(),
                    input.slippagePercentPerSide(), input.candles())));
    }
}
