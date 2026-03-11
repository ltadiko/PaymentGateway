package com.fintech.gateway.infrastructure.adapter.out.mock;

import com.fintech.gateway.domain.port.out.BankGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated acquiring bank gateway with variable latency and random outcomes.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Variable latency: 100-3000ms to mimic real-world bank processing</li>
 *   <li>Success rate: ~70% success, ~30% failure</li>
 *   <li>On success: returns a generated bank reference number</li>
 *   <li>On failure: returns a random rejection reason</li>
 * </ul>
 *
 * <p>In production, this would be replaced with a real bank API client
 * with circuit breaker and timeout policies.
 */
@Component
public class MockBankGatewayAdapter implements BankGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(MockBankGatewayAdapter.class);

    private static final double SUCCESS_RATE = 0.70;

    private static final String[] REJECTION_REASONS = {
            "Insufficient funds",
            "Account frozen",
            "Daily limit exceeded",
            "Invalid beneficiary account",
            "Bank system maintenance"
    };

    /**
     * Simulates bank processing with variable latency and random outcome.
     *
     * @param request the bank processing request
     * @return the processing result (success or failure)
     */
    @Override
    public BankProcessingResult process(BankProcessingRequest request) {
        log.info("Bank processing started: paymentId={}, amount={} {}",
                request.paymentId(), request.amount(), request.currency());

        // Simulate variable bank processing latency
        simulateLatency(100, 3000);

        boolean success = ThreadLocalRandom.current().nextDouble() < SUCCESS_RATE;

        if (success) {
            String bankRef = "BANK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.info("Bank approved: paymentId={}, bankRef={}", request.paymentId(), bankRef);
            return new BankProcessingResult(request.paymentId(), true, bankRef, null);
        } else {
            String reason = REJECTION_REASONS[
                    ThreadLocalRandom.current().nextInt(REJECTION_REASONS.length)];
            log.info("Bank rejected: paymentId={}, reason={}", request.paymentId(), reason);
            return new BankProcessingResult(request.paymentId(), false, null, reason);
        }
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

