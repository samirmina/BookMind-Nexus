package com.bookmind.gateway.providers;

import java.util.Map;

/**
 * Model information for a provider.
 */
public record ProviderModel(
    String id,
    String name,
    String type,
    int maxContextTokens,
    boolean supportsStreaming,
    boolean supportsTools,
    Map<String, Object> capabilities
) {}