package com.coinmind.trade.api;

import com.coinmind.trade.model.TradeSetup;
import com.coinmind.trade.service.TradeSetupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/trade")
public class TradeSetupController {

    private final TradeSetupService tradeSetupService;

    public TradeSetupController(TradeSetupService tradeSetupService) {
        this.tradeSetupService = tradeSetupService;
    }

    @GetMapping("/setup/{symbol}/{interval}")
    public Mono<TradeSetup> setup(
            @PathVariable String symbol,
            @PathVariable String interval
    ) {
        return tradeSetupService.build(symbol, interval);
    }
}
