package com.coinmind.ai.provider;

import com.coinmind.ai.config.AiProperties;
import com.coinmind.ai.model.AiAnalysisResult;
import com.coinmind.ai.model.AiDecisionReview;
import com.coinmind.ai.model.MarketContext;
import com.coinmind.ai.usage.AiUsageService;
import com.coinmind.trade.model.TradeSetup;
import com.coinmind.ai.status.AiStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Primary
@ConditionalOnProperty(
        prefix = "coinmind.ai",
        name = "provider",
        havingValue = "openai"
)
public class OpenAiLunaProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLunaProvider.class);

    private final AiProperties properties;
    private final AiUsageService usageService;
    private final ObjectMapper objectMapper;
    private final AiStatusService statusService;
    private final WebClient webClient;

    public OpenAiLunaProvider(
            AiProperties properties,
            AiUsageService usageService,
            ObjectMapper objectMapper,
            AiStatusService statusService
    ) {
        this.properties = properties;
        this.usageService = usageService;
        this.objectMapper = objectMapper;
        this.statusService = statusService;
        this.webClient = WebClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String name() {
        return properties.model();
    }

    @Override
    public Mono<AiAnalysisResult> analyze(
            MarketContext context,
            String triggerType
    ) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return Mono.just(new AiAnalysisResult(
                    context.symbol(),
                    context.interval(),
                    "UNAVAILABLE",
                    0,
                    "AI engine is not configured yet.",
                    List.of(),
                    List.of("OpenAI API key is missing"),
                    properties.model(),
                    Instant.now()
            ));
        }

        return Mono.fromCallable(() -> buildRequest(context))
                .flatMap(request ->
                        webClient.post()
                                .uri("/v1/responses")
                                .bodyValue(request)
                                .retrieve()
                                .bodyToMono(String.class)
                )
                .map(body -> parseResponse(body, context, triggerType))
                .doOnNext(ignored -> statusService.markReady())
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException responseError) {
                        int status = responseError.getStatusCode().value();

                        if (status == 401 || status == 403) {
                            statusService.markAuthError(
                                    status,
                                    "OpenAI authentication failed. Check the API key."
                            );
                        } else if (status == 429) {
                            statusService.markRateLimited(
                                    status,
                                    "OpenAI rate limit or billing limit reached."
                            );
                        } else {
                            statusService.markError(
                                    status,
                                    "OpenAI request failed with HTTP " + status + "."
                            );
                        }
                    } else {
                        statusService.markError(
                                null,
                                "AI provider request failed."
                        );
                    }

                    log.warn(
                            "Luna analysis failed. symbol={}, interval={}, trigger={}",
                            context.symbol(),
                            context.interval(),
                            triggerType,
                            error
                    );
                });
    }

    @Override
    public Mono<AiDecisionReview> reviewDecision(
            TradeSetup setup,
            MarketContext context
    ) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return Mono.just(new AiDecisionReview(
                    setup.symbol(),
                    setup.action(),
                    "UNAVAILABLE",
                    0,
                    "AI engine is not configured yet.",
                    List.of(),
                    List.of("OpenAI API key is missing"),
                    properties.model(),
                    Instant.now()
            ));
        }

        return Mono.fromCallable(() -> buildDecisionReviewRequest(setup, context))
                .flatMap(request ->
                        webClient.post()
                                .uri("/v1/responses")
                                .bodyValue(request)
                                .retrieve()
                                .bodyToMono(String.class)
                )
                .map(body -> parseDecisionReview(body, setup))
                .doOnNext(ignored -> statusService.markReady());
    }

    private Map<String, Object> buildDecisionReviewRequest(
            TradeSetup setup,
            MarketContext context
    ) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "engineDecision", setup,
                "marketContext1h", context
        ));

        String prompt = """
                Review CoinMind's deterministic crypto trade decision.
                Do not invent missing data and do not override the engine with unsupported claims.
                Focus on whether the engine decision is confirmed, should remain watch-only, or should wait.
                Treat the supplied entry/target/stop levels as engine-generated reference levels.
                Keep the review concise and explain conflicts, especially timeframe alignment, market regime, and news sentiment.

                DecisionPayload:
                """ + payload;

        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "verdict", Map.of(
                                "type", "string",
                                "enum", List.of("CONFIRM", "WATCH", "WAIT")
                        ),
                        "confidence", Map.of(
                                "type", "integer",
                                "minimum", 0,
                                "maximum", 100
                        ),
                        "summary", Map.of("type", "string"),
                        "confirmations", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "maxItems", 4
                        ),
                        "concerns", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "maxItems", 4
                        )
                ),
                "required", List.of(
                        "verdict",
                        "confidence",
                        "summary",
                        "confirmations",
                        "concerns"
                ),
                "additionalProperties", false
        );

        return Map.of(
                "model", properties.model(),
                "input", prompt,
                "reasoning", Map.of("effort", properties.reasoningEffort()),
                "max_output_tokens", properties.maxOutputTokens(),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "coinmind_decision_review",
                                "strict", true,
                                "schema", schema
                        )
                )
        );
    }

    private AiDecisionReview parseDecisionReview(
            String body,
            TradeSetup setup
    ) {
        try {
            JsonNode response = objectMapper.readTree(body);
            JsonNode usage = response.path("usage");

            long inputTokens = usage.path("input_tokens").asLong(0);
            long cachedInputTokens = usage.path("input_tokens_details")
                    .path("cached_tokens")
                    .asLong(0);
            long outputTokens = usage.path("output_tokens").asLong(0);
            long reasoningTokens = usage.path("output_tokens_details")
                    .path("reasoning_tokens")
                    .asLong(0);
            long totalTokens = usage.path("total_tokens").asLong(
                    inputTokens + outputTokens
            );

            usageService.record(
                    response.path("id").asText(""),
                    setup.symbol(),
                    "1h",
                    "DECISION_REVIEW",
                    properties.model(),
                    inputTokens,
                    cachedInputTokens,
                    outputTokens,
                    reasoningTokens,
                    totalTokens
            );

            String outputText = extractOutputText(response);
            JsonNode review = objectMapper.readTree(cleanJson(outputText));

            return new AiDecisionReview(
                    setup.symbol(),
                    setup.action(),
                    review.path("verdict").asText("WAIT"),
                    Math.max(0, Math.min(100, review.path("confidence").asInt(0))),
                    review.path("summary").asText(""),
                    stringList(review.path("confirmations")),
                    stringList(review.path("concerns")),
                    properties.model(),
                    Instant.now()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse AI decision review", ex);
        }
    }

    private Map<String, Object> buildRequest(MarketContext context) throws Exception {
        String compactContext = objectMapper.writeValueAsString(context);

        String prompt = """
                Analyze this cryptocurrency market context for market intelligence.
                Do not give financial advice. Do not invent missing data.
                Keep the summary concise and base the conclusion only on the supplied context.

                MarketContext:
                """ + compactContext;

        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "marketBias", Map.of(
                                "type", "string",
                                "enum", List.of("BULLISH", "BEARISH", "NEUTRAL")
                        ),
                        "confidence", Map.of(
                                "type", "integer",
                                "minimum", 0,
                                "maximum", 100
                        ),
                        "summary", Map.of("type", "string"),
                        "supportingFactors", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "maxItems", 4
                        ),
                        "riskFactors", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "maxItems", 4
                        )
                ),
                "required", List.of(
                        "marketBias",
                        "confidence",
                        "summary",
                        "supportingFactors",
                        "riskFactors"
                ),
                "additionalProperties", false
        );

        return Map.of(
                "model", properties.model(),
                "input", prompt,
                "reasoning", Map.of(
                        "effort", properties.reasoningEffort()
                ),
                "max_output_tokens", properties.maxOutputTokens(),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "coinmind_market_analysis",
                                "strict", true,
                                "schema", schema
                        )
                )
        );
    }

    private AiAnalysisResult parseResponse(
            String body,
            MarketContext context,
            String triggerType
    ) {
        try {
            JsonNode response = objectMapper.readTree(body);
            JsonNode usage = response.path("usage");

            long inputTokens = usage.path("input_tokens").asLong(0);
            long cachedInputTokens = usage.path("input_tokens_details")
                    .path("cached_tokens")
                    .asLong(0);
            long outputTokens = usage.path("output_tokens").asLong(0);
            long reasoningTokens = usage.path("output_tokens_details")
                    .path("reasoning_tokens")
                    .asLong(0);
            long totalTokens = usage.path("total_tokens").asLong(
                    inputTokens + outputTokens
            );

            usageService.record(
                    response.path("id").asText(""),
                    context.symbol(),
                    context.interval(),
                    triggerType,
                    properties.model(),
                    inputTokens,
                    cachedInputTokens,
                    outputTokens,
                    reasoningTokens,
                    totalTokens
            );

            String outputText = extractOutputText(response);
            JsonNode analysis = objectMapper.readTree(cleanJson(outputText));

            return new AiAnalysisResult(
                    context.symbol(),
                    context.interval(),
                    analysis.path("marketBias").asText("NEUTRAL"),
                    Math.max(0, Math.min(100, analysis.path("confidence").asInt(0))),
                    analysis.path("summary").asText(""),
                    stringList(analysis.path("supportingFactors")),
                    stringList(analysis.path("riskFactors")),
                    properties.model(),
                    Instant.now()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse Luna response", ex);
        }
    }

    private String extractOutputText(JsonNode response) {
        JsonNode output = response.path("output");

        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }

                for (JsonNode part : content) {
                    if ("output_text".equals(part.path("type").asText())) {
                        return part.path("text").asText();
                    }
                }
            }
        }

        throw new IllegalStateException("Luna response does not contain output_text");
    }

    private String cleanJson(String text) {
        String value = text == null ? "" : text.trim();

        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }

        return value;
    }

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();

        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }

        return List.copyOf(values);
    }
}
