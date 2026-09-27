package com.coinmind.notification.push;

import com.coinmind.notification.config.PushProperties;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.security.Security;
import java.util.Map;

@Service
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);

    private final PushProperties properties;
    private final ObjectProvider<PushSubscriptionRepository> repositoryProvider;
    private final ObjectMapper objectMapper;

    public WebPushService(
            PushProperties properties,
            ObjectProvider<PushSubscriptionRepository> repositoryProvider,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.repositoryProvider = repositoryProvider;
        this.objectMapper = objectMapper;

        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public boolean isConfigured() {
        return properties.enabled()
                && properties.publicKey() != null
                && !properties.publicKey().isBlank()
                && properties.privateKey() != null
                && !properties.privateKey().isBlank();
    }

    public Mono<Integer> sendToAll(
            String title,
            String body,
            String tag,
            String url
    ) {
        PushSubscriptionRepository repository = repositoryProvider.getIfAvailable();

        if (!isConfigured() || repository == null) {
            return Mono.just(0);
        }

        return repository.findEnabled()
                .flatMap(subscription ->
                        send(subscription, title, body, tag, url)
                                .onErrorResume(error -> {
                                    log.warn(
                                            "Web push failed. endpoint={}",
                                            subscription.endpoint(),
                                            error
                                    );
                                    return Mono.just(false);
                                })
                )
                .filter(Boolean::booleanValue)
                .count()
                .map(Long::intValue);
    }

    private Mono<Boolean> send(
            PushSubscriptionRecord subscription,
            String title,
            String body,
            String tag,
            String url
    ) {
        return Mono.fromCallable(() -> {
                    PushService pushService = new PushService(
                            properties.publicKey(),
                            properties.privateKey(),
                            properties.subject()
                    );

                    String payload = objectMapper.writeValueAsString(Map.of(
                            "title", title,
                            "body", body,
                            "tag", tag,
                            "url", url
                    ));

                    Notification notification = new Notification(
                            subscription.endpoint(),
                            subscription.p256dhKey(),
                            subscription.authKey(),
                            payload
                    );

                    HttpResponse response = pushService.send(notification);
                    int status = response.getStatusLine().getStatusCode();

                    if (status == 404 || status == 410) {
                        PushSubscriptionRepository repository =
                                repositoryProvider.getIfAvailable();

                        if (repository != null) {
                            repository.disable(subscription.endpoint()).subscribe();
                        }
                    }

                    return status >= 200 && status < 300;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
