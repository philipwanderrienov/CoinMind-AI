package com.coinmind.ai.status;

import java.time.Instant;

public record AiEngineStatus(
        String state,
        String provider,
        String model,
        boolean enabled,
        boolean apiKeyConfigured,
        String message,
        Integer lastHttpStatus,
        Instant lastCheckedAt
) {
}
