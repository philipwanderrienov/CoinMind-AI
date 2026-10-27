package com.coinmind.trade.futures;

import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fail-closed status: spot-derived signals must not be represented as futures-ready. */
@RestController
@RequestMapping("/api/v1/futures")
public class FuturesReadinessController {
    @GetMapping("/readiness")
    public Readiness readiness() {
        return new Readiness(
                "BLOCKED",
                false,
                false,
                "PAPER_ONLY",
                List.of(
                        "Futures mark/index prices and contract specifications not integrated",
                        "Open interest, funding and derivatives order flow not integrated",
                        "Exchange liquidation tier model and distance buffer not integrated",
                        "Futures intraday strategy has not passed after-fee backtests and forward tests"
                ),
                Instant.now()
        );
    }

    public record Readiness(String status, boolean futuresSignalsEnabled,
                            boolean liveOrdersEnabled, String riskPreviewMode,
                            List<String> blockers, Instant checkedAt) {}
}
