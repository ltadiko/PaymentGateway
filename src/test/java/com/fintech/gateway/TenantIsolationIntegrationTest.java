package com.fintech.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for multi-tenant isolation.
 *
 * <p>Verifies that tenant boundaries are enforced at the API layer:
 * <ul>
 *   <li>Tenant A cannot view Tenant B's payments</li>
 *   <li>Cross-tenant queries return 404 (no information leakage)</li>
 *   <li>Each tenant can access their own payments</li>
 * </ul>
 */
class TenantIsolationIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Tenant A cannot see Tenant B's payment → 404")
    @SuppressWarnings("unchecked")
    void tenantA_cannotSeeTenantB_payment() {
        String tokenA = getToken("tenant-iso-A", List.of("PAYMENT_SUBMIT", "PAYMENT_VIEW"));
        String tokenB = getToken("tenant-iso-B", List.of("PAYMENT_SUBMIT", "PAYMENT_VIEW"));

        // Tenant A creates a payment
        Map<String, Object> submitResult = submitPayment(tokenA, UUID.randomUUID().toString(), 25.00);
        String paymentId = (String) submitResult.get("paymentId");

        // Tenant B tries to view it → 404
        assertThatThrownBy(() -> getPaymentStatus(tokenB, paymentId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    @DisplayName("Tenant A can see their own payment → 200")
    @SuppressWarnings("unchecked")
    void tenantA_canSeeOwnPayment() {
        String tokenA = getToken("tenant-iso-own", List.of("PAYMENT_SUBMIT", "PAYMENT_VIEW"));

        Map<String, Object> submitResult = submitPayment(tokenA, UUID.randomUUID().toString(), 30.00);
        String paymentId = (String) submitResult.get("paymentId");

        // Same tenant queries → 200
        Map<String, Object> status = getPaymentStatus(tokenA, paymentId);
        assertThat(status.get("paymentId")).isEqualTo(paymentId);
    }

    @Test
    @DisplayName("Tenant A cannot see Tenant B's audit trail → 404")
    @SuppressWarnings("unchecked")
    void tenantA_cannotSeeTenantB_auditTrail() {
        String tokenA = getToken("tenant-iso-C", List.of("PAYMENT_SUBMIT", "PAYMENT_VIEW"));
        String tokenB = getToken("tenant-iso-D", List.of("PAYMENT_SUBMIT", "PAYMENT_VIEW"));

        Map<String, Object> submitResult = submitPayment(tokenA, UUID.randomUUID().toString(), 40.00);
        String paymentId = (String) submitResult.get("paymentId");

        // Tenant B tries to view audit trail → 404
        assertThatThrownBy(() -> getAuditTrail(tokenB, paymentId))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }
}
