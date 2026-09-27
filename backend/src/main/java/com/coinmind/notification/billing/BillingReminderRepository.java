package com.coinmind.notification.billing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Repository
@ConditionalOnProperty(
        prefix = "coinmind.persistence",
        name = "enabled",
        havingValue = "true"
)
public class BillingReminderRepository {

    private final DatabaseClient databaseClient;

    public BillingReminderRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Mono<Boolean> exists(
            String provider,
            LocalDate paymentDate,
            String reminderType
    ) {
        return databaseClient.sql("""
                SELECT COUNT(*) AS count
                FROM billing_reminders
                WHERE provider = :provider
                  AND payment_date = :paymentDate
                  AND reminder_type = :reminderType
                  AND status = 'SENT'
                """)
                .bind("provider", provider)
                .bind("paymentDate", paymentDate)
                .bind("reminderType", reminderType)
                .map((row, metadata) -> {
                    Long count = row.get("count", Long.class);
                    return count != null && count > 0;
                })
                .one()
                .defaultIfEmpty(false);
    }

    public Mono<Void> record(
            String provider,
            LocalDate paymentDate,
            LocalDate reminderDate,
            String reminderType,
            String status
    ) {
        return databaseClient.sql("""
                INSERT INTO billing_reminders (
                    id, provider, payment_date, reminder_date,
                    reminder_type, sent_at, status
                ) VALUES (
                    :id, :provider, :paymentDate, :reminderDate,
                    :reminderType, :sentAt, :status
                )
                ON CONFLICT (provider, payment_date, reminder_type)
                DO UPDATE SET
                    reminder_date = EXCLUDED.reminder_date,
                    sent_at = EXCLUDED.sent_at,
                    status = EXCLUDED.status
                """)
                .bind("id", UUID.randomUUID().toString())
                .bind("provider", provider)
                .bind("paymentDate", paymentDate)
                .bind("reminderDate", reminderDate)
                .bind("reminderType", reminderType)
                .bind("sentAt", Instant.now())
                .bind("status", status)
                .then();
    }
}
