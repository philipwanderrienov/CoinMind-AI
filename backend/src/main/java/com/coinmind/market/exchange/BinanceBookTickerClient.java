package com.coinmind.market.exchange;

import com.coinmind.config.MarketProperties;
import com.coinmind.market.model.OrderBookSnapshot;
import com.coinmind.market.service.OrderBookService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class BinanceBookTickerClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceBookTickerClient.class);
    private static final MathContext MC = MathContext.DECIMAL64;

    private final MarketProperties properties;
    private final OrderBookService orderBookService;
    private final ObjectMapper objectMapper;
    private final ReactorNettyWebSocketClient webSocketClient = new ReactorNettyWebSocketClient();

    public BinanceBookTickerClient(
            MarketProperties properties,
            OrderBookService orderBookService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.orderBookService = orderBookService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        URI uri = URI.create(buildCombinedStreamUrl());
        log.info("Connecting to Binance bookTicker streams. symbols={}", properties.symbols());

        webSocketClient.execute(uri, session ->
                        session.receive()
                                .doOnNext(message -> handlePayload(message.getPayloadAsText()))
                                .then()
                )
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(30)))
                .subscribe(
                        ignored -> { },
                        error -> log.error("Binance bookTicker WebSocket terminated unexpectedly", error)
                );
    }

    private String buildCombinedStreamUrl() {
        String streams = properties.symbols().stream()
                .map(symbol -> symbol.toLowerCase(Locale.ROOT) + "@bookTicker")
                .collect(Collectors.joining("/"));

        return properties.binanceWebsocketBaseUrl() + "/stream?streams=" + streams;
    }

    private void handlePayload(String payload) {
        try {
            JsonNode envelope = objectMapper.readTree(payload);
            JsonNode data = envelope.path("data");

            BigDecimal bid = decimal(data, "b");
            BigDecimal bidQty = decimal(data, "B");
            BigDecimal ask = decimal(data, "a");
            BigDecimal askQty = decimal(data, "A");
            BigDecimal spread = ask.subtract(bid);
            BigDecimal mid = ask.add(bid).divide(BigDecimal.valueOf(2), MC);

            orderBookService.publish(new OrderBookSnapshot(
                    data.path("s").asText(),
                    bid,
                    bidQty,
                    ask,
                    askQty,
                    spread,
                    mid,
                    Instant.now()
            ));
        } catch (Exception ex) {
            log.warn("Unable to parse Binance bookTicker payload", ex);
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.path(field).asText("0"));
    }
}
