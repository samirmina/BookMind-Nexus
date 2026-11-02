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