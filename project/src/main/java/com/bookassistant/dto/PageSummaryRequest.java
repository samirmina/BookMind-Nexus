package com.bookassistant.dto;

public record PageSummaryRequest(Long bookId, int startPage, int endPage, String outputLanguage, String chunkSize) {}