package com.coinmind.trade.futures;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline-only bridge: closed 5m/15m/1h/4h candles -> score -> decision -> replay -> metrics.
 * Rejects future/unclosed scoring candles and uses strictly post-decision execution candles.
 * No live trading signal, order execution, or empirically validated profitability.
 */
public final class FuturesCandleStrategyPipeline {
    private FuturesCandleStrategyPipeline() {}

    public static FuturesStrategyReplayPipeline.Report evaluate(List<Candidate> candidates) {
        if (candidates == null) throw new IllegalArgumentException("Candidates required");
        List<FuturesStrategyReplayPipeline.Candidate> mapped = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c == null || c.decisionTime() == null || c.replayRequest() == null)
                throw new IllegalArgumentException("Complete candidate required");
            FuturesReplayEngine.Request replay = c.replayRequest();
            var input = FuturesClosedCandleScoring.score(replay.provenance(), c.decisionTime(),
                    c.fiveMinutes(), c.fifteenMinutes(), c.oneHour(), c.fourHours());
            mapped.add(new FuturesStrategyReplayPipeline.Candidate(c.decisionTime(), input, replay));
        }
        return FuturesStrategyReplayPipeline.evaluate(mapped);
    }

    public record Candidate(Instant decisionTime,
                            List<FuturesReplayEngine.Candle> fiveMinutes,
                            List<FuturesReplayEngine.Candle> fifteenMinutes,
                            List<FuturesReplayEngine.Candle> oneHour,
                            List<FuturesReplayEngine.Candle> fourHours,
                            FuturesReplayEngine.Request replayRequest) {}
}
