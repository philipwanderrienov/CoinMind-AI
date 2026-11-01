package com.coinmind.trade.futures;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Development-only read access. Do not expose on the internet without authorization. */
@RestController
@RequestMapping("/api/v1/dev/futures-paper/archive")
public final class FuturesPaperReplayArchiveController {
    private final FuturesPaperReplayArchive archive;
    private final boolean enabled;

    public FuturesPaperReplayArchiveController(FuturesPaperReplayArchive archive,
            @Value("${coinmind.futures.paper-replay-archive-read-enabled:false}") boolean enabled) {
        this.archive = archive;
        this.enabled = enabled;
    }

    @GetMapping("/{id}")
    public Mono<FuturesPaperReplayArchive.Archived> one(@PathVariable UUID id) {
        if (!enabled) return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        return archive.find(id).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @GetMapping
    public Flux<FuturesPaperReplayArchive.Archived> recent(@RequestParam(defaultValue = "20") int limit) {
        if (!enabled) return Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (limit < 1 || limit > 100)
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be 1..100"));
        return archive.recent(limit);
    }
}
