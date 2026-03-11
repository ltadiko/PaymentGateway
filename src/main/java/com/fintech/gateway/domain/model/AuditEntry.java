package com.fintech.gateway.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable value object representing a single audit trail entry.
 * Append-only — once created, never modified.
 */
public record AuditEntry(
        UUID id,
        UUID paymentId,
        String tenantId,
        String previousStatus,
        String newStatus,
        String eventType,
        String metadata,
        String performedBy,
        Instant createdAt
) {

    public AuditEntry {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (paymentId == null) throw new IllegalArgumentException("paymentId must not be null");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be null or blank");
        if (newStatus == null || newStatus.isBlank()) throw new IllegalArgumentException("newStatus must not be null or blank");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType must not be null or blank");
        if (createdAt == null) throw new IllegalArgumentException("createdAt must not be null");
        // previousStatus can be null (for initial SUBMITTED state)
        // metadata can be null
        // performedBy can be null (for system-triggered events)
    }

    /**
     * Factory method for creating an audit entry for a state transition.
     */
    public static AuditEntry of(UUID paymentId, String tenantId, String previousStatus,
                                String newStatus, String eventType, String metadata,
                                String performedBy) {
        return new AuditEntry(
                UUID.randomUUID(), paymentId, tenantId, previousStatus,
                newStatus, eventType, metadata, performedBy, Instant.now()
        );
    }
}

