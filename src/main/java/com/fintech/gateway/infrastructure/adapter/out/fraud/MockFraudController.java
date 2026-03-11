package com.fintech.gateway.infrastructure.adapter.out.fraud;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * In-app mock of the external fraud assessment microservice.
 *
 * <p>This controller simulates a separate fraud service running inside the
 * same application. The {@link OpenApiFraudClient} calls this endpoint
 * via HTTP, exercising the full OpenAPI contract end-to-end.
 *
 * <p><strong>Scoring rules (deterministic for testability):</strong>
 * <ul>
 *   <li>Amount &gt; 10,000 → score 85, rejected ("High risk transaction amount")</li>
 *   <li>Amount &gt; 5,000 → score 55, approved (borderline)</li>
 *   <li>Otherwise → score 15, approved (low risk)</li>
 * </ul>
 *
 * <p>Simulates 50–200ms processing latency to mimic real-world behaviour.
 *
 * @see OpenApiFraudClient
 */
@RestController
@RequestMapping("/api/v1/fraud")
@Tag(name = "Fraud", description = "Mock fraud assessment service (internal — called by the Kafka consumer pipeline)")
@SecurityRequirements // No auth required — internal endpoint
public class MockFraudController {

    private static final Logger log = LoggerFactory.getLogger(MockFraudController.class);

    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("10000.00");
    private static final BigDecimal MEDIUM_RISK_THRESHOLD = new BigDecimal("5000.00");

    /**
     * Assesses a payment for fraud risk per the OpenAPI specification.
     *
     * @param request the fraud assessment request
     * @return the fraud assessment response with score and decision
     */
    @PostMapping("/assess")
    @Operation(
            summary = "Assess payment for fraud",
            description = "Scores a payment and returns approved/rejected. Amount ≥ 10,000 → rejected (score 85); 5,000-9,999 → borderline (score 55); otherwise → approved (score 15)."
    )
    public ResponseEntity<FraudApiResponse> assess(@RequestBody FraudApiRequest request) {
        log.info("Fraud API called: paymentId={}, amount={} {}",
                request.paymentId(), request.amount(), request.currency());

        simulateLatency(50, 200);

        int score;
        boolean approved;
        String reason;

        if (request.amount().compareTo(HIGH_RISK_THRESHOLD) >= 0) {
            score = 85;
            approved = false;
            reason = "High risk transaction amount";
        } else if (request.amount().compareTo(MEDIUM_RISK_THRESHOLD) >= 0) {
            score = 55;
            approved = true;
            reason = null;
        } else {
            score = 15;
            approved = true;
            reason = null;
        }

        log.info("Fraud API response: paymentId={}, approved={}, score={}",
                request.paymentId(), approved, score);

        return ResponseEntity.ok(new FraudApiResponse(
                request.paymentId(), approved, score, reason, Instant.now()));
    }

    private void simulateLatency(int minMs, int maxMs) {
        try {
            long delay = minMs + (long) (Math.random() * (maxMs - minMs));
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Request payload matching the OpenAPI {@code FraudAssessmentRequest} schema.
     *
     * @param paymentId     unique payment identifier
     * @param amount        payment amount
     * @param currency      ISO 4217 currency code
     * @param merchantId    tenant/merchant identifier
     * @param paymentMethod payment method
     */
    public record FraudApiRequest(
            UUID paymentId,
            BigDecimal amount,
            String currency,
            String merchantId,
            String paymentMethod
    ) {}

    /**
     * Response payload matching the OpenAPI {@code FraudAssessmentResponse} schema.
     *
     * @param paymentId  the assessed payment identifier
     * @param approved   whether the payment passed fraud checks
     * @param fraudScore risk score 0–100
     * @param reason     rejection reason (null when approved)
     * @param assessedAt timestamp of the assessment
     */
    public record FraudApiResponse(
            UUID paymentId,
            boolean approved,
            int fraudScore,
            String reason,
            Instant assessedAt
    ) {}
}

