package com.bookmind.gateway.providers;

import com.bookmind.gateway.core.GatewayModels.GatewayRequest;
import com.bookmind.gateway.core.GatewayModels.GatewayResponse;
import com.bookmind.gateway.core.GatewayModels.GatewayResponseChunk;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Interface for AI provider adapters.
 * Each provider (Ollama, OpenAI, Anthropic, etc.) implements this interface.
 */
public interface AiProvider {
    
    /**
     * Provider identifier (e.g., "ollama", "openai", "anthropic", "google").
     */
    String getProviderId();
    
    /**
     * Human-readable provider name.
     */
    String getDisplayName();
    
    /**
     * Check if this provider is available/configured.
     */
    boolean isAvailable();
    
    /**
     * Get supported models for this provider.
     */
    List<ProviderModel> getSupportedModels();
    
    /**
     * Execute a completion request (non-streaming).
     */
    GatewayResponse complete(GatewayRequest request);
    
    /**
     * Execute a completion request (streaming).
     */
    Flux<GatewayModels.GatewayResponseChunk> completeStream(GatewayRequest request);
    
    /**
     * Generate embeddings for the given texts.
     */
    List<float[]> embed(List<String> texts, String model);
    
    /**
     * Check if provider supports streaming.
     */
    default boolean supportsStreaming() {
        return true;
    }
    
    /**
     * Get provider-specific configuration schema.
     */
    Map<String, Object> getConfigSchema();
    
    /**
     * Validate provider configuration.
     */
    default boolean validateConfig(Map<String, Object> config) {
        return true;
    }
}

/**
 * Model information for a provider.
 */
record ProviderModel(
    String id,
    String name,
    String type,
    int maxContextTokens,
    boolean supportsStreaming,
    boolean supportsTools,
    Map<String, Object> capabilities
) {}