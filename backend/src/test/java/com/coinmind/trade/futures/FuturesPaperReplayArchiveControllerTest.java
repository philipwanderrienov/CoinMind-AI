package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

class FuturesPaperReplayArchiveControllerTest {
    @Test void disabledArchiveRoutesFailClosedWithoutDatabaseAccess() {
        var controller = new FuturesPaperReplayArchiveController(null, false);
        StepVerifier.create(controller.one(UUID.randomUUID()))
                .expectError(ResponseStatusException.class).verify();
        StepVerifier.create(controller.recent(20))
                .expectError(ResponseStatusException.class).verify();
    }
    @Test void rejectsUnboundedHistoryLimitBeforeDatabaseAccess() {
        var controller = new FuturesPaperReplayArchiveController(null, true);
        StepVerifier.create(controller.recent(0))
                .expectError(ResponseStatusException.class).verify();
        StepVerifier.create(controller.recent(101))
                .expectError(ResponseStatusException.class).verify();
    }
}
