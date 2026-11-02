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
 * OpenAI provider adapter.
 * Supports OpenAI API and compatible endpoints (vLLM, LM Studio, LocalAI, TGI, etc.).
 */
@Component
public class OpenAiProvider implements AiProvider {

    private final WebClient webClient;

    @Value("${ai-gateway.providers.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${ai-gateway.providers.openai.api-key:}")
    private String apiKey;

    @Value("${ai-gateway.providers.openai.enabled:true}")
    private boolean enabled;

    public OpenAiProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getProviderId() {
        return "openai";
    }

    @Override
    public String getDisplayName() {
        return "OpenAI / Compatible";
    }

    @Override
    public boolean isAvailable() {
        return enabled && (apiKey != null && !apiKey.isBlank());
    }

    @Override
    public List<ProviderModel> getSupportedModels() {
        return List.of(
            new ProviderModel("gpt-4o", "GPT-4o", "chat", 128000, true, true, Map.of()),
            new ProviderModel("gpt-4o-mini", "GPT-4o Mini", "chat", 128000, true, true, Map.of()),
            new ProviderModel("gpt-4-turbo", "GPT-4 Turbo", "chat", 128000, true, true, Map.of()),
            new ProviderModel("gpt-3.5-turbo", "GPT-3.5 Turbo", "chat", 16384, true, true, Map.of()),
            new ProviderModel("text-embedding-3-small", "Embedding 3 Small", "embedding", 8192, false, false, Map.of("dimensions", 1536)),
            new ProviderModel("text-embedding-3-large", "Embedding 3 Large", "embedding", 8192, false, false, Map.of("dimensions", 3072)),
            new ProviderModel("text-embedding-ada-002", "Embedding Ada 002", "embedding", 8192, false, false, Map.of("dimensions", 1536))
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

        return GatewayResponse.success(taskId, content.toString(), Map.of("provider", "openai"));
    }

    @Override
    public Flux<GatewayResponseChunk> completeStream(GatewayRequest request) {
        String model = extractModel(request);

        List<Map<String, Object>> messages = buildMessages(request);

        Map<String, Object> body = Map.of(
            "model", model,
            "messages", messages,
            "stream", true,
            "temperature", 0.1,
            "top_p", 0.9,
            "max_tokens", 4096
        );

        String url = baseUrl + "/chat/completions";

        return webClient.post()
            .uri(url)
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(Map.class)
            .flatMap(this::parseOpenAiChunk)
            .onErrorResume(new java.util.function.Function<Throwable, org.reactivestreams.Publisher<? extends com.bookmind.gateway.core.GatewayResponseChunk>>() {
                @Override
                public org.reactivestreams.Publisher<? extends com.bookmind.gateway.core.GatewayResponseChunk> apply(Throwable e) {
                    return reactor.core.publisher.Flux.just(com.bookmind.gateway.core.GatewayResponseChunk.error(request.taskId(), e.getMessage()));
                }
            });
    }

    @Override
    public List<float[]> embed(List<String> texts, String model) {
        String embedModel = model != null ? model : "text-embedding-3-small";

        Map<String, Object> body = Map.of(
            "model", embedModel,
            "input", texts
        );

        Map<String, Object> response = webClient.post()
            .uri(baseUrl + "/embeddings")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        if (response != null && response.containsKey("data")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            return data.stream()
                .<float[]>map(d -> {
                    @SuppressWarnings("unchecked")
                    List<Double> embedding = (List<Double>) d.get("embedding");
                    double[] doubles = embedding.stream().mapToDouble(Double::doubleValue).toArray();
                    float[] floats = new float[doubles.length];
                    for (int i = 0; i < doubles.length; i++) {
                        floats[i] = (float) doubles[i];
                    }
                    return floats;
                })
                .toList();
        }
        return List.of();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "baseUrl", Map.of("type", "string", "default", "https://api.openai.com/v1"),
            "apiKey", Map.of("type", "string", "format", "password"),
            "model", Map.of("type", "string", "default", "gpt-4o-mini"),
            "temperature", Map.of("type", "number", "default", 0.1),
            "topP", Map.of("type", "number", "default", 0.9),
            "maxTokens", Map.of("type", "integer", "default", 4096)
        );
    }

    private String extractModel(GatewayRequest request) {
        return (String) request.parameters().getOrDefault("model", "gpt-4o-mini");
    }

    private List<Map<String, Object>> buildMessages(GatewayRequest request) {
        List<Map<String, Object>> messages = new java.util.ArrayList<>();

        if (request.parameters().containsKey("systemPrompt")) {
            messages.add(Map.of("role", "system", "content", request.parameters().get("systemPrompt")));
        }

        if (request.parameters().containsKey("question")) {
            messages.add(Map.of("role", "user", "content", request.parameters().get("question")));
        } else if (request.parameters().containsKey("text")) {
            messages.add(Map.of("role", "user", "content",
                "Context:\n" + request.parameters().get("text") + "\n\nExtract verbatim passages with page citations."));
        }

        return messages;
    }

    private Flux<GatewayResponseChunk> parseOpenAiChunk(Map<String, Object> chunk) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");

        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> choice = choices.get(0);
            Map<String, Object> delta = (Map<String, Object>) choice.get("delta");

            if (delta != null && delta.containsKey("content")) {
                String content = (String) delta.get("content");
                if (content != null && !content.isEmpty()) {
                    return Flux.just(GatewayResponseChunk.content(
                        java.util.UUID.randomUUID().toString(), content));
                }
            }

            String finishReason = (String) choice.get("finish_reason");
            if ("stop".equals(finishReason) || "length".equals(finishReason)) {
                return Flux.just(GatewayResponseChunk.finalChunk(
                    java.util.UUID.randomUUID().toString(),
                    "",
                    Map.of("provider", "openai")
                ));
            }
        }

        return Flux.empty();
    }
}