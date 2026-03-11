package com.fintech.gateway.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a payment in the database.
 *
 * <p>Maps to the {@code payments} table. Sensitive fields
 * ({@code creditorAccount}, {@code debtorAccount}) are transparently
 * encrypted/decrypted via the {@link com.fintech.gateway.infrastructure.crypto.AesAttributeConverter}.
 *
 * <p>The {@code tenantId} column is indexed to support efficient
 * tenant-scoped queries and enforce tenant isolation at the data layer.
 *
 * @see com.fintech.gateway.infrastructure.crypto.AesAttributeConverter
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_tenant_id", columnList = "tenantId")
})
public class PaymentJpaEntity {

    /** Unique payment identifier (UUID v4). */
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Tenant identifier for multi-tenancy isolation. */
    @Column(nullable = false, updatable = false)
    private String tenantId;

    /** Payment amount. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** ISO 4217 currency code (e.g., "USD", "EUR"). */
    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Creditor (recipient) account number — encrypted at rest.
     *
     * <p>Stored as Base64-encoded AES-GCM ciphertext in the database.
     * Transparently decrypted when read by JPA.
     */
    @Column(nullable = false)
    @Convert(converter = com.fintech.gateway.infrastructure.crypto.AesAttributeConverter.class)
    private String creditorAccount;

    /**
     * Debtor (sender) account number — encrypted at rest.
     *
     * <p>Stored as Base64-encoded AES-GCM ciphertext in the database.
     * Transparently decrypted when read by JPA.
     */
    @Column(nullable = false)
    @Convert(converter = com.fintech.gateway.infrastructure.crypto.AesAttributeConverter.class)
    private String debtorAccount;

    /** Payment method (e.g., "CARD", "BANK_TRANSFER", "WALLET"). */
    @Column(nullable = false)
    private String paymentMethod;

    /** Current payment status as a string (e.g., "SUBMITTED", "COMPLETED"). */
    @Column(nullable = false)
    private String status;

    /** Additional status detail (bank reference on success, reason on failure). */
    @Column
    private String statusDetail;

    /** Timestamp when the payment was created. */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Timestamp when the payment was last updated. */
    @Column(nullable = false)
    private Instant updatedAt;

    /** Default constructor required by JPA. */
    protected PaymentJpaEntity() {}

    /**
     * Constructs a PaymentJpaEntity with all fields.
     *
     * @param id               unique payment identifier
     * @param tenantId         tenant identifier
     * @param amount           payment amount
     * @param currency         ISO 4217 currency code
     * @param creditorAccount  creditor account (will be encrypted)
     * @param debtorAccount    debtor account (will be encrypted)
     * @param paymentMethod    payment method
     * @param status           current status string
     * @param statusDetail     additional detail (nullable)
     * @param createdAt        creation timestamp
     * @param updatedAt        last update timestamp
     */
    public PaymentJpaEntity(UUID id, String tenantId, BigDecimal amount, String currency,
                            String creditorAccount, String debtorAccount, String paymentMethod,
                            String status, String statusDetail, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.amount = amount;
        this.currency = currency;
        this.creditorAccount = creditorAccount;
        this.debtorAccount = debtorAccount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.statusDetail = statusDetail;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ── Getters ──

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getCreditorAccount() { return creditorAccount; }
    public String getDebtorAccount() { return debtorAccount; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getStatus() { return status; }
    public String getStatusDetail() { return statusDetail; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // ── Mutable fields (status transitions) ──

    /**
     * Updates the payment status and detail.
     *
     * @param status       the new status string
     * @param statusDetail the new detail (nullable)
     */
    public void updateStatus(String status, String statusDetail) {
        this.status = status;
        this.statusDetail = statusDetail;
        this.updatedAt = Instant.now();
    }
}

