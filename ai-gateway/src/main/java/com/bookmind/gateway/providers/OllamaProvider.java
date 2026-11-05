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
 * Ollama provider adapter.
 * Supports local Ollama instances with OpenAI-compatible API.
 */
@Component
public class OllamaProvider implements AiProvider {

    private final WebClient webClient;

    @Value("${ai-gateway.providers.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ai-gateway.providers.ollama.enabled:true}")
    private boolean enabled;

    public OllamaProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getProviderId() {
        return "ollama";
    }

    @Override
    public String getDisplayName() {
        return "Ollama (Local)";
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    @Override
    public List<ProviderModel> getSupportedModels() {
        return List.of();
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

        return GatewayResponse.success(taskId, content.toString(), Map.of("provider", "ollama"));
    }

    @Override
    public Flux<GatewayResponseChunk> completeStream(GatewayRequest request) {
        String model = extractModel(request);
        String prompt = buildPrompt(request);

        Map<String, Object> body = Map.of(
            "model", model,
            "prompt", prompt,
            "stream", true,
            "options", Map.of(
                "temperature", 0.1,
                "top_p", 0.9,
                "num_predict", 4096
            )
        );

        String url = baseUrl + "/api/generate";

        return webClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(Map.class)
            .map(this::parseOllamaChunk)
            .onErrorResume(new java.util.function.Function<Throwable, org.reactivestreams.Publisher<? extends com.bookmind.gateway.core.GatewayResponseChunk>>() {
                @Override
                public org.reactivestreams.Publisher<? extends com.bookmind.gateway.core.GatewayResponseChunk> apply(Throwable e) {
                    return reactor.core.publisher.Flux.just(com.bookmind.gateway.core.GatewayResponseChunk.error(request.taskId(), e.getMessage()));
                }
            });
    }

    @Override
    public List<float[]> embed(List<String> texts, String model) {
        if (model == null) {
            throw new IllegalArgumentException("Model must be specified for embeddings");
        }

        return texts.stream()
            .<float[]>map(text -> {
                Map<String, Object> body = Map.of(
                    "model", model,
                    "prompt", text
                );

                Map<String, Object> response = webClient.post()
                    .uri(baseUrl + "/api/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

                if (response != null && response.containsKey("embedding")) {
                    @SuppressWarnings("unchecked")
                    List<Double> embedding = (List<Double>) response.get("embedding");
                    double[] doubles = embedding.stream().mapToDouble(Double::doubleValue).toArray();
                    float[] floats = new float[doubles.length];
                    for (int i = 0; i < doubles.length; i++) {
                        floats[i] = (float) doubles[i];
                    }
                    return floats;
                }
                return new float[0];
            })
            .toList();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "baseUrl", Map.of("type", "string", "default", "http://localhost:11434"),
            "model", Map.of("type", "string"),
            "temperature", Map.of("type", "number", "default", 0.1),
            "topP", Map.of("type", "number", "default", 0.9),
            "maxTokens", Map.of("type", "integer", "default", 4096)
        );
    }

    private String extractModel(GatewayRequest request) {
        Object model = request.parameters().get("model");
        if (model == null) {
            throw new IllegalArgumentException("Model must be specified in request parameters");
        }
        return model.toString();
    }

    private String buildPrompt(GatewayRequest request) {
        StringBuilder sb = new StringBuilder();

        if (request.parameters().containsKey("systemPrompt")) {
            sb.append("System: ").append(request.parameters().get("systemPrompt")).append("\n\n");
        }

        if (request.parameters().containsKey("question")) {
            sb.append("Question: ").append(request.parameters().get("question")).append("\n\n");
        }

        if (request.parameters().containsKey("text")) {
            sb.append("Context:\n").append(request.parameters().get("text")).append("\n\n");
        }

        sb.append("Answer:");
        return sb.toString();
    }

    private GatewayResponseChunk parseOllamaChunk(Map<String, Object> chunk) {
        String response = (String) chunk.get("response");
        boolean done = Boolean.TRUE.equals(chunk.get("done"));

        if (response != null && !response.isEmpty()) {
            return GatewayResponseChunk.content(
                java.util.UUID.randomUUID().toString(),
                response
            );
        }

        if (done) {
            return GatewayResponseChunk.finalChunk(
                java.util.UUID.randomUUID().toString(),
                "",
                Map.of("provider", "ollama")
            );
        }

        return GatewayResponseChunk.status(
            java.util.UUID.randomUUID().toString(),
            "processing"
        );
    }
}