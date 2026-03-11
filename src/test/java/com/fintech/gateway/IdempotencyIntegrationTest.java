package com.fintech.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for strict idempotency behaviour.
 *
 * <p>Verifies that the payment ingestion endpoint correctly handles
 * duplicate requests with the same idempotency key, preventing
 * duplicate charges in case of client retries or network partitions.
 */
class IdempotencyIntegrationTest extends IntegrationTestBase {

    private String token;

    @BeforeEach
    void setUp() {
        token = getToken("idempotency-tenant", List.of("PAYMENT_SUBMIT", "PAYMENT_VIEW"));
    }

    @Test
    @DisplayName("Duplicate request with same idempotency key returns same paymentId")
    @SuppressWarnings("unchecked")
    void duplicateRequest_returnsSamePaymentId() {
        String idempotencyKey = UUID.randomUUID().toString();

        // First submission
        Map<String, Object> first = submitPayment(token, idempotencyKey, 50.00);
        String paymentId1 = (String) first.get("paymentId");

        // Second submission with SAME key → idempotent
        Map<String, Object> second = submitPayment(token, idempotencyKey, 50.00);
        String paymentId2 = (String) second.get("paymentId");

        // Same payment returned
        assertThat(paymentId1).isEqualTo(paymentId2);
    }

    @Test
    @DisplayName("Different idempotency keys create different payments")
    @SuppressWarnings("unchecked")
    void differentKey_createsDifferentPayment() {
        Map<String, Object> first = submitPayment(token, UUID.randomUUID().toString(), 75.00);
        String paymentId1 = (String) first.get("paymentId");

        Map<String, Object> second = submitPayment(token, UUID.randomUUID().toString(), 75.00);
        String paymentId2 = (String) second.get("paymentId");

        assertThat(paymentId1).isNotEqualTo(paymentId2);
    }

    @Test
    @DisplayName("Same idempotency key for different tenants creates separate payments")
    @SuppressWarnings("unchecked")
    void sameKeyDifferentTenants_createsSeparatePayments() {
        String sharedKey = UUID.randomUUID().toString();

        String tokenA = getToken("tenant-A-idemp", List.of("PAYMENT_SUBMIT"));
        String tokenB = getToken("tenant-B-idemp", List.of("PAYMENT_SUBMIT"));

        Map<String, Object> responseA = submitPayment(tokenA, sharedKey, 100.00);
        String paymentIdA = (String) responseA.get("paymentId");

        Map<String, Object> responseB = submitPayment(tokenB, sharedKey, 100.00);
        String paymentIdB = (String) responseB.get("paymentId");

        // Same key but different tenants → different payments
        assertThat(paymentIdA).isNotEqualTo(paymentIdB);
    }
}
