package com.coinmind.ai.api;

import com.coinmind.ai.evaluation.AiAnalysisEvaluationService;
import com.coinmind.ai.evaluation.AiAnalysisHistoryItem;
import com.coinmind.ai.evaluation.AiEvaluationSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/ai/history")
public class AiHistoryController {

    private final AiAnalysisEvaluationService evaluationService;

    public AiHistoryController(AiAnalysisEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/summary/{symbol}/{interval}")
    public Mono<AiEvaluationSummary> summary(
            @PathVariable String symbol,
            @PathVariable String interval
    ) {
        return evaluationService.summary(symbol, interval);
    }

    @GetMapping("/{symbol}/{interval}")
    public Flux<AiAnalysisHistoryItem> history(
            @PathVariable String symbol,
            @PathVariable String interval,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return evaluationService.history(symbol, interval, limit);
    }
}
