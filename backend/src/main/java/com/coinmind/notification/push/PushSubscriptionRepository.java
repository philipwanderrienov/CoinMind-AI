package com.coinmind.notification.push;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@ConditionalOnProperty(
        prefix = "coinmind.persistence",
        name = "enabled",
        havingValue = "true"
)
public class PushSubscriptionRepository {

    private final DatabaseClient databaseClient;

    public PushSubscriptionRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Mono<Void> upsert(String endpoint, String p256dhKey, String authKey) {
        Instant now = Instant.now();

        return databaseClient.sql("""
                INSERT INTO push_subscriptions (
                    id, endpoint, p256dh_key, auth_key, enabled, created_at, updated_at
                ) VALUES (
                    :id, :endpoint, :p256dhKey, :authKey, TRUE, :now, :now
                )
                ON CONFLICT (endpoint)
                DO UPDATE SET
                    p256dh_key = EXCLUDED.p256dh_key,
                    auth_key = EXCLUDED.auth_key,
                    enabled = TRUE,
                    updated_at = EXCLUDED.updated_at
                """)
                .bind("id", UUID.randomUUID().toString())
                .bind("endpoint", endpoint)
                .bind("p256dhKey", p256dhKey)
                .bind("authKey", authKey)
                .bind("now", now)
                .then();
    }

    public Flux<PushSubscriptionRecord> findEnabled() {
        return databaseClient.sql("""
                SELECT id, endpoint, p256dh_key, auth_key, enabled, created_at, updated_at
                FROM push_subscriptions
                WHERE enabled = TRUE
                ORDER BY created_at ASC
                """)
                .map((row, metadata) -> new PushSubscriptionRecord(
                        row.get("id", String.class),
                        row.get("endpoint", String.class),
                        row.get("p256dh_key", String.class),
                        row.get("auth_key", String.class),
                        Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
                        row.get("created_at", Instant.class),
                        row.get("updated_at", Instant.class)
                ))
                .all();
    }

    public Mono<Void> disable(String endpoint) {
        return databaseClient.sql("""
                UPDATE push_subscriptions
                SET enabled = FALSE, updated_at = NOW()
                WHERE endpoint = :endpoint
                """)
                .bind("endpoint", endpoint)
                .then();
    }
}
