package com.coinmind.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "coinmind.billing")
public record BillingProperties(
        boolean enabled,
        int dueDay,
        int reminderDaysBefore,
        String timezone,
        String provider
) {
}
