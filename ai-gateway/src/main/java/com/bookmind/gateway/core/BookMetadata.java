package com.bookmind.gateway.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;

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