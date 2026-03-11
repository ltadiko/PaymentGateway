package com.fintech.gateway.domain.model;

import com.fintech.gateway.domain.exception.InvalidStateTransitionException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain aggregate root representing a payment.
 * Encapsulates state transitions via {@link PaymentStatusTransitionEngine}.
 * No Spring/JPA annotations — pure domain object.
 */
public class Payment {

    private final UUID id;
    private final String tenantId;
    private final Money amount;
    private final String creditorAccount;
    private final String debtorAccount;
    private final String paymentMethod;
    private PaymentStatus status;
    private String statusDetail;
    private final Instant createdAt;
    private Instant updatedAt;

    private Payment(UUID id, String tenantId, Money amount, String creditorAccount,
                    String debtorAccount, String paymentMethod, PaymentStatus status,
                    String statusDetail, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.creditorAccount = Objects.requireNonNull(creditorAccount, "creditorAccount must not be null");
        this.debtorAccount = Objects.requireNonNull(debtorAccount, "debtorAccount must not be null");
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "paymentMethod must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.statusDetail = statusDetail;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Factory method to create a new payment in SUBMITTED state.
     */
    public static Payment initiate(String tenantId, Money amount, String creditorAccount,
                                   String debtorAccount, String paymentMethod) {
        Instant now = Instant.now();
        return new Payment(
                UUID.randomUUID(), tenantId, amount, creditorAccount, debtorAccount,
                paymentMethod, new PaymentStatus.Submitted(), null, now, now
        );
    }

    /**
     * Reconstitution constructor — used by persistence mappers to rebuild from stored data.
     * Does not validate state transitions; stored state is trusted.
     */
    public static Payment reconstitute(UUID id, String tenantId, Money amount, String creditorAccount,
                                       String debtorAccount, String paymentMethod, PaymentStatus status,
                                       String statusDetail, Instant createdAt, Instant updatedAt) {
        return new Payment(id, tenantId, amount, creditorAccount, debtorAccount,
                paymentMethod, status, statusDetail, createdAt, updatedAt);
    }

    /**
     * Applies a state transition event. Delegates to {@link PaymentStatusTransitionEngine}.
     *
     * @param event the transition event
     * @return the new status after transition
     * @throws InvalidStateTransitionException if the transition is not allowed
     */
    public PaymentStatus transitionTo(TransitionEvent event) {
        PaymentStatus newStatus = PaymentStatusTransitionEngine.transition(this.status, event);
        this.status = newStatus;
        this.statusDetail = newStatus.detail();
        this.updatedAt = Instant.now();
        return newStatus;
    }

    // ── Getters (no setters — state changes only via transitionTo) ──

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public Money getAmount() { return amount; }
    public String getCreditorAccount() { return creditorAccount; }
    public String getDebtorAccount() { return debtorAccount; }
    public String getPaymentMethod() { return paymentMethod; }
    public PaymentStatus getStatus() { return status; }
    public String getStatusDetail() { return statusDetail; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Payment payment)) return false;
        return id.equals(payment.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Payment{id=" + id + ", tenantId=" + tenantId + ", status=" + status.toDbValue() + "}";
    }
}

