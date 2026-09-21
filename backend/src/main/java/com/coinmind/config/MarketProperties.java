package com.coinmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "coinmind.market")
public record MarketProperties(
        String binanceWebsocketBaseUrl,
        List<String> symbols
) {
}
