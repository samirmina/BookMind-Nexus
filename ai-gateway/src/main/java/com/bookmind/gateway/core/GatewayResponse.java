package com.bookmind.gateway.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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