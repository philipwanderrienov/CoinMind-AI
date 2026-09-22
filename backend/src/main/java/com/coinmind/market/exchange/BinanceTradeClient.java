package com.coinmind.market.exchange;

import com.coinmind.config.MarketProperties;
import com.coinmind.market.model.TradeSnapshot;
import com.coinmind.market.service.TradeService;
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
public class BinanceTradeClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceTradeClient.class);

    private final MarketProperties properties;
    private final TradeService tradeService;
    private final ObjectMapper objectMapper;
    private final ReactorNettyWebSocketClient webSocketClient = new ReactorNettyWebSocketClient();

    public BinanceTradeClient(
            MarketProperties properties,
            TradeService tradeService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.tradeService = tradeService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        URI uri = URI.create(buildCombinedStreamUrl());
        log.info("Connecting to Binance aggTrade streams. symbols={}", properties.symbols());

        webSocketClient.execute(uri, session ->
                        session.receive()
                                .doOnNext(message -> handlePayload(message.getPayloadAsText()))
                                .then()
                )
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(30)))
                .subscribe(
                        ignored -> { },
                        error -> log.error("Binance aggTrade WebSocket terminated unexpectedly", error)
                );
    }

    private String buildCombinedStreamUrl() {
        String streams = properties.symbols().stream()
                .map(symbol -> symbol.toLowerCase(Locale.ROOT) + "@aggTrade")
                .collect(Collectors.joining("/"));

        return properties.binanceWebsocketBaseUrl() + "/stream?streams=" + streams;
    }

    private void handlePayload(String payload) {
        try {
            JsonNode envelope = objectMapper.readTree(payload);
            JsonNode data = envelope.path("data");

            BigDecimal price = decimal(data, "p");
            BigDecimal quantity = decimal(data, "q");
            boolean buyerIsMaker = data.path("m").asBoolean();

            tradeService.publish(new TradeSnapshot(
                    data.path("s").asText(),
                    data.path("a").asLong(),
                    price,
                    quantity,
                    price.multiply(quantity),
                    buyerIsMaker,
                    buyerIsMaker ? "SELL" : "BUY",
                    Instant.ofEpochMilli(data.path("T").asLong()),
                    Instant.ofEpochMilli(data.path("E").asLong())
            ));
        } catch (Exception ex) {
            log.warn("Unable to parse Binance aggTrade payload", ex);
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.path(field).asText("0"));
    }
}
