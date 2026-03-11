package com.fintech.gateway.application.dto;

import java.math.BigDecimal;

/**
 * Command object for submitting a new payment.
 *
 * <p>This record carries all data needed to initiate a payment, including
 * the tenant context and idempotency key extracted from the HTTP request
 * by the REST adapter.
 *
 * <p>Validation of individual fields (e.g., amount > 0, valid currency)
 * is performed in the REST layer via Bean Validation annotations on the
 * request DTO. This command assumes the data has already been validated.
 *
 * @param tenantId        the tenant initiating the payment (from JWT claims)
 * @param idempotencyKey  client-provided UUID for deduplication
 * @param amount          payment amount (must be positive)
 * @param currency        ISO 4217 currency code (e.g., "USD", "EUR")
 * @param creditorAccount the creditor's (recipient's) account number
 * @param debtorAccount   the debtor's (sender's) account number
 * @param paymentMethod   payment method (e.g., "CARD", "BANK_TRANSFER", "WALLET")
 */
public record SubmitPaymentCommand(
        String tenantId,
        String idempotencyKey,
        BigDecimal amount,
        String currency,
        String creditorAccount,
        String debtorAccount,
        String paymentMethod
) {}

