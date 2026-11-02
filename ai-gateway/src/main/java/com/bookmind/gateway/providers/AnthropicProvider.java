package com.bookmind.gateway.providers;

import com.bookmind.gateway.core.GatewayRequest;
import com.bookmind.gateway.core.GatewayResponse;
import com.bookmind.gateway.core.GatewayResponseChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Anthropic (Claude) provider adapter.
 */
@Component
public class AnthropicProvider implements AiProvider {

    private final WebClient webClient;

    @Value("${ai-gateway.providers.anthropic.base-url:https://api.anthropic.com}")
    private String baseUrl;

    @Value("${ai-gateway.providers.anthropic.api-key:}")
    private String apiKey;

    @Value("${ai-gateway.providers.anthropic.enabled:true}")
    private boolean enabled;

    public AnthropicProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getProviderId() {
        return "anthropic";
    }

    @Override
    public String getDisplayName() {
        return "Anthropic (Claude)";
    }

    @Override
    public boolean isAvailable() {
        return enabled && (apiKey != null && !apiKey.isBlank());
    }

    @Override
    public List<ProviderModel> getSupportedModels() {
        return List.of(
            new ProviderModel("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", "chat", 200000, true, true, Map.of()),
            new ProviderModel("claude-3-opus-20240229", "Claude 3 Opus", "chat", 200000, true, true, Map.of()),
            new ProviderModel("claude-3-sonnet-20240229", "Claude 3 Sonnet", "chat", 200000, true, true, Map.of()),
            new ProviderModel("claude-3-haiku-20240307", "Claude 3 Haiku", "chat", 200000, true, true, Map.of())
        );
    }

    @Override
    public GatewayResponse complete(GatewayRequest request) {
        var stream = completeStream(request);
        StringBuilder content = new StringBuilder();
        String taskId = request.taskId();

        stream.doOnNext(chunk -> {
            if (chunk.content() != null && !chunk.isFinal()) {
                content.append(chunk.content());
            }
        }).blockLast();

        return GatewayResponse.success(taskId, content.toString(), Map.of("provider", "anthropic"));
    }

    @Override
    public Flux<GatewayResponseChunk> completeStream(GatewayRequest request) {
        String model = extractModel(request);

        String systemPrompt = (String) request.parameters().getOrDefault("systemPrompt",
            "You are a helpful assistant that extracts verbatim passages from books.");
        String userPrompt = buildUserPrompt(request);

        Map<String, Object> body = Map.of(
            "model", model,
            "max_tokens", 4096,
            "temperature", 0.1,
            "system", systemPrompt,
            "messages", List.of(Map.of("role", "user", "content", userPrompt)),
            "stream", true
        );

        String url = baseUrl + "/v1/messages";

        return webClient.post()
            .uri(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(String.class)
            .flatMap(this::parseAnthropicChunk)
            .onErrorResume(e -> Flux.just(GatewayResponseChunk.error(request.taskId(), e.getMessage())));
    }

    @Override
    public List<float[]> embed(List<String> texts, String model) {
        // Anthropic doesn't have a dedicated embeddings API yet
        // Fall back to a default or throw unsupported
        return texts.stream().map(t -> new float[0]).toList();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "baseUrl", Map.of("type", "string", "default", "https://api.anthropic.com"),
            "apiKey", Map.of("type", "string", "format", "password"),
            "model", Map.of("type", "string", "default", "claude-3-5-sonnet-20241022"),
            "temperature", Map.of("type", "number", "default", 0.1),
            "maxTokens", Map.of("type", "integer", "default", 4096)
        );
    }

    private String extractModel(GatewayRequest request) {
        return (String) request.parameters().getOrDefault("model", "claude-3-5-sonnet-20241022");
    }

    private String buildUserPrompt(GatewayRequest request) {
        StringBuilder sb = new StringBuilder();

        if (request.parameters().containsKey("text")) {
            sb.append("Context:\n").append(request.parameters().get("text")).append("\n\n");
        }
        if (request.parameters().containsKey("question")) {
            sb.append("Question: ").append(request.parameters().get("question")).append("\n\n");
        }
        sb.append("Extract verbatim passages with page citations.");
        return sb.toString();
    }

    private Flux<GatewayResponseChunk> parseAnthropicChunk(String line) {
        if (line == null || line.isBlank() || !line.startsWith("data: ")) {
            return Flux.empty();
        }

        String json = line.substring(6);
        if ("[DONE]".equals(json.trim())) {
            return Flux.just(GatewayResponseChunk.finalChunk(
                java.util.UUID.randomUUID().toString(), "", Map.of("provider", "anthropic")));
        }

        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> event = mapper.readValue(json, Map.class);
            String type = (String) event.get("type");

            if ("content_block_delta".equals(type)) {
                Map<String, Object> delta = (Map<String, Object>) event.get("delta");
                String text = (String) delta.get("text");
                if (text != null && !text.isEmpty()) {
                    return Flux.just(GatewayResponseChunk.content(
                        java.util.UUID.randomUUID().toString(), text));
                }
            } else if ("message_stop".equals(type)) {
                return Flux.just(GatewayResponseChunk.finalChunk(
                    java.util.UUID.randomUUID().toString(), "", Map.of("provider", "anthropic")));
            }
        } catch (Exception e) {
            // Ignore parse errors
        }

        return Flux.empty();
    }
}