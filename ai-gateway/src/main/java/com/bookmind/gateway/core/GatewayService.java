package com.bookmind.gateway.core;

import com.bookmind.gateway.providers.AiProvider;
import com.bookmind.gateway.providers.ProviderAdapterFactory;
import com.bookmind.gateway.providers.ProviderModel;
import com.bookmind.gateway.telemetry.GatewayTelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core AI Gateway service.
 * Orchestrates provider selection, request execution, and telemetry.
 */
@Service
public class GatewayService {

    private static final Logger log = LoggerFactory.getLogger(GatewayService.class);

    private final ProviderAdapterFactory providerFactory;
    private final GatewayTelemetryService telemetryService;

    public GatewayService(ProviderAdapterFactory providerFactory, GatewayTelemetryService telemetryService) {
        this.providerFactory = providerFactory;
        this.telemetryService = telemetryService;
    }

    /**
     * Execute a non-streaming gateway request.
     */
    public GatewayResponse execute(GatewayRequest request) {
        String taskId = request.taskId() != null ? request.taskId() : UUID.randomUUID().toString();
        GatewayRequest enrichedRequest = new GatewayRequest(
            request.sessionId(),
            taskId,
            request.taskType(),
            request.parameters(),
            request.context(),
            false
        );

        Instant start = Instant.now();
        AiProvider provider = providerFactory.getProviderForRequest(enrichedRequest);

        log.info("Executing gateway request | taskId={} | type={} | provider={} | session={}",
            taskId, enrichedRequest.taskType(), provider.getProviderId(), enrichedRequest.sessionId());

        try {
            GatewayResponse response = provider.complete(enrichedRequest);

            // Record telemetry
            telemetryService.recordRequest(GatewayTelemetryService.TelemetryRequest.builder()
                .taskId(taskId)
                .sessionId(enrichedRequest.sessionId())
                .taskType(enrichedRequest.taskType())
                .provider(provider.getProviderId())
                .model((String) enrichedRequest.parameters().get("model"))
                .status(response.status())
                .durationMs(java.time.Duration.between(start, Instant.now()).toMillis())
                .errorCount(response.errors() != null ? response.errors().size() : 0)
                .build());

            log.info("Gateway request completed | taskId={} | status={} | duration={}ms",
                taskId, response.status(), java.time.Duration.between(start, Instant.now()).toMillis());

            return response;

        } catch (Exception e) {
            log.error("Gateway request failed | taskId={} | error={}", taskId, e.getMessage(), e);

            telemetryService.recordRequest(GatewayTelemetryService.TelemetryRequest.builder()
                .taskId(taskId)
                .sessionId(enrichedRequest.sessionId())
                .taskType(enrichedRequest.taskType())
                .provider(provider.getProviderId())
                .model((String) enrichedRequest.parameters().get("model"))
                .status("error")
                .durationMs(java.time.Duration.between(start, Instant.now()).toMillis())
                .errorCount(1)
                .build());

            return GatewayResponse.error(taskId, e.getMessage());
        }
    }

    /**
     * Execute a streaming gateway request.
     */
    public Flux<GatewayResponseChunk> executeStream(GatewayRequest request) {
        String taskId = request.taskId() != null ? request.taskId() : UUID.randomUUID().toString();
        GatewayRequest enrichedRequest = new GatewayRequest(
            request.sessionId(),
            taskId,
            request.taskType(),
            request.parameters(),
            request.context(),
            true
        );

        Instant start = Instant.now();
        AiProvider provider = providerFactory.getProviderForRequest(enrichedRequest);

        log.info("Executing streaming gateway request | taskId={} | type={} | provider={} | session={}",
            taskId, enrichedRequest.taskType(), provider.getProviderId(), enrichedRequest.sessionId());

        return provider.completeStream(enrichedRequest)
            .doOnNext(chunk -> {
                if (chunk.isFinal()) {
                    telemetryService.recordRequest(GatewayTelemetryService.TelemetryRequest.builder()
                        .taskId(taskId)
                        .sessionId(enrichedRequest.sessionId())
                        .taskType(enrichedRequest.taskType())
                        .provider(provider.getProviderId())
                        .model((String) enrichedRequest.parameters().get("model"))
                        .status(chunk.chunkType().equals("error") ? "error" : "success")
                        .durationMs(java.time.Duration.between(start, Instant.now()).toMillis())
                        .errorCount(chunk.chunkType().equals("error") ? 1 : 0)
                        .build());
                }
            })
            .doOnError(e -> {
                log.error("Streaming gateway request failed | taskId={} | error={}", taskId, e.getMessage(), e);
                telemetryService.recordRequest(GatewayTelemetryService.TelemetryRequest.builder()
                    .taskId(taskId)
                    .sessionId(enrichedRequest.sessionId())
                    .taskType(enrichedRequest.taskType())
                    .provider(provider.getProviderId())
                    .model((String) enrichedRequest.parameters().get("model"))
                    .status("error")
                    .durationMs(java.time.Duration.between(start, Instant.now()).toMillis())
                    .errorCount(1)
                    .build());
            });
    }

    /**
     * Generate embeddings using the best available provider.
     */
    public List<float[]> embed(List<String> texts, String preferredModel) {
        AiProvider provider = providerFactory.getAvailableProviders().stream()
            .filter(p -> p.getSupportedModels().stream()
                .anyMatch(m -> m.type().equals("embedding") &&
                    (preferredModel == null || m.id().equals(preferredModel))))
            .findFirst()
            .orElseGet(() -> providerFactory.getAvailableProviders().get(0));

        String model = preferredModel != null ? preferredModel :
            provider.getSupportedModels().stream()
                .filter(m -> m.type().equals("embedding"))
                .findFirst()
                .map(ProviderModel::id)
                .orElse(null);

        return provider.embed(texts, model);
    }

    /**
     * Get all available provider models for UI.
     */
    public java.util.List<ProviderModel> getAvailableModels() {
        return providerFactory.getAvailableProviders().stream()
            .flatMap(p -> p.getSupportedModels().stream())
            .toList();
    }

    /**
     * Get available provider IDs.
     */
    public java.util.List<String> getAvailableProviderIds() {
        return providerFactory.getAvailableProviders().stream()
            .map(AiProvider::getProviderId)
            .toList();
    }
}