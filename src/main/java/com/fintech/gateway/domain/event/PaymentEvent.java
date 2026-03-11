package com.fintech.gateway.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sealed interface for domain events related to the payment lifecycle.
 * Events carry only IDs and non-sensitive metadata — NO PCI data (PCI compliance).
 * Used by Kafka publishers to route events to appropriate topics.
 */
public sealed interface PaymentEvent {

    UUID eventId();
    UUID paymentId();
    String tenantId();
    Instant timestamp();

    record PaymentSubmitted(
            UUID eventId,
            UUID paymentId,
            String tenantId,
            Instant timestamp
    ) implements PaymentEvent {
        public PaymentSubmitted {
            validate(eventId, paymentId, tenantId, timestamp);
        }
    }

    record FraudAssessmentCompleted(
            UUID eventId,
            UUID paymentId,
            String tenantId,
            boolean approved,
            int fraudScore,
            String reason,
            Instant timestamp
    ) implements PaymentEvent {
        public FraudAssessmentCompleted {
            validate(eventId, paymentId, tenantId, timestamp);
        }
    }

    record BankProcessingCompleted(
            UUID eventId,
            UUID paymentId,
            String tenantId,
            boolean success,
            String bankReference,
            String reason,
            Instant timestamp
    ) implements PaymentEvent {
        public BankProcessingCompleted {
            validate(eventId, paymentId, tenantId, timestamp);
        }
    }

    private static void validate(UUID eventId, UUID paymentId, String tenantId, Instant timestamp) {
        if (eventId == null) throw new IllegalArgumentException("eventId must not be null");
        if (paymentId == null) throw new IllegalArgumentException("paymentId must not be null");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be null or blank");
        if (timestamp == null) throw new IllegalArgumentException("timestamp must not be null");
    }
}

