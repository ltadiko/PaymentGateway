package com.fintech.gateway.domain.port.in;

import com.fintech.gateway.application.dto.AuditEntryResponse;
import com.fintech.gateway.application.dto.PaymentResponse;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port for querying payment status and audit trail.
 *
 * <p>All queries are tenant-scoped: a client can only retrieve payments
 * that belong to their own tenant. Attempting to access another tenant's
 * payment results in a {@link com.fintech.gateway.domain.exception.PaymentNotFoundException}.
 *
 * <p>Sensitive fields (account numbers) are masked in the response — raw
 * PCI data is never exposed through this port.
 *
 * @see com.fintech.gateway.application.dto.PaymentResponse
 * @see com.fintech.gateway.application.dto.AuditEntryResponse
 */
public interface GetPaymentUseCase {

    /**
     * Retrieves the current state of a payment.
     *
     * @param paymentId the unique payment identifier
     * @param tenantId  the tenant making the request (from JWT claims)
     * @return the payment status with masked sensitive fields
     * @throws com.fintech.gateway.domain.exception.PaymentNotFoundException
     *         if the payment does not exist or does not belong to the given tenant
     */
    PaymentResponse getPayment(UUID paymentId, String tenantId);

    /**
     * Retrieves the full audit trail for a payment, ordered chronologically.
     *
     * <p>Each entry represents a single state transition in the payment lifecycle.
     * The list is ordered by {@code createdAt ASC}, providing a complete history
     * from ingestion to final resolution.
     *
     * @param paymentId the unique payment identifier
     * @param tenantId  the tenant making the request (from JWT claims)
     * @return an ordered list of audit trail entries
     * @throws com.fintech.gateway.domain.exception.PaymentNotFoundException
     *         if the payment does not exist or does not belong to the given tenant
     */
    List<AuditEntryResponse> getAuditTrail(UUID paymentId, String tenantId);
}

