package com.fintech.gateway.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for storing idempotency keys.
 *
 * <p>Each row represents a previously processed payment submission. The unique
 * constraint on {@code (tenant_id, idempotency_key)} prevents duplicate processing
 * at the database level, even under race conditions.
 *
 * <p>The stored {@code responseBody} is returned on duplicate submissions, ensuring
 * the client receives the same response as the original request.
 */
@Entity
@Table(name = "idempotency_keys", uniqueConstraints = {
        @UniqueConstraint(name = "uk_idem_tenant_key",
                columnNames = {"tenantId", "idempotencyKey"})
}, indexes = {
        @Index(name = "idx_idem_created_at", columnList = "createdAt")
})
public class IdempotencyKeyEntity {

    /** Unique row identifier. */
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Tenant identifier — part of the composite uniqueness constraint. */
    @Column(nullable = false, updatable = false)
    private String tenantId;

    /** Client-provided idempotency key — part of the composite uniqueness constraint. */
    @Column(nullable = false, updatable = false)
    private String idempotencyKey;

    /** The payment ID created for this idempotency key. */
    @Column(nullable = false, updatable = false)
    private UUID paymentId;

    /** The HTTP status code of the original response (e.g., 202). */
    @Column(nullable = false, updatable = false)
    private int responseStatus;

    /** The serialised JSON response body of the original response. */
    @Column(columnDefinition = "TEXT", nullable = false, updatable = false)
    private String responseBody;

    /** When this idempotency key was created — used for TTL cleanup. */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Default constructor required by JPA. */
    protected IdempotencyKeyEntity() {}

    /**
     * Constructs an IdempotencyKeyEntity with all fields.
     *
     * @param id             unique row identifier
     * @param tenantId       tenant identifier
     * @param idempotencyKey client-provided key
     * @param paymentId      the associated payment ID
     * @param responseStatus HTTP status of the original response
     * @param responseBody   serialised JSON response body
     * @param createdAt      creation timestamp
     */
    public IdempotencyKeyEntity(UUID id, String tenantId, String idempotencyKey,
                                UUID paymentId, int responseStatus, String responseBody,
                                Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.idempotencyKey = idempotencyKey;
        this.paymentId = paymentId;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
    }

    // ── Getters ──

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getPaymentId() { return paymentId; }
    public int getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public Instant getCreatedAt() { return createdAt; }
}

