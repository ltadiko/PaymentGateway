package com.fintech.gateway.domain.exception;

/**
 * Thrown when a duplicate payment submission is detected via idempotency key.
 */
public class DuplicatePaymentException extends RuntimeException {

    private final String idempotencyKey;

    public DuplicatePaymentException(String idempotencyKey) {
        super("Duplicate payment submission for idempotency key: " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
}

