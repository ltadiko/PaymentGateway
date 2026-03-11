package com.fintech.gateway.infrastructure.adapter.in.rest;

import com.fintech.gateway.infrastructure.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC integration tests for {@link AuthController}.
 *
 * <p>Uses {@code @SpringBootTest} with {@code MockMvc} to verify the full
 * Spring Security filter chain, JWT issuance, and endpoint protection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
class AuthControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Nested
    @DisplayName("POST /api/v1/auth/token")
    class IssueToken {

        @Test
        @DisplayName("Returns 200 with valid JWT token")
        void shouldReturnTokenForValidRequest() throws Exception {
            mockMvc.perform(post("/api/v1/auth/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "subject": "merchant-abc",
                                      "tenantId": "tenant-001",
                                      "roles": ["PAYMENT_SUBMIT", "PAYMENT_VIEW"]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token", notNullValue()))
                    .andExpect(jsonPath("$.expiresIn", greaterThan(0)));
        }

        @Test
        @DisplayName("Returned token is valid and contains correct claims")
        void shouldReturnTokenWithCorrectClaims() throws Exception {
            String responseBody = mockMvc.perform(post("/api/v1/auth/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "subject": "merchant-xyz",
                                      "tenantId": "tenant-002",
                                      "roles": ["PAYMENT_VIEW"]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // Extract token from response JSON
            String token = responseBody.split("\"token\":\"")[1].split("\"")[0];

            Claims claims = jwtTokenProvider.validateAndExtract(token);
            assertThat(claims.getSubject()).isEqualTo("merchant-xyz");
            assertThat(jwtTokenProvider.getTenantId(claims)).isEqualTo("tenant-002");
            assertThat(jwtTokenProvider.getRoles(claims)).containsExactly("PAYMENT_VIEW");
        }

        @Test
        @DisplayName("Token endpoint is accessible without authentication")
        void shouldBePubliclyAccessible() throws Exception {
            // No Authorization header — should still succeed
            mockMvc.perform(post("/api/v1/auth/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "subject": "test",
                                      "tenantId": "t1",
                                      "roles": ["PAYMENT_SUBMIT"]
                                    }
                                    """))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Security filter chain — protected endpoints")
    class ProtectedEndpoints {

        @Test
        @DisplayName("Unauthenticated request to protected endpoint returns 401 with standard error format")
        void shouldReject401WithoutToken() throws Exception {
            mockMvc.perform(get("/api/v1/payments/some-id")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value("Authentication required"))
                    .andExpect(jsonPath("$.path").value("/api/v1/payments/some-id"))
                    .andExpect(jsonPath("$.timestamp", notNullValue()));
        }

        @Test
        @DisplayName("Request with valid token to non-existent payment returns 404 (not 401)")
        void shouldPassAuthWithValidToken() throws Exception {
            String token = jwtTokenProvider.generateToken(
                    "merchant-abc", "tenant-001", List.of("PAYMENT_SUBMIT", "PAYMENT_VIEW"));

            mockMvc.perform(get("/api/v1/payments/00000000-0000-0000-0000-000000000001")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON))
                    // Should NOT be 401 — auth passed. Could be 404 or any non-401 status.
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .isNotEqualTo(401));
        }

        @Test
        @DisplayName("Request with invalid token returns 401 with standard error format")
        void shouldRejectInvalidToken() throws Exception {
            mockMvc.perform(get("/api/v1/payments/some-id")
                            .header("Authorization", "Bearer invalid-token-here")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value("Invalid or expired token"))
                    .andExpect(jsonPath("$.timestamp", notNullValue()));
        }

        @Test
        @DisplayName("Request with expired token returns 401")
        void shouldRejectExpiredToken() throws Exception {
            // Create a provider with 0ms expiration to get an already-expired token
            var expiredProps = new com.fintech.gateway.infrastructure.security.JwtProperties(
                    "ThisIsA256BitSecretKeyForHMACSHA256PaymentGateway2026SecureKey!!",
                    0L
            );
            var expiredProvider = new JwtTokenProvider(expiredProps);
            String expiredToken = expiredProvider.generateToken(
                    "merchant-abc", "tenant-001", List.of("PAYMENT_SUBMIT"));

            mockMvc.perform(get("/api/v1/payments/some-id")
                            .header("Authorization", "Bearer " + expiredToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }
}

