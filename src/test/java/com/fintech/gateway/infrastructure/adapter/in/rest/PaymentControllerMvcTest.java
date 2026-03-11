package com.fintech.gateway.infrastructure.adapter.in.rest;

import com.fintech.gateway.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC integration tests for {@link PaymentController}.
 *
 * <p>Uses {@code @SpringBootTest} with {@code MockMvc} to verify:
 * <ul>
 *   <li>Authentication and authorization via JWT</li>
 *   <li>Idempotency handling (202 on new, 200 on duplicate)</li>
 *   <li>Request validation (missing fields, missing headers)</li>
 *   <li>Tenant isolation (404 for cross-tenant queries)</li>
 *   <li>Audit trail retrieval</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
class PaymentControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String tokenTenant001;
    private String tokenTenant002;
    private String tokenViewOnly;

    @BeforeEach
    void setUp() {
        tokenTenant001 = jwtTokenProvider.generateToken(
                "merchant-abc", "tenant-001",
                List.of("PAYMENT_SUBMIT", "PAYMENT_VIEW"));
        tokenTenant002 = jwtTokenProvider.generateToken(
                "merchant-xyz", "tenant-002",
                List.of("PAYMENT_SUBMIT", "PAYMENT_VIEW"));
        tokenViewOnly = jwtTokenProvider.generateToken(
                "viewer", "tenant-001",
                List.of("PAYMENT_VIEW"));
    }

    private String validPaymentBody() {
        return """
                {
                  "amount": 150.00,
                  "currency": "USD",
                  "creditorAccount": "NL91ABNA0417164300",
                  "debtorAccount": "DE89370400440532013000",
                  "paymentMethod": "BANK_TRANSFER"
                }
                """;
    }

    // ── Submit Payment ──

    @Nested
    @DisplayName("POST /api/v1/payments")
    class SubmitPayment {

        @Test
        @DisplayName("Valid request returns 202 Accepted with payment ID")
        void shouldReturn202ForValidPayment() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                            .header("Authorization", "Bearer " + tokenTenant001)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validPaymentBody()))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.paymentId", notNullValue()))
                    .andExpect(jsonPath("$.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.maskedCreditorAccount").value("****4300"))
                    .andExpect(jsonPath("$.maskedDebtorAccount").value("****3000"))
                    .andExpect(jsonPath("$.amount").value(150.00))
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.paymentMethod").value("BANK_TRANSFER"));
        }

        @Test
        @DisplayName("Duplicate Idempotency-Key returns 200 OK with same payment ID")
        void shouldReturn200ForDuplicateIdempotencyKey() throws Exception {
            String idempotencyKey = UUID.randomUUID().toString();

            // First submission → 202
            String firstResponse = mockMvc.perform(post("/api/v1/payments")
                            .header("Authorization", "Bearer " + tokenTenant001)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validPaymentBody()))
                    .andExpect(status().isAccepted())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // Second submission with same key → 200
            mockMvc.perform(post("/api/v1/payments")
                            .header("Authorization", "Bearer " + tokenTenant001)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validPaymentBody()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUBMITTED"));
        }

        @Test
        @DisplayName("Missing Idempotency-Key header returns 400")
        void shouldReturn400WithoutIdempotencyKey() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                            .header("Authorization", "Bearer " + tokenTenant001)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validPaymentBody()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Idempotency-Key header is required"));
        }

        @Test
        @DisplayName("Missing amount returns 400 with validation error")
        void shouldReturn400ForMissingAmount() throws Exception {
            String body = """
                    {
                      "currency": "USD",
                      "creditorAccount": "NL91ABNA0417164300",
                      "debtorAccount": "DE89370400440532013000",
                      "paymentMethod": "BANK_TRANSFER"
                    }
                    """;
            mockMvc.perform(post("/api/v1/payments")
                            .header("Authorization", "Bearer " + tokenTenant001)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Amount")));
        }

        @Test
        @DisplayName("Invalid currency (too short) returns 400")
        void shouldReturn400ForInvalidCurrency() throws Exception {
            String body = """
                    {
                      "amount": 100.00,
                      "currency": "US",
                      "creditorAccount": "NL91ABNA0417164300",
                      "debtorAccount": "DE89370400440532013000",
                      "paymentMethod": "BANK_TRANSFER"
                    }
                    """;
            mockMvc.perform(post("/api/v1/payments")
                            .header("Authorization", "Bearer " + tokenTenant001)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Without authentication returns 401")
        void shouldReturn401WithoutAuth() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validPaymentBody()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Without PAYMENT_SUBMIT role returns 403")
        void shouldReturn403WithoutSubmitRole() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                            .header("Authorization", "Bearer " + tokenViewOnly)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validPaymentBody()))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Query Payment ──

    @Nested
    @DisplayName("GET /api/v1/payments/{paymentId}")
    class QueryPayment {

        @Test
        @DisplayName("Existing payment returns 200 with masked accounts")
        void shouldReturn200ForExistingPayment() throws Exception {
            // First, create a payment
            String response = mockMvc.perform(post("/api/v1/payments")
                            .header("Authorization", "Bearer " + tokenTenant001)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validPaymentBody()))
                    .andExpect(status().isAccepted())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // Extract paymentId (simple parsing)
            String paymentId = extractPaymentId(response);

            // Query it
            mockMvc.perform(get("/api/v1/payments/" + paymentId)
                            .header("Authorization", "Bearer " + tokenTenant001))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId").value(paymentId))
                    .andExpect(jsonPath("$.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.maskedCreditorAccount").value("****4300"))
                    .andExpect(jsonPath("$.maskedDebtorAccount").value("****3000"));
        }

        @Test
        @DisplayName("Non-existent payment returns 404")
        void shouldReturn404ForNonExistentPayment() throws Exception {
            mockMvc.perform(get("/api/v1/payments/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + tokenTenant001))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"));
        }

        @Test
        @DisplayName("Tenant isolation: tenant-002 cannot see tenant-001 payment → 404")
        void shouldReturn404ForCrossTenantQuery() throws Exception {
            // Create payment as tenant-001
            String response = mockMvc.perform(post("/api/v1/payments")
                            .header("Authorization", "Bearer " + tokenTenant001)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validPaymentBody()))
                    .andExpect(status().isAccepted())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String paymentId = extractPaymentId(response);

            // Query as tenant-002 → should get 404 (not 200)
            mockMvc.perform(get("/api/v1/payments/" + paymentId)
                            .header("Authorization", "Bearer " + tokenTenant002))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Without authentication returns 401")
        void shouldReturn401WithoutAuth() throws Exception {
            mockMvc.perform(get("/api/v1/payments/" + UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── Audit Trail ──

    @Nested
    @DisplayName("GET /api/v1/payments/{paymentId}/audit")
    class AuditTrail {

        @Test
        @DisplayName("Returns audit trail with at least one PAYMENT_CREATED entry")
        void shouldReturnAuditTrail() throws Exception {
            // Create a payment
            String response = mockMvc.perform(post("/api/v1/payments")
                            .header("Authorization", "Bearer " + tokenTenant001)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validPaymentBody()))
                    .andExpect(status().isAccepted())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String paymentId = extractPaymentId(response);

            // Query audit trail
            mockMvc.perform(get("/api/v1/payments/" + paymentId + "/audit")
                            .header("Authorization", "Bearer " + tokenTenant001))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))))
                    .andExpect(jsonPath("$[0].newStatus").value("SUBMITTED"))
                    .andExpect(jsonPath("$[0].eventType").value("PAYMENT_CREATED"))
                    .andExpect(jsonPath("$[0].paymentId").value(paymentId));
        }

        @Test
        @DisplayName("Non-existent payment audit trail returns 404")
        void shouldReturn404ForNonExistentPaymentAudit() throws Exception {
            mockMvc.perform(get("/api/v1/payments/" + UUID.randomUUID() + "/audit")
                            .header("Authorization", "Bearer " + tokenTenant001))
                    .andExpect(status().isNotFound());
        }
    }

    // ── Helper ──

    /**
     * Extracts paymentId from a JSON response string.
     * Simple parsing without Jackson dependency in test.
     */
    private String extractPaymentId(String json) {
        int start = json.indexOf("\"paymentId\":\"") + "\"paymentId\":\"".length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}

