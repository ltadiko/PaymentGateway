package com.fintech.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Full end-to-end pipeline integration tests.
 *
 * <p>These tests exercise the complete payment lifecycle:
 * <pre>
 * REST API → Security → Ingestion → Kafka → Fraud Check → Kafka → Bank → Database
 * </pre>
 *
 * <p>Uses deterministic test hooks in the bank simulator:
 * <ul>
 *   <li>Amount {@code 11.11} → bank always succeeds → payment COMPLETED</li>
 *   <li>Amount {@code 99.99} → bank always fails → payment FAILED</li>
 *   <li>Amount {@code 15000} → fraud rejected → payment FRAUD_REJECTED</li>
 * </ul>
 *
 * <p>Uses {@link org.awaitility.Awaitility} to poll payment status since
 * processing is asynchronous through Kafka.
 */
class FullPipelineIntegrationTest extends IntegrationTestBase {

    private String token;

    @BeforeEach
    void setUp() {
        token = getToken("pipeline-tenant", List.of("PAYMENT_SUBMIT", "PAYMENT_VIEW"));
    }

    @Test
    @DisplayName("Happy path: low amount → fraud approved → bank success → COMPLETED")
    @SuppressWarnings("unchecked")
    void happyPath_paymentCompletesSuccessfully() {
        // Submit payment with deterministic success amount
        Map<String, Object> submitResult = submitPayment(token, UUID.randomUUID().toString(), 11.11);
        String paymentId = (String) submitResult.get("paymentId");
        assertThat(paymentId).isNotNull();

        // Wait for async processing to complete
        await().atMost(30, SECONDS).pollInterval(1, SECONDS).untilAsserted(() -> {
            Map<String, Object> status = getPaymentStatus(token, paymentId);
            assertThat(status.get("status")).isEqualTo("COMPLETED");
        });

        // Verify audit trail has all expected transitions
        List<Map<String, Object>> auditEntries = getAuditTrail(token, paymentId);
        List<String> statuses = auditEntries.stream()
                .map(e -> (String) e.get("newStatus"))
                .toList();

        assertThat(statuses).containsExactly(
                "SUBMITTED",
                "FRAUD_CHECK_IN_PROGRESS",
                "FRAUD_APPROVED",
                "PROCESSING_BY_BANK",
                "COMPLETED"
        );
    }

    @Test
    @DisplayName("Fraud rejection: high amount → fraud rejected → pipeline stops")
    @SuppressWarnings("unchecked")
    void fraudRejection_pipelineStopsAfterFraud() {
        Map<String, Object> submitResult = submitPayment(token, UUID.randomUUID().toString(), 15000.00);
        String paymentId = (String) submitResult.get("paymentId");

        // Wait for fraud rejection
        await().atMost(30, SECONDS).pollInterval(1, SECONDS).untilAsserted(() -> {
            Map<String, Object> status = getPaymentStatus(token, paymentId);
            assertThat(status.get("status")).isEqualTo("FRAUD_REJECTED");
        });

        // Verify audit trail — no bank processing step
        List<Map<String, Object>> auditEntries = getAuditTrail(token, paymentId);
        List<String> statuses = auditEntries.stream()
                .map(e -> (String) e.get("newStatus"))
                .toList();

        assertThat(statuses).containsExactly(
                "SUBMITTED",
                "FRAUD_CHECK_IN_PROGRESS",
                "FRAUD_REJECTED"
        );
    }

    @Test
    @DisplayName("Bank failure: deterministic fail amount → fraud approved → bank fails → FAILED")
    @SuppressWarnings("unchecked")
    void bankFailure_paymentFails() {
        Map<String, Object> submitResult = submitPayment(token, UUID.randomUUID().toString(), 99.99);
        String paymentId = (String) submitResult.get("paymentId");

        // Wait for bank failure
        await().atMost(30, SECONDS).pollInterval(1, SECONDS).untilAsserted(() -> {
            Map<String, Object> status = getPaymentStatus(token, paymentId);
            assertThat(status.get("status")).isEqualTo("FAILED");
        });

        // Verify full audit trail including bank processing
        List<Map<String, Object>> auditEntries = getAuditTrail(token, paymentId);
        List<String> statuses = auditEntries.stream()
                .map(e -> (String) e.get("newStatus"))
                .toList();

        assertThat(statuses).containsExactly(
                "SUBMITTED",
                "FRAUD_CHECK_IN_PROGRESS",
                "FRAUD_APPROVED",
                "PROCESSING_BY_BANK",
                "FAILED"
        );
    }
}
