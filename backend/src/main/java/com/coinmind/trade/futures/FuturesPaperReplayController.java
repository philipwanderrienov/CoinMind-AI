package com.coinmind.trade.futures;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only process-local replay snapshots. Disabled by default. Never live-account state. */
@RestController
@RequestMapping("/api/v1/dev/futures-paper/replays")
public final class FuturesPaperReplayController {
    private final FuturesPaperReplayRegistry registry;
    private final boolean enabled;

    public FuturesPaperReplayController(FuturesPaperReplayRegistry registry,
            @Value("${coinmind.futures.paper-replay-read-enabled:false}") boolean enabled) {
        this.registry = registry;
        this.enabled = enabled;
    }

    @GetMapping("/{id}/risk")
    public FuturesPaperReplayRegistry.Snapshot risk(@PathVariable UUID id) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var snapshot = registry.find(id);
        if (snapshot == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return snapshot;
    }
}
