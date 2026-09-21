package com.coinmind.market.api;

import com.coinmind.market.model.Candlestick;
import com.coinmind.market.persistence.CandlestickRepository;
import com.coinmind.market.service.MarketHistoryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market/history")
public class MarketHistoryController {

    private final MarketHistoryService historyService;
    private final ObjectProvider<CandlestickRepository> repository;

    public MarketHistoryController(
            MarketHistoryService historyService,
            ObjectProvider<CandlestickRepository> repository
    ) {
        this.historyService = historyService;
        this.repository = repository;
    }

    @GetMapping("/{symbol}/{interval}")
    public Mono<List<Candlestick>> getHistory(
            @PathVariable String symbol,
            @PathVariable String interval,
            @RequestParam(defaultValue = "500") int limit
    ) {
        CandlestickRepository databaseRepository = repository.getIfAvailable();

        if (databaseRepository == null) {
            return Mono.just(historyService.get(symbol, interval, limit));
        }

        return databaseRepository.findRecent(symbol, interval, limit)
                .collectList()
                .map(databaseCandles ->
                        databaseCandles.isEmpty()
                                ? historyService.get(symbol, interval, limit)
                                : databaseCandles
                );
    }
}
