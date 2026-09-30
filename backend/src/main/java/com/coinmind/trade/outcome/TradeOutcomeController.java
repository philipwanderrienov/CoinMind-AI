package com.coinmind.trade.outcome;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/trade/outcomes")
public class TradeOutcomeController {

    private final TradeOutcomeService service;

    public TradeOutcomeController(TradeOutcomeService service) {
        this.service = service;
    }

    @GetMapping("/{symbol}")
    public Flux<TradeOutcome> recent(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return service.recent(symbol, limit);
    }

    @GetMapping("/{symbol}/summary")
    public Mono<TradeOutcomeSummary> summary(
            @PathVariable String symbol
    ) {
        return service.summary(symbol);
    }

    @PostMapping("/evaluate")
    public Mono<EvaluationResponse> evaluate() {
        return service.evaluateOpen()
                .map(EvaluationResponse::new);
    }

    public record EvaluationResponse(int updated) {
    }
}
