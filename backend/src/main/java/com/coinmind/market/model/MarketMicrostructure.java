package com.coinmind.market.model;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketMicrostructure(
        String symbol,
        BigDecimal spread,
        BigDecimal spreadBps,
        BigDecimal midPrice,
        BigDecimal buyVolume,
        BigDecimal sellVolume,
        BigDecimal buySellRatio,
        Instant updatedAt
) {
}
