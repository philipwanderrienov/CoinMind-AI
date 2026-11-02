package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

class FuturesPaperReplayArchiveControllerTest {
    @Test void disabledArchiveRoutesFailClosedWithoutDatabaseAccess() {
        var controller = new FuturesPaperReplayArchiveController(null, null, false, "");
        StepVerifier.create(controller.one(UUID.randomUUID(), null))
                .expectError(ResponseStatusException.class).verify();
        StepVerifier.create(controller.recent(20, null))
                .expectError(ResponseStatusException.class).verify();
    }
    @Test void deniesMissingOrInvalidReadKey() {
        var controller = new FuturesPaperReplayArchiveController(null, null, true,
                "12345678901234567890123456789012");
        StepVerifier.create(controller.recent(20, null))
                .expectError(ResponseStatusException.class).verify();
        StepVerifier.create(controller.one(UUID.randomUUID(), "incorrect"))
                .expectError(ResponseStatusException.class).verify();
        StepVerifier.create(controller.equity(UUID.randomUUID(), "incorrect"))
                .expectError(ResponseStatusException.class).verify();
    }

    @Test void rejectsUnboundedHistoryLimitBeforeDatabaseAccess() {
        var controller = new FuturesPaperReplayArchiveController(null, null, true, "12345678901234567890123456789012");
        StepVerifier.create(controller.recent(0, "bad-key"))
                .expectError(ResponseStatusException.class).verify();
        StepVerifier.create(controller.recent(101, "bad-key"))
                .expectError(ResponseStatusException.class).verify();
    }
}
