package com.coinmind.market.api;

import com.coinmind.market.model.Candlestick;
import com.coinmind.market.service.MarketHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market/history")
public class MarketHistoryController {

    private final MarketHistoryService historyService;

    public MarketHistoryController(MarketHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/{symbol}/{interval}")
    public List<Candlestick> getHistory(
            @PathVariable String symbol,
            @PathVariable String interval,
            @RequestParam(defaultValue = "500") int limit
    ) {
        return historyService.get(symbol, interval, limit);
    }
}
