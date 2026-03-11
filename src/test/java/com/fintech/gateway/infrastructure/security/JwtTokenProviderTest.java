package com.fintech.gateway.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtTokenProvider}.
 *
 * <p>No Spring context — pure unit test with direct instantiation.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "ThisIsA256BitSecretKeyForHMACSHA256PaymentGateway2026SecureKey!!",
                3600000L
        );
        provider = new JwtTokenProvider(properties);
    }

    @Nested
    @DisplayName("Token generation")
    class TokenGeneration {

        @Test
        @DisplayName("Generates a non-empty compact JWT string")
        void shouldGenerateNonEmptyToken() {
            String token = provider.generateToken("merchant-abc", "tenant-001",
                    List.of("PAYMENT_SUBMIT", "PAYMENT_VIEW"));

            assertThat(token).isNotNull().isNotEmpty();
            // JWT has 3 dot-separated parts: header.payload.signature
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("Tokens for different subjects are different")
        void shouldGenerateDifferentTokensForDifferentSubjects() {
            String token1 = provider.generateToken("merchant-abc", "tenant-001", List.of("PAYMENT_SUBMIT"));
            String token2 = provider.generateToken("merchant-xyz", "tenant-002", List.of("PAYMENT_VIEW"));

            assertThat(token1).isNotEqualTo(token2);
        }
    }

    @Nested
    @DisplayName("Token validation and claim extraction")
    class TokenValidation {

        @Test
        @DisplayName("Valid token — extracts correct subject")
        void shouldExtractSubject() {
            String token = provider.generateToken("merchant-abc", "tenant-001", List.of("PAYMENT_SUBMIT"));
            Claims claims = provider.validateAndExtract(token);

            assertThat(claims.getSubject()).isEqualTo("merchant-abc");
        }

        @Test
        @DisplayName("Valid token — extracts correct tenantId")
        void shouldExtractTenantId() {
            String token = provider.generateToken("merchant-abc", "tenant-001", List.of("PAYMENT_SUBMIT"));
            Claims claims = provider.validateAndExtract(token);

            assertThat(provider.getTenantId(claims)).isEqualTo("tenant-001");
        }

        @Test
        @DisplayName("Valid token — extracts correct roles")
        void shouldExtractRoles() {
            String token = provider.generateToken("merchant-abc", "tenant-001",
                    List.of("PAYMENT_SUBMIT", "PAYMENT_VIEW"));
            Claims claims = provider.validateAndExtract(token);

            List<String> roles = provider.getRoles(claims);
            assertThat(roles).containsExactly("PAYMENT_SUBMIT", "PAYMENT_VIEW");
        }

        @Test
        @DisplayName("Valid token — expiration is set in the future")
        void shouldHaveFutureExpiration() {
            String token = provider.generateToken("merchant-abc", "tenant-001", List.of("PAYMENT_SUBMIT"));
            Claims claims = provider.validateAndExtract(token);

            assertThat(claims.getExpiration()).isInTheFuture();
        }
    }

    @Nested
    @DisplayName("Invalid tokens")
    class InvalidTokens {

        @Test
        @DisplayName("Expired token throws JwtException")
        void shouldRejectExpiredToken() {
            // Create a provider with 0ms expiration
            JwtProperties expiredProps = new JwtProperties(
                    "ThisIsA256BitSecretKeyForHMACSHA256PaymentGateway2026SecureKey!!",
                    0L
            );
            JwtTokenProvider expiredProvider = new JwtTokenProvider(expiredProps);
            String token = expiredProvider.generateToken("merchant-abc", "tenant-001", List.of("PAYMENT_SUBMIT"));

            // Token is already expired
            assertThatThrownBy(() -> expiredProvider.validateAndExtract(token))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Tampered token throws JwtException")
        void shouldRejectTamperedToken() {
            String token = provider.generateToken("merchant-abc", "tenant-001", List.of("PAYMENT_SUBMIT"));

            // Tamper with the payload (change a character in the middle)
            String tampered = token.substring(0, token.length() / 2) + "X" + token.substring(token.length() / 2 + 1);

            assertThatThrownBy(() -> provider.validateAndExtract(tampered))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Malformed string throws JwtException")
        void shouldRejectMalformedString() {
            assertThatThrownBy(() -> provider.validateAndExtract("not-a-jwt-token"))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Token signed with wrong key throws JwtException")
        void shouldRejectWrongKey() {
            String token = provider.generateToken("merchant-abc", "tenant-001", List.of("PAYMENT_SUBMIT"));

            JwtProperties otherKeyProps = new JwtProperties(
                    "ACompletelyDifferentSecretKeyForAnotherGatewayService2026!!!!",
                    3600000L
            );
            JwtTokenProvider otherProvider = new JwtTokenProvider(otherKeyProps);

            assertThatThrownBy(() -> otherProvider.validateAndExtract(token))
                    .isInstanceOf(JwtException.class);
        }
    }
}

