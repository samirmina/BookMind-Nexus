package com.bookmind.gateway.providers;

import com.bookmind.gateway.core.GatewayModels.GatewayRequest;
import com.bookmind.gateway.core.GatewayModels.GatewayResponse;
import com.bookmind.gateway.core.GatewayModels.GatewayResponseChunk;
import com.bookmind.gateway.core.GatewayModels.ProviderModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Factory for selecting and managing AI providers.
 * Supports explicit provider selection and auto-detection.
 */
@Component
public class ProviderAdapterFactory {
    
    private static final Logger log = LoggerFactory.getLogger(ProviderAdapterFactory.class);
    
    private final Map<String, AiProvider> providers = new HashMap<>();
    
    @Autowired(required = false)
    private List<AiProvider> providerList;
    
    @PostConstruct
    public void init() {
        if (providerList != null) {
            for (AiProvider provider : providerList) {
                if (provider.isAvailable()) {
                    providers.put(provider.getProviderId(), provider);
                    log.info("Registered AI provider: {} ({})", provider.getDisplayName(), provider.getProviderId());
                } else {
                    log.warn("Provider {} not available (disabled or misconfigured)", provider.getProviderId());
                }
            }
        }
    }
    
    /**
     * Get provider by explicit ID.
     */
    public Optional<AiProvider> getProvider(String providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }
    
    /**
     * Get provider for a request (explicit or auto-detected).
     */
    public AiProvider getProviderForRequest(GatewayRequest request) {
        // Explicit provider in request
        if (request.context() != null && request.context().provider() != null) {
            return getProvider(request.context().provider())
                .orElseThrow(() -> new IllegalArgumentException("Provider not available: " + request.context().provider()));
        }
        
        // Auto-detect from model name or config
        String model = (String) request.parameters().get("model");
        if (model != null) {
            return detectProviderByModel(model)
                .orElseGet(this::getDefaultProvider);
        }
        
        // Fallback to default
        return getDefaultProvider();
    }
    
    /**
     * Auto-detect provider from model name.
     */
    private Optional<AiProvider> detectProviderByModel(String model) {
        String lower = model.toLowerCase();
        
        if (lower.startsWith("gpt-") || lower.contains("openai") || lower.contains("embedding-3") || lower.contains("ada-002")) {
            return getProvider("openai");
        }
        if (lower.startsWith("claude-") || lower.contains("anthropic")) {
            return getProvider("anthropic");
        }
        if (lower.startsWith("gemini-") || lower.contains("google") || lower.contains("vertex")) {
            return getProvider("google");
        }
        if (lower.startsWith("llama") || lower.contains("ollama") || lower.contains("nomic") || lower.contains("mxbai")) {
            return getProvider("ollama");
        }
        return Optional.empty();
    }
    
    /**
     * Get default available provider (prefers local Ollama).
     */
    private AiProvider getDefaultProvider() {
        // Prefer Ollama for local-first
        if (providers.containsKey("ollama") && providers.get("ollama").isAvailable()) {
            return providers.get("ollama");
        }
        // Then OpenAI
        if (providers.containsKey("openai") && providers.get("openai").isAvailable()) {
            return providers.get("openai");
        }
        // Then Anthropic
        if (providers.containsKey("anthropic") && providers.get("anthropic").isAvailable()) {
            return providers.get("anthropic");
        }
        // Then Google
        if (providers.containsKey("google") && providers.get("google").isAvailable()) {
            return providers.get("google");
        }
        
        throw new IllegalStateException("No AI providers available. Configure at least one provider.");
    }
    
    /**
     * Get all available providers.
     */
    public List<AiProvider> getAvailableProviders() {
        return providers.values().stream()
            .filter(AiProvider::isAvailable)
            .toList();
    }
    
    /**
     * Get all provider models (for UI dropdowns).
     */
    public List<ProviderModel> getAllModels() {
        return providers.values().stream()
            .filter(AiProvider::isAvailable)
            .flatMap(p -> p.getSupportedModels().stream())
            .toList();
    }
    
    /**
     * Register a provider dynamically.
     */
    public void registerProvider(AiProvider provider) {
        if (provider.isAvailable()) {
            providers.put(provider.getProviderId(), provider);
            log.info("Dynamically registered provider: {}", provider.getDisplayName());
        }
    }
    
    /**
     * Unregister a provider.
     */
    public void unregisterProvider(String providerId) {
        providers.remove(providerId);
        log.info("Unregistered provider: {}", providerId);
    }
}