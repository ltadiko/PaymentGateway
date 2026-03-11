package com.fintech.gateway.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the immutable audit trail.
 *
 * <p>Each row records a single state transition in a payment's lifecycle.
 * This entity is append-only — no setters are exposed, and all fields are
 * set via the constructor to enforce immutability at the application layer.
 *
 * <p>The {@code paymentId} column is indexed to support efficient lookups
 * of a payment's complete audit history.
 */
@Entity
@Table(name = "audit_trail", indexes = {
        @Index(name = "idx_audit_payment_id", columnList = "paymentId"),
        @Index(name = "idx_audit_tenant_id", columnList = "tenantId")
})
public class AuditTrailEntity {

    /** Unique audit entry identifier. */
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** The payment this audit entry belongs to. */
    @Column(nullable = false, updatable = false)
    private UUID paymentId;

    /** Tenant identifier for multi-tenancy isolation. */
    @Column(nullable = false, updatable = false)
    private String tenantId;

    /** Payment status before this transition (null for initial creation). */
    @Column(updatable = false)
    private String previousStatus;

    /** Payment status after this transition. */
    @Column(nullable = false, updatable = false)
    private String newStatus;

    /** What triggered this transition (e.g., "PAYMENT_CREATED", "FRAUD_CHECK_PASSED"). */
    @Column(nullable = false, updatable = false)
    private String eventType;

    /** Additional structured context (JSON) — fraud score, bank reference, etc. */
    @Column(columnDefinition = "TEXT", updatable = false)
    private String metadata;

    /** The system component that performed the transition. */
    @Column(updatable = false)
    private String performedBy;

    /** When this transition occurred. */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Default constructor required by JPA. */
    protected AuditTrailEntity() {}

    /**
     * Constructs an AuditTrailEntity with all fields.
     *
     * @param id             unique audit entry identifier
     * @param paymentId      the payment this entry belongs to
     * @param tenantId       tenant identifier
     * @param previousStatus status before transition (nullable)
     * @param newStatus      status after transition
     * @param eventType      transition trigger
     * @param metadata       additional context (nullable)
     * @param performedBy    system component (nullable)
     * @param createdAt      transition timestamp
     */
    public AuditTrailEntity(UUID id, UUID paymentId, String tenantId, String previousStatus,
                            String newStatus, String eventType, String metadata,
                            String performedBy, Instant createdAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.tenantId = tenantId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.eventType = eventType;
        this.metadata = metadata;
        this.performedBy = performedBy;
        this.createdAt = createdAt;
    }

    // ── Getters (no setters — immutable) ──

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public String getTenantId() { return tenantId; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public String getEventType() { return eventType; }
    public String getMetadata() { return metadata; }
    public String getPerformedBy() { return performedBy; }
    public Instant getCreatedAt() { return createdAt; }
}

