package com.coinmind.market.exchange;

import com.coinmind.config.MarketProperties;
import com.coinmind.market.model.Candlestick;
import com.coinmind.market.persistence.CandlestickPersistenceService;
import com.coinmind.market.service.MarketCandlestickService;
import com.coinmind.market.service.MarketHistoryService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
public class BinanceKlineClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceKlineClient.class);

    private final MarketProperties properties;
    private final MarketCandlestickService candlestickService;
    private final MarketHistoryService historyService;
    private final ObjectProvider<CandlestickPersistenceService> persistenceService;
    private final ObjectMapper objectMapper;
    private final ReactorNettyWebSocketClient webSocketClient = new ReactorNettyWebSocketClient();

    public BinanceKlineClient(
            MarketProperties properties,
            MarketCandlestickService candlestickService,
            MarketHistoryService historyService,
            ObjectProvider<CandlestickPersistenceService> persistenceService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.candlestickService = candlestickService;
        this.historyService = historyService;
        this.persistenceService = persistenceService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        URI uri = URI.create(buildCombinedStreamUrl());
        log.info("Connecting to Binance kline streams. symbols={}, intervals={}",
                properties.symbols(), properties.intervals());

        webSocketClient.execute(uri, session ->
                        session.receive()
                                .doOnNext(message -> handlePayload(message.getPayloadAsText()))
                                .then()
                )
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(signal ->
                                log.warn("Binance kline WebSocket disconnected. Reconnecting. attempt={}, cause={}", signal.totalRetries() + 1, signal.failure().toString())))
                .subscribe(
                        ignored -> { },
                        error -> log.error("Binance kline WebSocket terminated unexpectedly", error)
                );
    }

    private String buildCombinedStreamUrl() {
        String streams = properties.symbols().stream()
                .flatMap(symbol -> properties.intervals().stream()
                        .map(interval -> symbol.toLowerCase(Locale.ROOT)
                                + "@kline_" + interval.toLowerCase(Locale.ROOT)))
                .collect(Collectors.joining("/"));

        return properties.binanceWebsocketBaseUrl() + "/stream?streams=" + streams;
    }

    private void handlePayload(String payload) {
        try {
            JsonNode envelope = objectMapper.readTree(payload);
            JsonNode data = envelope.path("data");
            JsonNode kline = data.path("k");

            if (data.isMissingNode() || kline.isMissingNode()) {
                return;
            }

            Candlestick candlestick = new Candlestick(
                    data.path("s").asText(),
                    kline.path("i").asText(),
                    Instant.ofEpochMilli(kline.path("t").asLong()),
                    Instant.ofEpochMilli(kline.path("T").asLong()),
                    decimal(kline, "o"),
                    decimal(kline, "h"),
                    decimal(kline, "l"),
                    decimal(kline, "c"),
                    decimal(kline, "v"),
                    decimal(kline, "q"),
                    kline.path("n").asLong(),
                    kline.path("x").asBoolean(),
                    Instant.ofEpochMilli(data.path("E").asLong())
            );

            candlestickService.publish(candlestick);
            historyService.upsert(candlestick);

            persistenceService.ifAvailable(service ->
                    service.persistIfClosed(candlestick).subscribe()
            );
        } catch (Exception ex) {
            log.warn("Unable to parse Binance kline payload", ex);
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.path(field).asText("0"));
    }
}
