package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Strict offline parser; checksum and manifest provenance are verified by caller. */
public final class FuturesOfflineVolumeCsv {
    private static final String HEADER = "open_time_utc,base_volume,quote_volume,trades,"
            + "taker_buy_base_volume,taker_buy_quote_volume";
    private FuturesOfflineVolumeCsv() {}

    public static List<FuturesOfflineVolumeConfirmation.Bar> parse(String csv,
            List<FuturesReplayEngine.Candle> prices) {
        if (csv == null || csv.length() > 24_000_000 || prices == null)
            throw new IllegalArgumentException("Bounded volume CSV required");
        String[] lines = csv.split("\\R", -1);
        if (lines.length < 3 || !HEADER.equals(lines[0]))
            throw new IllegalArgumentException("Unexpected historical volume schema");
        var bars = new ArrayList<FuturesOfflineVolumeConfirmation.Bar>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty() && i == lines.length - 1) continue;
            String[] cells = lines[i].split(",", -1);
            if (cells.length != 6) throw new IllegalArgumentException("Invalid volume CSV row");
            try {
                if (!cells[3].matches("[0-9]+")) throw new IllegalArgumentException("Invalid trades");
                long trades = Long.parseLong(cells[3]);
                Instant open = Instant.parse(cells[0]);
                bars.add(new FuturesOfflineVolumeConfirmation.Bar(open,
                        new BigDecimal(cells[1]), new BigDecimal(cells[2]), trades,
                        new BigDecimal(cells[4]), new BigDecimal(cells[5])));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Malformed historical volume row " + i, ex);
            }
        }
        // Validator performs strict timestamp parity, continuity, and volume bounds.
        new FuturesOfflineVolumeConfirmation(bars, prices);
        return List.copyOf(bars);
    }
}
