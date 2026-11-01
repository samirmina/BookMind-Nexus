package com.bookmind.gateway.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Base request for all AI Gateway operations.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GatewayRequest(
    String sessionId,
    String taskId,
    String taskType,
    Map<String, Object> parameters,
    GatewayContext context,
    boolean stream
) {
    public static GatewayRequest extract(String sessionId, String bookId, int startPage, int endPage, boolean stream) {
        return new GatewayRequest(
            sessionId,
            java.util.UUID.randomUUID().toString(),
            "extract",
            Map.of("bookId", bookId, "startPage", startPage, "endPage", endPage),
            new GatewayContext(null, null, null),
            stream
        );
    }
    
    public static GatewayRequest ask(String sessionId, String question, String bookId, boolean stream) {
        return new GatewayRequest(
            sessionId,
            java.util.UUID.randomUUID().toString(),
            "ask",
            Map.of("question", question, "bookId", bookId),
            new GatewayContext(null, null, null),
            stream
        );
    }
    
    public static GatewayRequest agentTask(String sessionId, String task, Map<String, Object> params, boolean stream) {
        return new GatewayRequest(
            sessionId,
            java.util.UUID.randomUUID().toString(),
            "agent",
            Map.of("task", task, "parameters", params),
            new GatewayContext(null, null, null),
            stream
        );
    }
}

/**
 * Context for the gateway request.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GatewayContext(
    String userId,
    String provider,
    Map<String, Object> providerConfig
) {}

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

/**
 * Complete response for non-streaming operations.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GatewayResponse(
    String taskId,
    String status,
    String content,
    Map<String, Object> metadata,
    List<String> errors,
    Instant completedAt
) {
    public static GatewayResponse success(String taskId, String content, Map<String, Object> metadata) {
        return new GatewayResponse(taskId, "success", content, metadata, null, Instant.now());
    }
    
    public static GatewayResponse error(String taskId, String error) {
        return new GatewayResponse(taskId, "error", null, null, List.of(error), Instant.now());
    }
}

/**
 * Book metadata for gateway operations.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookMetadata(
    String id,
    String title,
    String fileName,
    int pageCount,
    String language,
    String directoryPath,
    Instant createdAt
) {}