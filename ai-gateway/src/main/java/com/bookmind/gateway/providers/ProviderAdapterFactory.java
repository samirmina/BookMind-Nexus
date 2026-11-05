package com.bookmind.gateway.providers;

import com.bookmind.gateway.core.GatewayRequest;
import com.bookmind.gateway.core.GatewayResponse;
import com.bookmind.gateway.core.GatewayResponseChunk;
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
     * Get provider for a request (explicit provider required in context).
     */
    public AiProvider getProviderForRequest(GatewayRequest request) {
        // Explicit provider in request context (required)
        if (request.context() != null && request.context().provider() != null) {
            return getProvider(request.context().provider())
                .orElseThrow(() -> new IllegalArgumentException("Provider not available: " + request.context().provider()));
        }

        throw new IllegalArgumentException("Provider must be specified in request context");
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
     * Get all provider models.
     * Models are user-defined; this returns an empty list.
     */
    public List<ProviderModel> getAllModels() {
        return List.of();
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