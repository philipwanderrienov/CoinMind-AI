package com.coinmind.ai.service;

import com.coinmind.ai.config.AiProperties;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class AiBudgetGuardrailService {

    private final AiProperties properties;

    private LocalDate day;
    private int hour = -1;
    private int hourlyCalls;
    private int dailyCalls;

    public AiBudgetGuardrailService(AiProperties properties) {
        this.properties = properties;
    }

    public synchronized boolean allowAutomaticCall() {
        ZoneId zone = ZoneId.of(properties.usageTimezone());
        ZonedDateTime now = ZonedDateTime.now(zone);

        if (day == null || !day.equals(now.toLocalDate())) {
            day = now.toLocalDate();
            dailyCalls = 0;
            hourlyCalls = 0;
            hour = now.getHour();
        } else if (hour != now.getHour()) {
            hour = now.getHour();
            hourlyCalls = 0;
        }

        if (properties.maxAutomaticCallsPerHour() > 0
                && hourlyCalls >= properties.maxAutomaticCallsPerHour()) {
            return false;
        }

        if (properties.maxAutomaticCallsPerDay() > 0
                && dailyCalls >= properties.maxAutomaticCallsPerDay()) {
            return false;
        }

        hourlyCalls++;
        dailyCalls++;
        return true;
    }

    public synchronized GuardrailStatus status() {
        return new GuardrailStatus(
                hourlyCalls,
                dailyCalls,
                properties.maxAutomaticCallsPerHour(),
                properties.maxAutomaticCallsPerDay()
        );
    }

    public record GuardrailStatus(
            int automaticCallsThisHour,
            int automaticCallsToday,
            int maxAutomaticCallsPerHour,
            int maxAutomaticCallsPerDay
    ) {
    }
}
