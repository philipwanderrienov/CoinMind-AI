package com.coinmind.polymarket.model;

import java.time.Instant;
import java.util.List;

public record PredictionSourceHealth(
        List<SourceStatus> sources,
        Instant checkedAt
) {
    public record SourceStatus(
            String source,
            String state,
            String detail,
            Instant lastCheckedAt,
            int lastSignalCount
    ) {
    }
}
