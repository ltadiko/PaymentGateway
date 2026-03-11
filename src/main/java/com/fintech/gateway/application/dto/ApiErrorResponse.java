package com.fintech.gateway.application.dto;

import java.time.Instant;

/**
 * Standard error response DTO returned by all API endpoints on failure.
 *
 * <p>Provides a consistent, predictable error format for API consumers:
 * <pre>{@code
 * {
 *   "timestamp": "2026-03-10T19:00:00Z",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Payment not found: 550e8400-e29b-41d4-a716-446655440000",
 *   "path": "/api/v1/payments/550e8400-e29b-41d4-a716-446655440000"
 * }
 * }</pre>
 *
 * <p><strong>Security:</strong> The {@code message} field must never contain
 * stack traces, internal class names, or PCI-sensitive data. Only safe,
 * client-facing messages should be included.
 *
 * @param timestamp when the error occurred
 * @param status    HTTP status code
 * @param error     HTTP status reason phrase (e.g., "Not Found", "Conflict")
 * @param message   human-readable error description (safe for clients)
 * @param path      the request path that caused the error
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {

    /**
     * Factory method for creating an error response.
     *
     * @param status  HTTP status code
     * @param error   HTTP reason phrase
     * @param message client-safe error message
     * @param path    request path
     * @return a new {@code ApiErrorResponse}
     */
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path);
    }
}

