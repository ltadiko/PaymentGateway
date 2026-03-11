package com.fintech.gateway.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing the current state of a payment.
 *
 * <p><strong>Security:</strong> Account numbers are always masked in this response.
 * The full account number is never returned to the client. Masking is applied
 * at the service layer before constructing this record.
 *
 * <p>Example masked values:
 * <ul>
 *   <li>{@code "NL91ABNA0417164300"} → {@code "****4300"}</li>
 *   <li>{@code "DE89370400440532013000"} → {@code "****3000"}</li>
 * </ul>
 *
 * @param paymentId             unique payment identifier (tracking ID)
 * @param status                current status (e.g., "SUBMITTED", "COMPLETED", "FAILED")
 * @param statusDetail          additional detail (bank reference on success, reason on failure)
 * @param maskedCreditorAccount masked creditor account number
 * @param maskedDebtorAccount   masked debtor account number
 * @param amount                payment amount
 * @param currency              ISO 4217 currency code
 * @param paymentMethod         payment method used
 * @param createdAt             when the payment was initiated
 * @param updatedAt             when the payment was last updated
 */
public record PaymentResponse(
        UUID paymentId,
        String status,
        String statusDetail,
        String maskedCreditorAccount,
        String maskedDebtorAccount,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Indicates whether this response was returned from the idempotency cache
     * (i.e., the same idempotency key was used before).
     *
     * <p>When {@code true}, the REST controller should return HTTP 200 instead of 202.
     */
    private static final String DUPLICATE_MARKER = "__duplicate__";

    /**
     * Creates a new PaymentResponse marked as a duplicate (idempotent replay).
     *
     * @return a copy of this response with duplicate marker in statusDetail
     */
    public PaymentResponse asDuplicate() {
        return new PaymentResponse(
                paymentId, status, DUPLICATE_MARKER,
                maskedCreditorAccount, maskedDebtorAccount,
                amount, currency, paymentMethod, createdAt, updatedAt
        );
    }

    /**
     * Returns {@code true} if this response is an idempotent duplicate.
     *
     * @return whether this response was returned from the idempotency cache
     */
    public boolean isDuplicate() {
        return DUPLICATE_MARKER.equals(statusDetail);
    }
}

