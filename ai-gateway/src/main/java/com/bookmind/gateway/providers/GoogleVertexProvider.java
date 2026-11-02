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
 * Google Vertex AI / Gemini provider adapter.
 */
@Component
public class GoogleVertexProvider implements AiProvider {

    private final WebClient webClient;

    @Value("${ai-gateway.providers.google.base-url:https://generativelanguage.googleapis.com}")
    private String baseUrl;

    @Value("${ai-gateway.providers.google.api-key:}")
    private String apiKey;

    @Value("${ai-gateway.providers.google.enabled:true}")
    private boolean enabled;

    public GoogleVertexProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getProviderId() {
        return "google";
    }

    @Override
    public String getDisplayName() {
        return "Google Vertex AI / Gemini";
    }

    @Override
    public boolean isAvailable() {
        return enabled && (apiKey != null && !apiKey.isBlank());
    }

    @Override
    public List<ProviderModel> getSupportedModels() {
        return List.of(
            new ProviderModel("gemini-1.5-pro", "Gemini 1.5 Pro", "chat", 2000000, true, true, Map.of()),
            new ProviderModel("gemini-1.5-flash", "Gemini 1.5 Flash", "chat", 1000000, true, true, Map.of()),
            new ProviderModel("gemini-1.0-pro", "Gemini 1.0 Pro", "chat", 32768, true, true, Map.of()),
            new ProviderModel("text-embedding-004", "Embedding 004", "embedding", 2048, false, false, Map.of("dimensions", 768))
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

        return GatewayResponse.success(taskId, content.toString(), Map.of("provider", "google"));
    }

    @Override
    public Flux<GatewayResponseChunk> completeStream(GatewayRequest request) {
        String model = extractModel(request);

        String systemPrompt = (String) request.parameters().getOrDefault("systemPrompt",
            "You are a helpful assistant that extracts verbatim passages from books.");
        String userPrompt = buildUserPrompt(request);

        Map<String, Object> body = Map.of(
            "contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userPrompt))
            )),
            "systemInstruction", Map.of("parts", List.of(Map.of("text",
                (String) request.parameters().getOrDefault("systemPrompt",
                    "You are a helpful assistant that extracts verbatim passages from books."
                )))),
            "generationConfig", Map.of(
                "temperature", 0.1,
                "topP", 0.9,
                "topK", 40,
                "maxOutputTokens", 4096
            )
        );

        String url = baseUrl + "/v1beta/models/" + extractModel(request) + ":streamGenerateContent?key=" + apiKey;

        return webClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(Map.class)
            .flatMap(this::parseGeminiChunk)
            .onErrorResume(new java.util.function.Function<Throwable, org.reactivestreams.Publisher<? extends com.bookmind.gateway.core.GatewayResponseChunk>>() {
                @Override
                public org.reactivestreams.Publisher<? extends com.bookmind.gateway.core.GatewayResponseChunk> apply(Throwable e) {
                    return reactor.core.publisher.Flux.just(com.bookmind.gateway.core.GatewayResponseChunk.error(request.taskId(), e.getMessage()));
                }
            });
    }

    @Override
    public List<float[]> embed(List<String> texts, String model) {
        String embedModel = model != null ? model : "text-embedding-004";

        return texts.stream()
            .map(text -> {
                Map<String, Object> body = Map.of(
                    "model", "models/" + embedModel,
                    "content", Map.of("parts", List.of(Map.of("text", text)))
                );

                Map<String, Object> response = webClient.post()
                    .uri(baseUrl + "/v1beta/models/" + embedModel + ":embedContent?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

                if (response != null && response.containsKey("embedding")) {
                    Map<String, Object> embedding = (Map<String, Object>) response.get("embedding");
                    @SuppressWarnings("unchecked")
                    List<Double> values = (List<Double>) embedding.get("values");
                    double[] doubles = values.stream().mapToDouble(Double::doubleValue).toArray();
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
            "baseUrl", Map.of("type", "string", "default", "https://generativelanguage.googleapis.com"),
            "apiKey", Map.of("type", "string", "format", "password"),
            "model", Map.of("type", "string", "default", "gemini-1.5-flash"),
            "temperature", Map.of("type", "number", "default", 0.1),
            "topP", Map.of("type", "number", "default", 0.9),
            "maxTokens", Map.of("type", "integer", "default", 4096)
        );
    }

    private String extractModel(GatewayRequest request) {
        return (String) request.parameters().getOrDefault("model", "gemini-1.5-flash");
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

    private Flux<GatewayResponseChunk> parseGeminiChunk(Map<String, Object> chunk) {
        Object candidates = chunk.get("candidates");
        if (candidates instanceof List && !((List<?>) candidates).isEmpty()) {
            Map<String, Object> candidate = (Map<String, Object>) ((List<?>) candidates).get(0);
            Object content = candidate.get("content");
            if (content instanceof Map) {
                Object parts = ((Map<String, Object>) content).get("parts");
                if (parts instanceof List) {
                    for (Object part : (List<?>) parts) {
                        if (part instanceof Map) {
                            String text = (String) ((Map<String, Object>) part).get("text");
                            if (text != null && !text.isEmpty()) {
                                return Flux.just(GatewayResponseChunk.content(
                                    java.util.UUID.randomUUID().toString(), text));
                            }
                        }
                    }
                }
            }
        }
        return Flux.empty();
    }
}