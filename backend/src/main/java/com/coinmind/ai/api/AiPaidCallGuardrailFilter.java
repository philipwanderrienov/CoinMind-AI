package com.coinmind.ai.api;

import com.coinmind.ai.config.AiProperties;
import com.coinmind.ai.usage.AiUsageService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AiPaidCallGuardrailFilter implements WebFilter {

    private final AiProperties properties;
    private final AiUsageService usageService;

    public AiPaidCallGuardrailFilter(
            AiProperties properties,
            AiUsageService usageService
    ) {
        this.properties = properties;
        this.usageService = usageService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        boolean paidAiPost = HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && path.startsWith("/api/v1/ai/");

        if (!paidAiPost
                || !properties.enabled()
                || !"openai".equalsIgnoreCase(properties.provider())) {
            return chain.filter(exchange);
        }

        return usageService.allowPaidCall()
                .flatMap(allowed -> {
                    if (allowed) {
                        return chain.filter(exchange);
                    }

                    exchange.getResponse().setStatusCode(HttpStatus.PAYMENT_REQUIRED);
                    exchange.getResponse().getHeaders()
                            .setContentType(MediaType.APPLICATION_JSON);

                    String body = """
                            {"error":"AI_SPEND_GUARDRAIL","message":"Paid AI request blocked. Enable persistence and set AI_MAX_MONTHLY_SPEND_USD above current recorded monthly spend."}
                            """;

                    var buffer = exchange.getResponse()
                            .bufferFactory()
                            .wrap(body.getBytes(StandardCharsets.UTF_8));

                    return exchange.getResponse().writeWith(Mono.just(buffer));
                });
    }
}
