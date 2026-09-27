package com.coinmind.ai.api;

import com.coinmind.ai.status.AiEngineStatus;
import com.coinmind.ai.status.AiStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiStatusController {

    private final AiStatusService statusService;

    public AiStatusController(AiStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    public AiEngineStatus status() {
        return statusService.current();
    }
}
