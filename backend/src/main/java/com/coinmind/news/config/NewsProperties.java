package com.coinmind.news.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "coinmind.news")
public record NewsProperties(
        boolean enabled,
        long refreshIntervalMs,
        List<String> rssUrls,
        boolean retentionEnabled,
        int hotRetentionDays,
        int archiveRetentionDays
) {
}
