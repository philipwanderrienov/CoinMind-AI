package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FuturesClosedCandleScoringTest {
    private final Instant decision = Instant.parse("2026-10-28T12:00:00Z");
    private List<FuturesReplayEngine.Candle> bars(Duration timeframe, int lastClose) {
        return IntStream.range(0,3).mapToObj(i -> {
            Instant t = decision.minus(timeframe.multipliedBy(3-i));
            BigDecimal close = BigDecimal.valueOf(i == 2 ? lastClose : 100);
            BigDecimal high = close.max(BigDecimal.valueOf(100));
            BigDecimal low = close.min(BigDecimal.valueOf(100));
            return new FuturesReplayEngine.Candle(t, BigDecimal.valueOf(100), high, low, close);
        }).toList();
    }
    private FuturesIntradayDecisionEngine.Input score(int close) {
        return FuturesClosedCandleScoring.score("SYNTHETIC_FIXTURE",decision,
                bars(Duration.ofMinutes(5), close),bars(Duration.ofMinutes(15),close),
                bars(Duration.ofHours(1),close),bars(Duration.ofHours(4),close));
    }
    @Test void closedBullishCandlesProduceWatchLong() {
        assertEquals("WATCH_LONG", FuturesIntradayDecisionEngine.evaluate(score(102)).action());
    }
    @Test void closedBearishCandlesProduceWatchShort() {
        assertEquals("WATCH_SHORT", FuturesIntradayDecisionEngine.evaluate(score(98)).action());
    }
    @Test void rejectsCandleNotYetClosed() {
        var invalid = bars(Duration.ofMinutes(5),102);
        var shifted = List.of(invalid.get(0),invalid.get(1),
                new FuturesReplayEngine.Candle(decision.minusSeconds(60),
                        invalid.get(2).open(),invalid.get(2).high(),invalid.get(2).low(),invalid.get(2).close()));
        assertThrows(IllegalArgumentException.class, () -> FuturesClosedCandleScoring.score(
                "HISTORICAL_REPLAY",decision,shifted,
                bars(Duration.ofMinutes(15),102),bars(Duration.ofHours(1),102),bars(Duration.ofHours(4),102)));
    }
    @Test void rejectsLiveProvenance() {
        assertThrows(IllegalArgumentException.class, () -> FuturesClosedCandleScoring.score(
                "LIVE_EXCHANGE",decision,bars(Duration.ofMinutes(5),102),
                bars(Duration.ofMinutes(15),102),bars(Duration.ofHours(1),102),bars(Duration.ofHours(4),102)));
    }
}
