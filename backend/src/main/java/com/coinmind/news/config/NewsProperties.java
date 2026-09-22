package com.coinmind.news.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "coinmind.news")
public record NewsProperties(
        boolean enabled,
        Duration refreshInterval,
        List<String> rssUrls
) {
}
