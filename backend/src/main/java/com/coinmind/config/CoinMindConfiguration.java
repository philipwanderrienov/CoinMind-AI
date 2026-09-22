package com.coinmind.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MarketProperties.class)
public class CoinMindConfiguration {
}
