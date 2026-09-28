package com.coinmind.ai.api;

import com.coinmind.ai.config.AiProperties;
import com.coinmind.ai.service.AiBudgetGuardrailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/ai")
public class AiGuardrailController {

    private final AiBudgetGuardrailService budgetGuardrailService;
    private final AiProperties properties;

    public AiGuardrailController(
            AiBudgetGuardrailService budgetGuardrailService,
            AiProperties properties
    ) {
        this.budgetGuardrailService = budgetGuardrailService;
        this.properties = properties;
    }

    @GetMapping("/guardrails")
    public GuardrailResponse guardrails() {
        var status = budgetGuardrailService.status();

        return new GuardrailResponse(
                properties.minimumAutomaticSignalScore(),
                status.automaticCallsThisHour(),
                status.automaticCallsToday(),
                status.maxAutomaticCallsPerHour(),
                status.maxAutomaticCallsPerDay()
        );
    }

    public record GuardrailResponse(
            BigDecimal minimumAutomaticSignalScore,
            int automaticCallsThisHour,
            int automaticCallsToday,
            int maxAutomaticCallsPerHour,
            int maxAutomaticCallsPerDay
    ) {
    }
}
