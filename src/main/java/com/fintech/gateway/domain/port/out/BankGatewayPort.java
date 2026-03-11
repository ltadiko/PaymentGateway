package com.fintech.gateway.domain.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound port for processing payments through the acquiring bank.
 *
 * <p>This port abstracts the downstream bank integration. The implementation
 * simulates a real bank gateway with:
 * <ul>
 *   <li>Variable latency (100–3000ms) to mimic real-world conditions</li>
 *   <li>Random success/failure outcomes (70% success, 30% failure)</li>
 *   <li>Deterministic test hooks for predictable integration tests</li>
 * </ul>
 *
 * <p>In a production system, this would be replaced with a real bank API
 * integration, typically with circuit breaker and timeout policies.
 *
 * @see BankProcessingRequest
 * @see BankProcessingResult
 */
public interface BankGatewayPort {

    /**
     * Processes a payment through the acquiring bank.
     *
     * <p>This call is blocking and may take up to several seconds due to
     * the bank's processing time. The caller should handle timeouts
     * and failures gracefully.
     *
     * @param request the bank processing request
     * @return the processing result indicating success or failure
     * @throws RuntimeException if the bank is unreachable or returns an unexpected error
     */
    BankProcessingResult process(BankProcessingRequest request);

    /**
     * Request payload for bank processing.
     *
     * @param paymentId       unique payment identifier
     * @param amount          payment amount
     * @param currency        ISO 4217 currency code
     * @param creditorAccount the creditor's (recipient's) account number
     */
    record BankProcessingRequest(
            UUID paymentId,
            BigDecimal amount,
            String currency,
            String creditorAccount
    ) {}

    /**
     * Response payload from the bank gateway.
     *
     * @param paymentId     the processed payment identifier
     * @param success       {@code true} if the bank approved the transaction
     * @param bankReference the bank's reference number (non-null on success)
     * @param reason        rejection reason from the bank (non-null on failure)
     */
    record BankProcessingResult(
            UUID paymentId,
            boolean success,
            String bankReference,
            String reason
    ) {}
}

