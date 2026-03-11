package com.fintech.gateway.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a single entry in the payment audit trail.
 *
 * <p>Each entry records one state transition in the payment lifecycle.
 * Entries are returned in chronological order ({@code createdAt ASC}),
 * providing a complete history from ingestion to final resolution.
 *
 * <p>Sensitive data in the {@code metadata} field is masked before
 * returning to the client.
 *
 * @param id             unique audit entry identifier
 * @param paymentId      the payment this entry belongs to
 * @param previousStatus status before this transition (null for initial creation)
 * @param newStatus      status after this transition
 * @param eventType      what triggered the transition (e.g., "PAYMENT_CREATED", "FRAUD_CHECK_PASSED")
 * @param metadata       additional context (fraud score, bank reference, etc.)
 * @param performedBy    the system component that made the change
 * @param createdAt      when the transition occurred
 */
public record AuditEntryResponse(
        UUID id,
        UUID paymentId,
        String previousStatus,
        String newStatus,
        String eventType,
        String metadata,
        String performedBy,
        Instant createdAt
) {}

