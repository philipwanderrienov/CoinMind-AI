package com.coinmind.trade.futures;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline-only orchestration of replay and performance reporting.
 * Data gate and risk guard are evaluated to prove no simulated input is live-eligible.
 * No HTTP endpoints, live signals or order execution.
 */
public final class FuturesOfflineValidationPipeline {
    private FuturesOfflineValidationPipeline() {}

    public static Report evaluate(List<FuturesReplayEngine.Request> requests, Instant evaluationTime) {
        if (requests == null || evaluationTime == null) throw new IllegalArgumentException("Requests and time required");
        List<FuturesReplayEngine.Result> outcomes = new ArrayList<>();
        for (FuturesReplayEngine.Request request : requests) {
            if (request == null || (!"SYNTHETIC_FIXTURE".equals(request.provenance())
                    && !"HISTORICAL_REPLAY".equals(request.provenance()))) {
                throw new IllegalArgumentException("Offline data provenance required");
            }
            outcomes.add(FuturesReplayEngine.run(request));
        }
        var metrics = FuturesPerformanceAnalytics.summarize(outcomes);
        return new Report("OFFLINE_ONLY", false, List.copyOf(outcomes), metrics,
                "Historical or synthetic results only. No live Futures signals, orders, funding or liquidation model.");
    }

    public record Report(String mode, boolean liveSignalEligible,
                         List<FuturesReplayEngine.Result> outcomes,
                         FuturesPerformanceAnalytics.Summary performance, String limitations) {}
}
