package com.fintech.gateway.domain.port.out;

import com.fintech.gateway.domain.model.AuditEntry;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port for the immutable audit trail.
 *
 * <p>Every payment state transition is recorded as an append-only audit entry.
 * The audit trail provides a complete, chronologically ordered history of
 * each payment for compliance, dispute resolution, and forensic analysis.
 *
 * <p>This port only exposes {@code append} and {@code find} operations —
 * no update or delete. Immutability is enforced at both the application
 * and database layers.
 *
 * @see com.fintech.gateway.domain.model.AuditEntry
 */
public interface AuditTrailStore {

    /**
     * Appends a new audit entry to the trail.
     *
     * <p>Must be called within the same transaction as the corresponding
     * payment state change to guarantee consistency.
     *
     * @param entry the audit entry to persist
     */
    void append(AuditEntry entry);

    /**
     * Retrieves all audit entries for a payment, ordered by {@code createdAt ASC}.
     *
     * <p>Results are scoped by {@code tenantId} to enforce tenant isolation.
     *
     * @param paymentId the payment identifier
     * @param tenantId  the tenant who owns the payment
     * @return chronologically ordered list of audit entries (empty if none found)
     */
    List<AuditEntry> findByPaymentIdAndTenantId(UUID paymentId, String tenantId);
}

