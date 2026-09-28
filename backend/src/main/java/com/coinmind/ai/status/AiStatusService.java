package com.coinmind.ai.status;

import com.coinmind.ai.config.AiProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AiStatusService {

    private final AiProperties properties;

    private volatile String runtimeState = null;
    private volatile String runtimeMessage = null;
    private volatile Integer lastHttpStatus = null;
    private volatile Instant lastCheckedAt = null;

    public AiStatusService(AiProperties properties) {
        this.properties = properties;
    }

    public AiEngineStatus current() {
        boolean keyConfigured = properties.apiKey() != null
                && !properties.apiKey().isBlank();

        if (!properties.enabled()) {
            return status(
                    "DISABLED",
                    keyConfigured,
                    "AI engine is disabled."
            );
        }

        if ("mock".equalsIgnoreCase(properties.provider())) {
            return status(
                    "MOCK",
                    false,
                    "Mock AI mode is active. No paid API calls will be made."
            );
        }

        if (!keyConfigured) {
            return status(
                    "MISSING_API_KEY",
                    false,
                    "OpenAI API key is not configured."
            );
        }

        if (runtimeState != null) {
            return new AiEngineStatus(
                    runtimeState,
                    properties.provider(),
                    properties.model(),
                    true,
                    true,
                    runtimeMessage,
                    lastHttpStatus,
                    lastCheckedAt
            );
        }

        return status(
                "CONFIGURED_UNVERIFIED",
                true,
                "API key is configured, but the AI provider has not been verified since startup."
        );
    }

    public void markReady() {
        runtimeState = "READY";
        runtimeMessage = "AI engine is configured and responding.";
        lastHttpStatus = 200;
        lastCheckedAt = Instant.now();
    }

    public void markAuthError(int status, String message) {
        runtimeState = "AUTH_ERROR";
        runtimeMessage = message;
        lastHttpStatus = status;
        lastCheckedAt = Instant.now();
    }

    public void markRateLimited(int status, String message) {
        runtimeState = "RATE_LIMITED";
        runtimeMessage = message;
        lastHttpStatus = status;
        lastCheckedAt = Instant.now();
    }

    public void markError(Integer status, String message) {
        runtimeState = "ERROR";
        runtimeMessage = message;
        lastHttpStatus = status;
        lastCheckedAt = Instant.now();
    }

    private AiEngineStatus status(
            String state,
            boolean keyConfigured,
            String message
    ) {
        return new AiEngineStatus(
                state,
                properties.provider(),
                properties.model(),
                properties.enabled(),
                keyConfigured,
                message,
                lastHttpStatus,
                lastCheckedAt
        );
    }
}
