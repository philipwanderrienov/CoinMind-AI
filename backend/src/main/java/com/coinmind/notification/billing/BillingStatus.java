package com.coinmind.notification.billing;

import java.time.LocalDate;

public record BillingStatus(
        boolean enabled,
        String provider,
        LocalDate nextPaymentDate,
        long daysUntilPayment,
        int reminderDaysBefore,
        boolean pushConfigured
) {
}
