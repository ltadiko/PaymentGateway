package com.fintech.gateway.infrastructure.adapter.in.rest;

import com.fintech.gateway.application.dto.ApiErrorResponse;
import com.fintech.gateway.domain.exception.DuplicatePaymentException;
import com.fintech.gateway.domain.exception.InvalidStateTransitionException;
import com.fintech.gateway.domain.exception.PaymentNotFoundException;
import com.fintech.gateway.infrastructure.crypto.EncryptionService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Global exception handler that translates exceptions into consistent
 * {@link ApiErrorResponse} JSON responses.
 *
 * <p>Every exception thrown by any {@code @RestController} is intercepted here,
 * ensuring:
 * <ul>
 *   <li><strong>Consistent format:</strong> All errors follow the same JSON structure</li>
 *   <li><strong>No stack trace leakage:</strong> Internal details are logged server-side
 *       but never returned to the client</li>
 *   <li><strong>PCI compliance:</strong> Sensitive data (account numbers, encryption keys)
 *       is never included in error messages</li>
 *   <li><strong>Proper HTTP status codes:</strong> Each exception type maps to the
 *       semantically correct status code</li>
 * </ul>
 *
 * <p>Exception-to-status mapping:
 * <pre>
 * Exception                        → HTTP Status
 * ─────────────────────────────────────────────────
 * PaymentNotFoundException         → 404 Not Found
 * DuplicatePaymentException        → 409 Conflict
 * InvalidStateTransitionException  → 422 Unprocessable Entity
 * MethodArgumentNotValidException  → 400 Bad Request
 * HttpMessageNotReadableException  → 400 Bad Request
 * MethodArgumentTypeMismatchException → 400 Bad Request
 * HttpRequestMethodNotSupportedException → 405 Method Not Allowed
 * EncryptionService.EncryptionException → 500 Internal Server Error
 * (any other)                      → 500 Internal Server Error
 * </pre>
 *
 * @see ApiErrorResponse
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Domain Exceptions ──

    /**
     * Handles payment not found — returns 404.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 404 response with error details
     */
    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentNotFound(
            PaymentNotFoundException ex, HttpServletRequest request) {
        log.warn("Payment not found: paymentId={}", ex.getPaymentId());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Handles duplicate payment submission (idempotency conflict) — returns 409.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 409 response with error details
     */
    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicatePayment(
            DuplicatePaymentException ex, HttpServletRequest request) {
        log.warn("Duplicate payment: idempotencyKey={}", ex.getIdempotencyKey());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Handles invalid state transition — returns 422.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 422 response with error details
     */
    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTransition(
            InvalidStateTransitionException ex, HttpServletRequest request) {
        log.warn("Invalid state transition: currentStatus={}, event={}",
                ex.getCurrentStatus(), ex.getEvent());
        return buildResponse(HttpStatus.valueOf(422), ex.getMessage(), request);
    }

    // ── Validation / Request Parsing ──

    /**
     * Handles Bean Validation failures — returns 400 with field-level details.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 400 response with validation error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * Handles missing required request headers (e.g., Idempotency-Key) — returns 400.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 400 response indicating which header is missing
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        String message = ex.getHeaderName() + " header is required";
        log.warn("Missing required header: {}", ex.getHeaderName());
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * Handles malformed JSON request body — returns 400.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 400 response with safe error message
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    /**
     * Handles type mismatch in path/query parameters (e.g., invalid UUID) — returns 400.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 400 response with safe error message
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        log.warn("Type mismatch: parameter={}, value={}", ex.getName(), ex.getValue());
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * Handles unsupported HTTP method — returns 405.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 405 response
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request);
    }

    // ── Infrastructure Exceptions ──

    /**
     * Handles encryption/decryption failures — returns 500.
     *
     * <p>Logs the full exception server-side but returns a generic message to the client
     * to avoid leaking cryptographic details.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 500 response with generic error message
     */
    @ExceptionHandler(EncryptionService.EncryptionException.class)
    public ResponseEntity<ApiErrorResponse> handleEncryptionError(
            EncryptionService.EncryptionException ex, HttpServletRequest request) {
        log.error("Encryption error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An internal error occurred while processing your request", request);
    }

    // ── Security Exceptions ──

    /**
     * Handles authorization failures from {@code @PreAuthorize} — returns 403.
     *
     * <p>Thrown when an authenticated user lacks the required role
     * (e.g., trying to submit a payment without {@code ROLE_PAYMENT_SUBMIT}).
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 403 response with error details
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: path={}, reason={}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "Insufficient permissions", request);
    }

    // ── Catch-All ──

    /**
     * Catches any unhandled exception — returns 500.
     *
     * <p>Logs the full stack trace server-side for debugging but returns
     * a generic message to the client. This prevents internal implementation
     * details, class names, or stack traces from leaking.
     *
     * @param ex      the exception
     * @param request the HTTP request
     * @return 500 response with generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}: {}", request.getMethod(),
                request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", request);
    }

    // ── Helper ──

    /**
     * Builds a standardised error response.
     *
     * @param status  the HTTP status
     * @param message the client-safe error message
     * @param request the HTTP request (used to extract the path)
     * @return a {@link ResponseEntity} wrapping the {@link ApiErrorResponse}
     */
    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status, String message, HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}

