package com.coinmind.notification.api;

import com.coinmind.notification.config.PushProperties;
import com.coinmind.notification.push.PushSubscriptionRepository;
import com.coinmind.notification.push.WebPushService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/push")
public class PushController {

    private final PushProperties pushProperties;
    private final ObjectProvider<PushSubscriptionRepository> repositoryProvider;
    private final WebPushService webPushService;

    public PushController(
            PushProperties pushProperties,
            ObjectProvider<PushSubscriptionRepository> repositoryProvider,
            WebPushService webPushService
    ) {
        this.pushProperties = pushProperties;
        this.repositoryProvider = repositoryProvider;
        this.webPushService = webPushService;
    }

    @GetMapping("/public-key")
    public ResponseEntity<PushPublicKeyResponse> publicKey() {
        return ResponseEntity.ok(new PushPublicKeyResponse(
                pushProperties.enabled(),
                pushProperties.publicKey() == null ? "" : pushProperties.publicKey()
        ));
    }

    @PostMapping("/subscribe")
    public Mono<ResponseEntity<Void>> subscribe(
            @RequestBody PushSubscriptionRequest request
    ) {
        PushSubscriptionRepository repository = repositoryProvider.getIfAvailable();

        if (repository == null) {
            return Mono.just(ResponseEntity.serviceUnavailable().build());
        }

        if (!valid(request)) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return repository.upsert(
                        request.endpoint(),
                        request.keys().p256dh(),
                        request.keys().auth()
                )
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/test")
    public Mono<ResponseEntity<TestPushResponse>> testPush() {
        if (!webPushService.isConfigured()) {
            return Mono.just(ResponseEntity
                    .serviceUnavailable()
                    .body(new TestPushResponse(0, "Web Push is not configured")));
        }

        return webPushService.sendToAll(
                        "CoinMind AI",
                        "Push notification test successful.",
                        "coinmind-push-test",
                        "/"
                )
                .map(sent -> ResponseEntity.ok(
                        new TestPushResponse(sent, "Test notification processed")
                ));
    }

    private boolean valid(PushSubscriptionRequest request) {
        if (
                request == null
                        || request.endpoint() == null
                        || request.endpoint().isBlank()
                        || request.keys() == null
                        || request.keys().p256dh() == null
                        || request.keys().p256dh().isBlank()
                        || request.keys().auth() == null
                        || request.keys().auth().isBlank()
        ) {
            return false;
        }

        try {
            URI uri = URI.create(request.endpoint());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    public record PushPublicKeyResponse(
            boolean enabled,
            String publicKey
    ) {
    }

    public record TestPushResponse(
            int sent,
            String message
    ) {
    }

    public record PushSubscriptionRequest(
            String endpoint,
            Object expirationTime,
            Keys keys
    ) {
        public record Keys(
                String p256dh,
                String auth
        ) {
        }
    }
}
