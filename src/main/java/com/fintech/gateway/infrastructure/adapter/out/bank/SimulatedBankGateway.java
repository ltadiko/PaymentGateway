package com.fintech.gateway.infrastructure.adapter.out.bank;

import com.fintech.gateway.domain.port.out.BankGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated acquiring bank gateway with variable latency and random outcomes.
 *
 * <p>Simulates a real-world bank integration with the following behaviour:
 * <ul>
 *   <li><strong>Variable latency:</strong> 100–3000ms to mimic real bank processing</li>
 *   <li><strong>Success rate:</strong> ~70% success, ~30% failure</li>
 *   <li><strong>On success:</strong> returns a generated bank reference ({@code BNK-xxxxxxxx})</li>
 *   <li><strong>On failure:</strong> returns a random rejection reason</li>
 * </ul>
 *
 * <p><strong>Deterministic test hooks</strong> for predictable integration tests:
 * <ul>
 *   <li>Amount {@code 11.11} → always succeeds (no random latency)</li>
 *   <li>Amount {@code 99.99} → always fails with "Insufficient funds"</li>
 * </ul>
 *
 * <p>In production, this would be replaced with a real bank API client
 * with circuit breaker, timeout, and retry policies.
 *
 * <p>This adapter is marked {@code @Primary} to take precedence over the
 * simpler {@link com.fintech.gateway.infrastructure.adapter.out.mock.MockBankGatewayAdapter}.
 *
 * @see BankGatewayPort
 */
@Component
@Primary
public class SimulatedBankGateway implements BankGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedBankGateway.class);

    /** Default success rate for random outcomes. */
    private static final double SUCCESS_RATE = 0.70;

    /** Amount that always triggers a successful bank response (test hook). */
    static final BigDecimal ALWAYS_SUCCESS_AMOUNT = new BigDecimal("11.11");

    /** Amount that always triggers a failed bank response (test hook). */
    static final BigDecimal ALWAYS_FAIL_AMOUNT = new BigDecimal("99.99");

    private static final String[] REJECTION_REASONS = {
            "Insufficient funds",
            "Account frozen",
            "Daily limit exceeded",
            "Invalid beneficiary account",
            "Bank system timeout",
            "Currency not supported"
    };

    /**
     * Simulates bank processing with variable latency and controllable outcomes.
     *
     * <p>For amounts matching the test hooks, the outcome is deterministic
     * with no simulated latency. For all other amounts, the outcome is
     * random with a 70% success rate and 100–3000ms latency.
     *
     * @param request the bank processing request
     * @return the processing result (success or failure)
     */
    @Override
    public BankProcessingResult process(BankProcessingRequest request) {
        log.info("Bank processing started: paymentId={}, amount={} {}",
                request.paymentId(), request.amount(), request.currency());

        // Deterministic test hooks (no latency for predictable tests)
        if (ALWAYS_SUCCESS_AMOUNT.compareTo(request.amount()) == 0) {
            String bankRef = "BNK-TEST-OK";
            log.info("Bank approved (test hook): paymentId={}, bankRef={}", request.paymentId(), bankRef);
            return new BankProcessingResult(request.paymentId(), true, bankRef, null);
        }

        if (ALWAYS_FAIL_AMOUNT.compareTo(request.amount()) == 0) {
            String reason = "Insufficient funds";
            log.warn("Bank rejected (test hook): paymentId={}, reason={}", request.paymentId(), reason);
            return new BankProcessingResult(request.paymentId(), false, null, reason);
        }

        // Real simulation: variable latency + random outcome
        simulateLatency(100, 3000);

        boolean success = ThreadLocalRandom.current().nextDouble() < SUCCESS_RATE;

        if (success) {
            String bankRef = "BNK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.info("Bank approved: paymentId={}, bankRef={}", request.paymentId(), bankRef);
            return new BankProcessingResult(request.paymentId(), true, bankRef, null);
        } else {
            String reason = REJECTION_REASONS[
                    ThreadLocalRandom.current().nextInt(REJECTION_REASONS.length)];
            log.warn("Bank rejected: paymentId={}, reason={}", request.paymentId(), reason);
            return new BankProcessingResult(request.paymentId(), false, null, reason);
        }
    }

    /**
     * Simulates network/processing latency. Protected for test overriding.
     *
     * @param minMs minimum latency in milliseconds
     * @param maxMs maximum latency in milliseconds
     */
    protected void simulateLatency(int minMs, int maxMs) {
        try {
            long delay = minMs + (long) (Math.random() * (maxMs - minMs));
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

