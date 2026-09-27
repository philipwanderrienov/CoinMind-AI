package com.coinmind.notification.api;

import com.coinmind.notification.config.PushProperties;
import com.coinmind.notification.push.PushSubscriptionRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/push")
public class PushController {

    private final PushProperties pushProperties;
    private final ObjectProvider<PushSubscriptionRepository> repositoryProvider;

    public PushController(
            PushProperties pushProperties,
            ObjectProvider<PushSubscriptionRepository> repositoryProvider
    ) {
        this.pushProperties = pushProperties;
        this.repositoryProvider = repositoryProvider;
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

        return repository.upsert(
                        request.endpoint(),
                        request.keys().p256dh(),
                        request.keys().auth()
                )
                .thenReturn(ResponseEntity.noContent().build());
    }

    public record PushPublicKeyResponse(
            boolean enabled,
            String publicKey
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
