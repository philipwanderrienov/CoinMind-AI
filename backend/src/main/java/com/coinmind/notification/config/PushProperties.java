package com.coinmind.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "coinmind.push")
public record PushProperties(
        boolean enabled,
        String publicKey,
        String privateKey,
        String subject
) {
}
