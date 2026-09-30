package com.coinmind.market.api;

import com.coinmind.market.model.MarketActivityProfile;
import com.coinmind.market.service.MarketActivityProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/market/activity")
public class MarketActivityProfileController {

    private final MarketActivityProfileService activityProfileService;

    public MarketActivityProfileController(
            MarketActivityProfileService activityProfileService
    ) {
        this.activityProfileService = activityProfileService;
    }

    @GetMapping("/{symbol}")
    public Mono<MarketActivityProfile> getActivityProfile(
            @PathVariable String symbol
    ) {
        return activityProfileService.build(symbol);
    }
}
