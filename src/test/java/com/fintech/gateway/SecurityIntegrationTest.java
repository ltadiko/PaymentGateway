package com.fintech.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the security filter chain.
 *
 * <p>Verifies authentication and authorization behaviour at the HTTP level
 * using real JWT tokens and the full Spring Security filter chain.
 */
class SecurityIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("No token → 401 Unauthorized")
    void noToken_returns401() {
        assertThatThrownBy(() ->
                restClient()
                        .get()
                        .uri("/api/v1/payments/" + UUID.randomUUID())
                        .retrieve()
                        .body(Map.class)
        ).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    @DisplayName("Invalid token → 401 Unauthorized")
    void invalidToken_returns401() {
        assertThatThrownBy(() ->
                restClient()
                        .get()
                        .uri("/api/v1/payments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer this.is.not.a.valid.jwt.token")
                        .retrieve()
                        .body(Map.class)
        ).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    @DisplayName("Valid token, wrong role → 403 Forbidden")
    void validToken_wrongRole_returns403() {
        // Token with only PAYMENT_VIEW → cannot POST
        String viewOnlyToken = getToken("sec-tenant", List.of("PAYMENT_VIEW"));

        assertThatThrownBy(() ->
                restClient()
                        .post()
                        .uri("/api/v1/payments")
                        .headers(h -> h.addAll(authHeaders(viewOnlyToken, UUID.randomUUID().toString())))
                        .body(paymentRequest(10.00))
                        .retrieve()
                        .body(Map.class)
        ).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    @Test
    @DisplayName("Valid token, correct role → 202 Accepted")
    @SuppressWarnings("unchecked")
    void validToken_correctRole_returns202() {
        String token = getToken("sec-tenant-ok", List.of("PAYMENT_SUBMIT"));

        Map<String, Object> result = submitPayment(token, UUID.randomUUID().toString(), 10.00);
        assertThat(result).containsKey("paymentId");
    }

    @Test
    @DisplayName("Auth token endpoint is publicly accessible without JWT")
    @SuppressWarnings("unchecked")
    void authEndpoint_isPublic() {
        Map<String, Object> body = Map.of(
                "subject", "public-test",
                "tenantId", "any-tenant",
                "roles", List.of("PAYMENT_VIEW")
        );

        Map<String, Object> response = restClient()
                .post()
                .uri("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        assertThat(response).containsKey("token");
    }
}
