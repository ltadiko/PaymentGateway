package com.fintech.gateway.domain.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbound port for invoking the external fraud assessment service.
 *
 * <p>This port follows the Strategy pattern — the implementation can be:
 * <ul>
 *   <li>A real HTTP client calling an external microservice (via OpenAPI spec)</li>
 *   <li>A mock adapter returning deterministic results for testing</li>
 * </ul>
 *
 * <p>The contract is defined by the OpenAPI specification at
 * {@code src/main/resources/openapi/fraud-assessment-api.yaml}.
 *
 * <p><strong>Security:</strong> Only non-sensitive fields are sent to the fraud
 * service. Raw card/account numbers are never included in the request.
 *
 * @see FraudAssessmentRequest
 * @see FraudAssessmentResult
 */
public interface FraudAssessmentPort {

    /**
     * Assesses a payment for fraud risk.
     *
     * <p>The fraud service evaluates the payment and returns a risk score
     * (0–100) along with an approval/rejection decision. Implementations
     * may have variable latency.
     *
     * @param request the assessment request containing payment details
     * @return the assessment result with score and decision
     * @throws RuntimeException if the fraud service is unavailable or returns an error
     */
    FraudAssessmentResult assess(FraudAssessmentRequest request);

    /**
     * Request payload for fraud assessment.
     * Contains only non-sensitive payment metadata.
     *
     * @param paymentId     unique payment identifier
     * @param amount        payment amount
     * @param currency      ISO 4217 currency code (e.g., "USD")
     * @param merchantId    tenant/merchant identifier
     * @param paymentMethod payment method (e.g., "CARD", "BANK_TRANSFER")
     */
    record FraudAssessmentRequest(
            UUID paymentId,
            BigDecimal amount,
            String currency,
            String merchantId,
            String paymentMethod
    ) {}

    /**
     * Response payload from the fraud assessment service.
     *
     * @param paymentId  the assessed payment identifier
     * @param approved   {@code true} if the payment passed fraud checks
     * @param fraudScore risk score from 0 (no risk) to 100 (definite fraud)
     * @param reason     human-readable rejection reason (null when approved)
     * @param assessedAt timestamp of the assessment
     */
    record FraudAssessmentResult(
            UUID paymentId,
            boolean approved,
            int fraudScore,
            String reason,
            Instant assessedAt
    ) {}
}

