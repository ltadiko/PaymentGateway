package com.fintech.gateway.infrastructure.adapter.out.mock;

import com.fintech.gateway.domain.port.out.FraudAssessmentPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Mock implementation of the fraud assessment external service.
 *
 * <p>Simulates a fraud assessment with deterministic behaviour:
 * <ul>
 *   <li>Payments with amount &lt; 10,000 are approved (low risk score)</li>
 *   <li>Payments with amount &ge; 10,000 are rejected (high risk score)</li>
 * </ul>
 *
 * <p>This adapter will be replaced by a real OpenAPI client in production.
 * For the assignment scope, it provides predictable results for testing.
 */
@Component
public class MockFraudAssessmentAdapter implements FraudAssessmentPort {

    private static final Logger log = LoggerFactory.getLogger(MockFraudAssessmentAdapter.class);

    private static final java.math.BigDecimal FRAUD_THRESHOLD = new java.math.BigDecimal("10000.00");

    /**
     * Assesses a payment for fraud risk.
     *
     * <p>Uses a simple amount-based rule: amounts below the threshold are approved
     * with a low score; amounts at or above the threshold are rejected.
     *
     * @param request the assessment request
     * @return the assessment result
     */
    @Override
    public FraudAssessmentResult assess(FraudAssessmentRequest request) {
        log.info("Fraud assessment requested: paymentId={}, amount={} {}",
                request.paymentId(), request.amount(), request.currency());

        // Simulate processing latency (50-200ms)
        simulateLatency(50, 200);

        boolean approved = request.amount().compareTo(FRAUD_THRESHOLD) < 0;
        int score = approved ? 15 : 85;
        String reason = approved ? null : "Amount exceeds fraud threshold";

        log.info("Fraud assessment result: paymentId={}, approved={}, score={}",
                request.paymentId(), approved, score);

        return new FraudAssessmentResult(
                request.paymentId(), approved, score, reason, Instant.now());
    }

    private void simulateLatency(int minMs, int maxMs) {
        try {
            long delay = minMs + (long) (Math.random() * (maxMs - minMs));
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

