package com.coinmind.notification.push;

import java.time.Instant;

public record PushSubscriptionRecord(
        String id,
        String endpoint,
        String p256dhKey,
        String authKey,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
