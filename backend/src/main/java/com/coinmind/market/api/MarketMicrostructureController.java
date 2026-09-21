package com.coinmind.market.api;

import com.coinmind.market.model.MarketMicrostructure;
import com.coinmind.market.model.OrderBookSnapshot;
import com.coinmind.market.model.TradeSnapshot;
import com.coinmind.market.service.OrderBookService;
import com.coinmind.market.service.TradeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market/microstructure")
public class MarketMicrostructureController {

    private final OrderBookService orderBookService;
    private final TradeService tradeService;

    public MarketMicrostructureController(
            OrderBookService orderBookService,
            TradeService tradeService
    ) {
        this.orderBookService = orderBookService;
        this.tradeService = tradeService;
    }

    @GetMapping("/order-books")
    public List<OrderBookSnapshot> getOrderBooks() {
        return orderBookService.getAllLatest();
    }

    @GetMapping("/order-books/{symbol}")
    public ResponseEntity<OrderBookSnapshot> getOrderBook(@PathVariable String symbol) {
        return orderBookService.getLatest(symbol)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/trades/{symbol}/latest")
    public ResponseEntity<TradeSnapshot> getLatestTrade(@PathVariable String symbol) {
        return tradeService.getLatest(symbol)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{symbol}/summary")
    public ResponseEntity<MarketMicrostructure> getSummary(@PathVariable String symbol) {
        return orderBookService.getLatest(symbol)
                .map(orderBook -> ResponseEntity.ok(
                        tradeService.summarize(
                                symbol,
                                orderBook.spread(),
                                orderBook.midPrice()
                        )
                ))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/order-books/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<OrderBookSnapshot> streamOrderBooks() {
        return orderBookService.stream();
    }

    @GetMapping(value = "/trades/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<TradeSnapshot> streamTrades() {
        return tradeService.stream();
    }
}
