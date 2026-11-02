package com.bookmind.gateway.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.Map;

/**
 * Streaming response chunk.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GatewayResponseChunk(
    String taskId,
    String chunkType,
    String content,
    boolean isFinal,
    Map<String, Object> metadata,
    Instant timestamp
) {
    public static GatewayResponseChunk content(String taskId, String content) {
        return new GatewayResponseChunk(taskId, "content", content, false, null, Instant.now());
    }

    public static GatewayResponseChunk finalChunk(String taskId, String content, Map<String, Object> metadata) {
        return new GatewayResponseChunk(taskId, "final", content, true, metadata, Instant.now());
    }

    public static GatewayResponseChunk error(String taskId, String error) {
        return new GatewayResponseChunk(taskId, "error", error, true, null, Instant.now());
    }

    public static GatewayResponseChunk status(String taskId, String status) {
        return new GatewayResponseChunk(taskId, "status", status, false, null, Instant.now());
    }
}