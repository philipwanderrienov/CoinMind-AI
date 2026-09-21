package com.coinmind.ai.service;

import com.coinmind.ai.model.MarketContext;
import com.coinmind.indicator.model.TechnicalIndicators;
import com.coinmind.indicator.service.TechnicalIndicatorService;
import com.coinmind.market.model.OrderBookSnapshot;
import com.coinmind.market.model.TickerSnapshot;
import com.coinmind.market.service.MarketHistoryService;
import com.coinmind.market.service.MarketTickerService;
import com.coinmind.market.service.OrderBookService;
import com.coinmind.market.service.TradeService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class MarketContextBuilder {

    private final MarketTickerService tickerService;
    private final MarketHistoryService historyService;
    private final OrderBookService orderBookService;
    private final TradeService tradeService;
    private final TechnicalIndicatorService indicatorService;

    public MarketContextBuilder(
            MarketTickerService tickerService,
            MarketHistoryService historyService,
            OrderBookService orderBookService,
            TradeService tradeService,
            TechnicalIndicatorService indicatorService
    ) {
        this.tickerService = tickerService;
        this.historyService = historyService;
        this.orderBookService = orderBookService;
        this.tradeService = tradeService;
        this.indicatorService = indicatorService;
    }

    public MarketContext build(String symbol, String interval) {
        String normalizedSymbol = symbol.toUpperCase();
        String normalizedInterval = interval.toLowerCase();

        TickerSnapshot ticker = tickerService.getLatest(normalizedSymbol)
                .orElseThrow(() -> new IllegalStateException("Ticker not available"));

        var candles = historyService.get(normalizedSymbol, normalizedInterval, 500);
        if (candles.size() < 30) {
            throw new IllegalStateException("Not enough candle history");
        }

        TechnicalIndicators indicators = indicatorService.calculate(
                normalizedSymbol,
                normalizedInterval,
                candles
        );

        OrderBookSnapshot orderBook = orderBookService.getLatest(normalizedSymbol)
                .orElseThrow(() -> new IllegalStateException("Order book not available"));

        var micro = tradeService.summarize(
                normalizedSymbol,
                orderBook.spread(),
                orderBook.midPrice()
        );

        return new MarketContext(
                normalizedSymbol,
                normalizedInterval,
                Instant.now(),
                new MarketContext.PriceContext(
                        ticker.closePrice(),
                        ticker.openPrice(),
                        ticker.highPrice(),
                        ticker.lowPrice(),
                        ticker.quoteVolume()
                ),
                new MarketContext.TechnicalContext(
                        indicators.ema20(),
                        indicators.ema50(),
                        indicators.ema200(),
                        indicators.rsi14(),
                        indicators.macd(),
                        indicators.macdSignal(),
                        indicators.macdHistogram(),
                        indicators.atr14(),
                        indicators.bollingerMiddle(),
                        indicators.bollingerUpper(),
                        indicators.bollingerLower(),
                        indicators.volumeRatio(),
                        indicators.trendScore(),
                        indicators.momentumScore(),
                        indicators.volatilityScore()
                ),
                new MarketContext.MicrostructureContext(
                        orderBook.bestBidPrice(),
                        orderBook.bestAskPrice(),
                        orderBook.spread(),
                        micro.spreadBps(),
                        orderBook.midPrice(),
                        micro.buyVolume(),
                        micro.sellVolume(),
                        micro.buySellRatio()
                )
        );
    }
}
