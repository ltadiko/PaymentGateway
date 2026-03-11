package com.fintech.gateway.infrastructure.adapter.out.bank;

import com.fintech.gateway.domain.port.out.BankGatewayPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SimulatedBankGateway}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Deterministic test hooks for predictable integration tests</li>
 *   <li>Random outcome distribution (success vs. failure)</li>
 *   <li>Bank reference format on success</li>
 *   <li>Rejection reasons on failure</li>
 * </ul>
 *
 * <p>No Spring context required — pure unit test.
 */
class SimulatedBankGatewayTest {

    private SimulatedBankGateway gateway;

    @BeforeEach
    void setUp() {
        // Use a subclass that skips latency for fast tests
        gateway = new SimulatedBankGateway() {
            @Override
            protected void simulateLatency(int minMs, int maxMs) {
                // No-op for unit tests
            }
        };
    }

    private BankGatewayPort.BankProcessingRequest createRequest(BigDecimal amount) {
        return new BankGatewayPort.BankProcessingRequest(
                UUID.randomUUID(), amount, "USD", "NL91ABNA0417164300");
    }

    @Nested
    @DisplayName("Deterministic test hooks")
    class TestHooks {

        @Test
        @DisplayName("Amount 11.11 → always succeeds")
        void shouldAlwaysSucceedForTestHookAmount() {
            BankGatewayPort.BankProcessingResult result =
                    gateway.process(createRequest(new BigDecimal("11.11")));

            assertThat(result.success()).isTrue();
            assertThat(result.bankReference()).isEqualTo("BNK-TEST-OK");
            assertThat(result.reason()).isNull();
        }

        @Test
        @DisplayName("Amount 99.99 → always fails with 'Insufficient funds'")
        void shouldAlwaysFailForTestHookAmount() {
            BankGatewayPort.BankProcessingResult result =
                    gateway.process(createRequest(new BigDecimal("99.99")));

            assertThat(result.success()).isFalse();
            assertThat(result.bankReference()).isNull();
            assertThat(result.reason()).isEqualTo("Insufficient funds");
        }

        @RepeatedTest(10)
        @DisplayName("Amount 11.11 → consistent across multiple calls")
        void shouldBeConsistentlySuccessful() {
            BankGatewayPort.BankProcessingResult result =
                    gateway.process(createRequest(new BigDecimal("11.11")));

            assertThat(result.success()).isTrue();
        }

        @RepeatedTest(10)
        @DisplayName("Amount 99.99 → consistent across multiple calls")
        void shouldBeConsistentlyFailing() {
            BankGatewayPort.BankProcessingResult result =
                    gateway.process(createRequest(new BigDecimal("99.99")));

            assertThat(result.success()).isFalse();
        }
    }

    @Nested
    @DisplayName("Random outcomes")
    class RandomOutcomes {

        @Test
        @DisplayName("Random amount produces both success and failure outcomes")
        void shouldProduceBothOutcomes() {
            AtomicInteger successes = new AtomicInteger(0);
            AtomicInteger failures = new AtomicInteger(0);

            // Run 100 times — with 70% success rate, both outcomes should occur
            for (int i = 0; i < 100; i++) {
                BankGatewayPort.BankProcessingResult result =
                        gateway.process(createRequest(new BigDecimal("500.00")));
                if (result.success()) {
                    successes.incrementAndGet();
                } else {
                    failures.incrementAndGet();
                }
            }

            assertThat(successes.get())
                    .as("Should have some successes in 100 attempts")
                    .isGreaterThan(0);
            assertThat(failures.get())
                    .as("Should have some failures in 100 attempts")
                    .isGreaterThan(0);
        }

        @Test
        @DisplayName("Success result has bank reference and no reason")
        void successResultFormat() {
            // Use test hook for deterministic success
            BankGatewayPort.BankProcessingResult result =
                    gateway.process(createRequest(new BigDecimal("11.11")));

            assertThat(result.paymentId()).isNotNull();
            assertThat(result.success()).isTrue();
            assertThat(result.bankReference()).startsWith("BNK-");
            assertThat(result.reason()).isNull();
        }

        @Test
        @DisplayName("Failure result has reason and no bank reference")
        void failureResultFormat() {
            // Use test hook for deterministic failure
            BankGatewayPort.BankProcessingResult result =
                    gateway.process(createRequest(new BigDecimal("99.99")));

            assertThat(result.paymentId()).isNotNull();
            assertThat(result.success()).isFalse();
            assertThat(result.bankReference()).isNull();
            assertThat(result.reason()).isNotBlank();
        }
    }
}

