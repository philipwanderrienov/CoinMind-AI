package com.coinmind.trade.futures;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.Duration;

/**
 * Explicit trusted-reader entry point; does not open filesystem paths or download data.
 * The caller is responsible for verifying provenance and controlling reader ownership.
 */
public final class FuturesOfflineCsvBundle {
    private FuturesOfflineCsvBundle() {}

    public static FuturesHistoricalWalkForwardRunner.Input read(
            String provenance, Reader m5, Reader m15, Reader h1, Reader h4,
            int horizonBars, BigDecimal stopPercent, BigDecimal targetPercent,
            BigDecimal quantity, BigDecimal feePercentPerSide,
            BigDecimal slippagePercentPerSide) throws IOException {
        if (!"SYNTHETIC_FIXTURE".equals(provenance) && !"HISTORICAL_REPLAY".equals(provenance))
            throw new IllegalArgumentException("Offline provenance required before reading input");
        return FuturesOfflineCandleImport.assemble(provenance,
                FuturesOfflineCandleImport.parse(m5, Duration.ofMinutes(5)),
                FuturesOfflineCandleImport.parse(m15, Duration.ofMinutes(15)),
                FuturesOfflineCandleImport.parse(h1, Duration.ofHours(1)),
                FuturesOfflineCandleImport.parse(h4, Duration.ofHours(4)),
                horizonBars, stopPercent, targetPercent, quantity,
                feePercentPerSide, slippagePercentPerSide);
    }
}
