package com.coinmind.ai.provider;

import com.coinmind.ai.config.AiProperties;
import com.coinmind.ai.model.AiAnalysisResult;
import com.coinmind.ai.model.MarketContext;
import com.coinmind.ai.usage.AiUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
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
        name = "enabled",
        havingValue = "true"
)
public class OpenAiLunaProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLunaProvider.class);

    private final AiProperties properties;
    private final AiUsageService usageService;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenAiLunaProvider(
            AiProperties properties,
            AiUsageService usageService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.usageService = usageService;
        this.objectMapper = objectMapper;
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
        return Mono.fromCallable(() -> buildRequest(context))
                .flatMap(request ->
                        webClient.post()
                                .uri("/v1/responses")
                                .bodyValue(request)
                                .retrieve()
                                .bodyToMono(String.class)
                )
                .map(body -> parseResponse(body, context, triggerType))
                .doOnError(error -> log.warn(
                        "Luna analysis failed. symbol={}, interval={}, trigger={}",
                        context.symbol(),
                        context.interval(),
                        triggerType,
                        error
                ));
    }

    private Map<String, Object> buildRequest(MarketContext context) throws Exception {
        String compactContext = objectMapper.writeValueAsString(context);

        String prompt = """
                Analyze this cryptocurrency market context for market intelligence.
                Do not give financial advice and do not invent missing data.
                Return ONLY valid compact JSON with this exact shape:
                {
                  "marketBias":"BULLISH|BEARISH|NEUTRAL",
                  "confidence":0,
                  "summary":"maximum 2 concise sentences",
                  "supportingFactors":["maximum 4 short factors"],
                  "riskFactors":["maximum 4 short risks"]
                }
                confidence must be an integer from 0 to 100.

                MarketContext:
                """ + compactContext;

        return Map.of(
                "model", properties.model(),
                "input", prompt,
                "reasoning", Map.of(
                        "effort", properties.reasoningEffort()
                ),
                "max_output_tokens", properties.maxOutputTokens()
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
            value = value.replaceFirst("^\`\`\`(?:json)?\\s*", "");
            value = value.replaceFirst("\\s*\`\`\`$", "");
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
