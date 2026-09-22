package com.coinmind.market.exchange;

import com.coinmind.config.MarketProperties;
import com.coinmind.market.model.TickerSnapshot;
import com.coinmind.market.service.MarketTickerService;
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
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class BinanceTickerClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceTickerClient.class);

    private final MarketProperties properties;
    private final MarketTickerService tickerService;
    private final ObjectMapper objectMapper;
    private final ReactorNettyWebSocketClient webSocketClient = new ReactorNettyWebSocketClient();

    public BinanceTickerClient(
            MarketProperties properties,
            MarketTickerService tickerService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.tickerService = tickerService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        URI uri = URI.create(buildCombinedStreamUrl());
        log.info("Connecting to Binance market stream: {}", uri);

        webSocketClient.execute(uri, session ->
                        session.receive()
                                .doOnNext(message -> handlePayload(message.getPayloadAsText()))
                                .then()
                )
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(signal ->
                                log.warn("Binance WebSocket disconnected. Reconnecting. attempt={}, cause={}", signal.totalRetries() + 1, signal.failure().toString())))
                .subscribe(
                        ignored -> { },
                        error -> log.error("Binance WebSocket terminated unexpectedly", error)
                );
    }

    private String buildCombinedStreamUrl() {
        String streams = properties.symbols().stream()
                .map(symbol -> symbol.toLowerCase(Locale.ROOT) + "@miniTicker")
                .collect(Collectors.joining("/"));

        return properties.binanceWebsocketBaseUrl() + "/stream?streams=" + streams;
    }

    private void handlePayload(String payload) {
        try {
            JsonNode envelope = objectMapper.readTree(payload);
            JsonNode data = envelope.path("data");

            if (data.isMissingNode() || data.isNull()) {
                return;
            }

            TickerSnapshot snapshot = new TickerSnapshot(
                    data.path("s").asText(),
                    decimal(data, "c"),
                    decimal(data, "o"),
                    decimal(data, "h"),
                    decimal(data, "l"),
                    decimal(data, "v"),
                    decimal(data, "q"),
                    Instant.ofEpochMilli(data.path("E").asLong())
            );

            tickerService.publish(snapshot);
        } catch (Exception ex) {
            log.warn("Unable to parse Binance ticker payload", ex);
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.path(field).asText("0"));
    }
}
