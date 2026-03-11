package com.fintech.gateway.domain.exception;

import java.util.UUID;

/**
 * Thrown when a payment cannot be found by ID and tenant.
 */
public class PaymentNotFoundException extends RuntimeException {

    private final UUID paymentId;

    public PaymentNotFoundException(UUID paymentId) {
        super("Payment not found: " + paymentId);
        this.paymentId = paymentId;
    }

    public UUID getPaymentId() { return paymentId; }
}

