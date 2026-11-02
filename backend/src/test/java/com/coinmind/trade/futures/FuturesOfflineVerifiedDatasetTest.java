package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FuturesOfflineVerifiedDatasetTest {
    private static String fixture(Duration frame) {
        var csv = new StringBuilder("open_time_utc,open,high,low,close\n");
        for (int i=0; i<3; i++)
            csv.append(Instant.parse("2026-10-26T00:00:00Z").plus(frame.multipliedBy(i)))
                    .append(",100,100,100,100\n");
        return csv.toString();
    }
    private static List<String> streams() {
        return List.of(fixture(Duration.ofMinutes(5)),fixture(Duration.ofMinutes(15)),
                fixture(Duration.ofHours(1)),fixture(Duration.ofHours(4)));
    }
    private static FuturesOfflineVerifiedDataset.Manifest manifest(List<String> data) {
        return new FuturesOfflineVerifiedDataset.Manifest("operator-reviewed-provider-archive",
                "USD_M_FUTURES","BTCUSDT","HISTORICAL_REPLAY",
                data.stream().map(FuturesOfflineVerifiedDataset::sha256).toList());
    }
    @Test void acceptsMatchingHistoricalManifestAndFourFrames() throws Exception {
        var data=streams();
        var result=FuturesOfflineVerifiedDataset.read(manifest(data),data,2,BigDecimal.ONE,
                BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO);
        assertEquals("HISTORICAL_REPLAY",result.provenance());
        assertEquals(3,result.h4().size());
    }
    @Test void rejectsTamperedCsvBeforeParsing() {
        var data=streams();
        var changed=new java.util.ArrayList<>(data);
        changed.set(0,data.get(0).replace("100,100,100,100","101,101,101,101"));
        assertThrows(IllegalArgumentException.class,() ->
                FuturesOfflineVerifiedDataset.read(manifest(data),changed,2,BigDecimal.ONE,
                        BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO));
    }
    @Test void rejectsSyntheticProvenanceOrAmbiguousMarket() {
        var data=streams();
        assertThrows(IllegalArgumentException.class,()-> new FuturesOfflineVerifiedDataset.Manifest(
                "source","SPOT","BTCUSDT","SYNTHETIC_FIXTURE",
                data.stream().map(FuturesOfflineVerifiedDataset::sha256).toList()));
        assertThrows(IllegalArgumentException.class,()-> new FuturesOfflineVerifiedDataset.Manifest(
                "source","UNKNOWN","BTCUSDT","HISTORICAL_REPLAY",
                data.stream().map(FuturesOfflineVerifiedDataset::sha256).toList()));
    }
}
