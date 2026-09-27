package com.coinmind.ai.api;

import com.coinmind.ai.usage.AiUsageService;
import com.coinmind.ai.usage.AiUsageSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/ai/usage")
public class AiUsageController {

    private final AiUsageService usageService;

    public AiUsageController(AiUsageService usageService) {
        this.usageService = usageService;
    }

    @GetMapping("/summary")
    public Mono<AiUsageSummary> summary(
            @RequestParam(defaultValue = "14") int days
    ) {
        return usageService.summary(days);
    }
}
