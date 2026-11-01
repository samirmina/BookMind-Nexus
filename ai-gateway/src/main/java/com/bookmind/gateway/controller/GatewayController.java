package com.bookmind.gateway.controller;

import com.bookmind.gateway.core.GatewayModels.GatewayRequest;
import com.bookmind.gateway.core.GatewayModels.GatewayResponse;
import com.bookmind.gateway.core.GatewayModels.GatewayResponseChunk;
import com.bookmind.gateway.core.GatewayModels.ProviderModel;
import com.bookmind.gateway.core.GatewayService;
import com.bookmind.gateway.providers.ProviderAdapterFactory;
import com.bookmind.gateway.telemetry.GatewayTelemetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * REST controller for AI Gateway.
 * Exposes gateway functionality to the Web App.
 */
@RestController
@RequestMapping("/api/gateway")
public class GatewayController {
    
    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);
    
    private final GatewayService gatewayService;
    private final ProviderAdapterFactory providerFactory;
    private final GatewayTelemetryService telemetryService;
    
    public GatewayController(GatewayService gatewayService, 
                            ProviderAdapterFactory providerFactory,
                            GatewayTelemetryService telemetryService) {
        this.gatewayService = gatewayService;
        this.providerFactory = providerFactory;
        this.telemetryService = telemetryService;
    }
    
    /**
     * Execute a non-streaming gateway request.
     */
    @PostMapping(value = "/execute", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GatewayModels.GatewayResponse> execute(@RequestBody GatewayRequest request) {
        log.info("Gateway execute request | taskType={} | session={}", request.taskType(), request.sessionId());
        
        GatewayModels.GatewayResponse response = gatewayService.execute(request);
        
        if ("error".equals(response.status())) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }
    
    /**
     * Execute a streaming gateway request.
     */
    @PostMapping(value = "/execute/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<GatewayModels.GatewayResponseChunk> executeStream(@RequestBody GatewayRequest request) {
        log.info("Gateway streaming request | taskType={} | session={}", request.taskType(), request.sessionId());
        
        return gatewayService.executeStream(request);
    }
    
    /**
     * Generate embeddings.
     */
    @PostMapping(value = "/embed", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> embed(@RequestBody Map<String, Object> request) {
        List<String> texts = (List<String>) request.get("texts");
        String model = (String) request.get("model");
        
        if (texts == null || texts.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "texts array is required"));
        }
        
        try {
            List<float[]> embeddings = gatewayService.embed(texts, (String) request.get("model"));
            return ResponseEntity.ok(Map.of(
                "embeddings", embeddings,
                "count", embeddings.size(),
                "dimensions", embeddings.isEmpty() ? 0 : embeddings.get(0).length
            ));
        } catch (Exception e) {
            log.error("Embedding failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get available providers and models.
     */
    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> getProviders() {
        return ResponseEntity.ok(Map.of(
            "providers", gatewayService.getAvailableProviderIds(),
            "models", gatewayService.getAvailableModels()
        ));
    }
    
    /**
     * Get telemetry for a session.
     */
    @GetMapping("/telemetry/session/{sessionId}")
    public ResponseEntity<List<com.bookmind.gateway.telemetry.GatewayTelemetryEntities.GatewayTelemetryRecord>> getSessionTelemetry(
            @PathVariable String sessionId) {
        return ResponseEntity.ok(telemetryService.getSessionTelemetry(sessionId));
    }
    
    /**
     * Get provider telemetry.
     */
    @GetMapping("/telemetry/provider/{provider}")
    public ResponseEntity<List<com.bookmind.gateway.telemetry.GatewayTelemetryEntities.GatewayTelemetryRecord>> getProviderTelemetry(
            @PathVariable String provider) {
        return ResponseEntity.ok(telemetryService.getProviderTelemetry(provider));
    }
    
    /**
     * Get error telemetry.
     */
    @GetMapping("/telemetry/errors")
    public ResponseEntity<List<com.bookmind.gateway.telemetry.GatewayTelemetryEntities.GatewayTelemetryRecord>> getErrors() {
        return ResponseEntity.ok(telemetryService.getErrors());
    }
    
    /**
     * Health check.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        var availableProviders = providerFactory.getAvailableProviders();
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "providers", availableProviders.size(),
            "providersDetail", availableProviders.stream()
                .map(p -> Map.of("id", p.getProviderId(), "name", p.getDisplayName(), "available", p.isAvailable()))
                .toList(),
            "timestamp", java.time.Instant.now()
        ));
    }
}