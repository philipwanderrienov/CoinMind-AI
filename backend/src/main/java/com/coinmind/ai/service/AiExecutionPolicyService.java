package com.coinmind.ai.service;

import com.coinmind.ai.config.AiProperties;
import com.coinmind.ai.model.MarketContext;
import org.springframework.stereotype.Service;

@Service
public class AiExecutionPolicyService {

    private final AiProperties properties;
    private final AiBudgetGuardrailService budgetGuardrailService;

    public AiExecutionPolicyService(
            AiProperties properties,
            AiBudgetGuardrailService budgetGuardrailService
    ) {
        this.properties = properties;
        this.budgetGuardrailService = budgetGuardrailService;
    }

    public boolean providerReadyForAutomaticCalls() {
        if (!properties.enabled()) {
            return false;
        }

        if ("mock".equalsIgnoreCase(properties.provider())) {
            return true;
        }

        return "openai".equalsIgnoreCase(properties.provider())
                && properties.apiKey() != null
                && !properties.apiKey().isBlank();
    }

    public boolean allowSignalTriggered(MarketContext context) {
        return providerReadyForAutomaticCalls()
                && context.signal().triggerEligible()
                && budgetGuardrailService.allowAutomaticCall();
    }

    public boolean allowHighRelevanceNews() {
        return providerReadyForAutomaticCalls()
                && budgetGuardrailService.allowAutomaticCall();
    }
}
