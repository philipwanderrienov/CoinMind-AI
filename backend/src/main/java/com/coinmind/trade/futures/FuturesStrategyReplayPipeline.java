package com.coinmind.trade.futures;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline strategy-to-replay bridge. The scored decision is strictly earlier
 * than every execution candle. No live recommendations, position routing, or orders.
 */
public final class FuturesStrategyReplayPipeline {
    private FuturesStrategyReplayPipeline() {}

    public static Report evaluate(List<Candidate> candidates) {
        if (candidates == null) throw new IllegalArgumentException("Candidates required");
        List<FuturesReplayEngine.Result> results = new ArrayList<>();
        int waits = 0;
        for (Candidate candidate : candidates) {
            if (candidate == null || candidate.decisionTime() == null
                    || candidate.decisionInput() == null || candidate.replayRequest() == null)
                throw new IllegalArgumentException("Complete candidate required");
            var decisionInput = candidate.decisionInput();
            var replay = candidate.replayRequest();
            if (!decisionInput.provenance().equals(replay.provenance())
                    || (!"SYNTHETIC_FIXTURE".equals(replay.provenance())
                        && !"HISTORICAL_REPLAY".equals(replay.provenance())))
                throw new IllegalArgumentException("Matching offline provenance required");
            if (replay.candles() == null || replay.candles().isEmpty())
                throw new IllegalArgumentException("Execution candles required");
            for (var candle : replay.candles()) {
                if (candle == null || candle.time() == null
                        || !candle.time().isAfter(candidate.decisionTime()))
                    throw new IllegalArgumentException("Execution candles must occur after decision time");
            }
            var decision = FuturesIntradayDecisionEngine.evaluate(decisionInput);
            if ("WAIT".equals(decision.action())) { waits++; continue; }
            String requiredSide = switch (decision.action()) {
                case "WATCH_LONG" -> "LONG";
                case "WATCH_SHORT" -> "SHORT";
                default -> throw new IllegalStateException("Unknown decision");
            };
            if (!requiredSide.equals(replay.side()))
                throw new IllegalArgumentException("Replay side conflicts with decision");
            results.add(FuturesReplayEngine.run(replay));
        }
        return new Report("OFFLINE_ONLY", false, waits, List.copyOf(results),
                FuturesPerformanceAnalytics.summarize(results),
                "Experimental score inputs must be built from closed pre-decision candles; timestamp checks alone cannot prove that. No funding, liquidation or exchange constraints.");
    }

    public record Candidate(Instant decisionTime,
                            FuturesIntradayDecisionEngine.Input decisionInput,
                            FuturesReplayEngine.Request replayRequest) {}
    public record Report(String mode, boolean liveSignalEligible, int waits,
                         List<FuturesReplayEngine.Result> results,
                         FuturesPerformanceAnalytics.Summary performance, String limitations) {}
}
