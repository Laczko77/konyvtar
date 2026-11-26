package com.library.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> validationErrors
) {
    public ErrorResponse(int status, String error, String message) {
        this(LocalDateTime.now(), status, error, message, Map.of());
    }

    public ErrorResponse(int status, String error, String message, Map<String, String> validationErrors) {
        this(LocalDateTime.now(), status, error, message, validationErrors);
    }
}
