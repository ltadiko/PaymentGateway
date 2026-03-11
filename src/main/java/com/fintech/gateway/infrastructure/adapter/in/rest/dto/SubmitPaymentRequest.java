package com.fintech.gateway.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for submitting a new payment.
 *
 * <p>Bean Validation annotations enforce input constraints at the REST layer
 * before the data reaches the application service. This keeps the domain
 * model free of framework-specific validation concerns.
 *
 * <p>Example:
 * <pre>{@code
 * {
 *   "amount": 150.00,
 *   "currency": "USD",
 *   "creditorAccount": "NL91ABNA0417164300",
 *   "debtorAccount": "DE89370400440532013000",
 *   "paymentMethod": "BANK_TRANSFER"
 * }
 * }</pre>
 *
 * @param amount           payment amount (must be positive)
 * @param currency         ISO 4217 currency code (exactly 3 characters)
 * @param creditorAccount  the creditor's (recipient's) account number
 * @param debtorAccount    the debtor's (sender's) account number
 * @param paymentMethod    payment method (e.g., "CARD", "BANK_TRANSFER", "WALLET")
 */
public record SubmitPaymentRequest(

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO 4217 code")
        String currency,

        @NotBlank(message = "Creditor account is required")
        String creditorAccount,

        @NotBlank(message = "Debtor account is required")
        String debtorAccount,

        @NotBlank(message = "Payment method is required")
        String paymentMethod
) {}

