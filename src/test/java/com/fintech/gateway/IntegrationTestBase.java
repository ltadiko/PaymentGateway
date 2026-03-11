package com.fintech.gateway;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Base class for full-stack integration tests.
 *
 * <p>Provides shared infrastructure for tests that exercise the complete
 * application stack: REST → Security → Business Logic → Kafka → Database.
 *
 * <p>Configuration:
 * <ul>
 *   <li>{@code @SpringBootTest(RANDOM_PORT)}: Starts the full application on a random port</li>
 *   <li>{@code @EmbeddedKafka}: Provides an in-memory Kafka broker</li>
 *   <li>{@code @ActiveProfiles("test")}: Uses H2 + test encryption keys</li>
 * </ul>
 *
 * <p>Subclasses get helper methods for:
 * <ul>
 *   <li>Obtaining JWT tokens for specific tenants and roles</li>
 *   <li>Building authenticated HTTP headers</li>
 *   <li>Submitting payments with idempotency keys</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @LocalServerPort
    protected int port;

    /**
     * Creates a {@link RestClient} pointed at the local test server.
     *
     * @return a configured RestClient
     */
    protected RestClient restClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    /**
     * Obtains a JWT token for the given tenant and roles by calling the
     * mock auth endpoint.
     *
     * @param tenantId the tenant identifier
     * @param roles    the roles to include in the token
     * @return the JWT token string
     */
    @SuppressWarnings("unchecked")
    protected String getToken(String tenantId, List<String> roles) {
        Map<String, Object> body = Map.of(
                "subject", "test-user",
                "tenantId", tenantId,
                "roles", roles
        );

        Map<String, Object> response = restClient()
                .post()
                .uri("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        return (String) response.get("token");
    }

    /**
     * Builds HTTP headers with JWT authentication and JSON content type.
     *
     * @param token          the JWT token
     * @param idempotencyKey the idempotency key (may be null for GET requests)
     * @return configured HTTP headers
     */
    protected HttpHeaders authHeaders(String token, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    /**
     * Builds HTTP headers with JWT authentication (no idempotency key).
     *
     * @param token the JWT token
     * @return configured HTTP headers
     */
    protected HttpHeaders authHeaders(String token) {
        return authHeaders(token, null);
    }

    /**
     * Submits a payment and returns the response as a Map.
     *
     * @param token          JWT token
     * @param idempotencyKey idempotency key
     * @param amount         payment amount
     * @return response body as Map
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> submitPayment(String token, String idempotencyKey, double amount) {
        return restClient()
                .post()
                .uri("/api/v1/payments")
                .headers(h -> h.addAll(authHeaders(token, idempotencyKey)))
                .body(paymentRequest(amount))
                .retrieve()
                .body(Map.class);
    }

    /**
     * Queries payment status.
     *
     * @param token     JWT token
     * @param paymentId payment identifier
     * @return response body as Map
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getPaymentStatus(String token, String paymentId) {
        return restClient()
                .get()
                .uri("/api/v1/payments/" + paymentId)
                .headers(h -> h.addAll(authHeaders(token)))
                .retrieve()
                .body(Map.class);
    }

    /**
     * Queries payment audit trail.
     *
     * @param token     JWT token
     * @param paymentId payment identifier
     * @return list of audit entries
     */
    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> getAuditTrail(String token, String paymentId) {
        return restClient()
                .get()
                .uri("/api/v1/payments/" + paymentId + "/audit")
                .headers(h -> h.addAll(authHeaders(token)))
                .retrieve()
                .body(List.class);
    }

    /**
     * Creates a standard payment request body for testing.
     *
     * @param amount the payment amount
     * @return the request body as a Map
     */
    protected Map<String, Object> paymentRequest(double amount) {
        return Map.of(
                "amount", amount,
                "currency", "USD",
                "creditorAccount", "NL91ABNA0417164300",
                "debtorAccount", "DE89370400440532013000",
                "paymentMethod", "BANK_TRANSFER"
        );
    }
}
