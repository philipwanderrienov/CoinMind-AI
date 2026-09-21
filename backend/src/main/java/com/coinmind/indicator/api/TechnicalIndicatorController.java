package com.coinmind.indicator.api;

import com.coinmind.indicator.model.TechnicalIndicators;
import com.coinmind.indicator.service.TechnicalIndicatorService;
import com.coinmind.market.persistence.CandlestickRepository;
import com.coinmind.market.service.MarketHistoryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/indicators")
public class TechnicalIndicatorController {

    private final TechnicalIndicatorService indicatorService;
    private final MarketHistoryService historyService;
    private final ObjectProvider<CandlestickRepository> repository;

    public TechnicalIndicatorController(
            TechnicalIndicatorService indicatorService,
            MarketHistoryService historyService,
            ObjectProvider<CandlestickRepository> repository
    ) {
        this.indicatorService = indicatorService;
        this.historyService = historyService;
        this.repository = repository;
    }

    @GetMapping("/{symbol}/{interval}")
    public Mono<ResponseEntity<TechnicalIndicators>> getIndicators(
            @PathVariable String symbol,
            @PathVariable String interval
    ) {
        CandlestickRepository databaseRepository = repository.getIfAvailable();

        Mono<List<com.coinmind.market.model.Candlestick>> candlesMono =
                databaseRepository == null
                        ? Mono.just(historyService.get(symbol, interval, 500))
                        : databaseRepository.findRecent(symbol, interval, 500)
                                .collectList()
                                .map(dbCandles -> dbCandles.isEmpty()
                                        ? historyService.get(symbol, interval, 500)
                                        : dbCandles
                                );

        return candlesMono.map(candles -> {
            if (candles.size() < 30) {
                return ResponseEntity.noContent().build();
            }

            return ResponseEntity.ok(
                    indicatorService.calculate(symbol, interval, candles)
            );
        });
    }
}
