package com.fintech.gateway.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT token provider for generating and validating bearer tokens.
 *
 * <p>Uses HMAC-SHA256 for signing. Tokens contain the following claims:
 * <ul>
 *   <li>{@code sub} — the subject (merchant/client identifier)</li>
 *   <li>{@code tenantId} — tenant identifier for multi-tenancy isolation</li>
 *   <li>{@code roles} — list of granted roles (e.g., PAYMENT_SUBMIT, PAYMENT_VIEW)</li>
 *   <li>{@code iat} — issued-at timestamp</li>
 *   <li>{@code exp} — expiration timestamp</li>
 * </ul>
 *
 * <p><strong>Note:</strong> In production, tokens would be issued by an external IdP
 * (Keycloak, Auth0). This component serves as a mock for the assignment scope.
 *
 * @see JwtProperties
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey signingKey;
    private final long expirationMs;

    /**
     * Constructs the token provider with configuration from {@link JwtProperties}.
     *
     * @param properties JWT configuration properties
     */
    public JwtTokenProvider(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = properties.expirationMs();
    }

    /**
     * Generates a signed JWT token.
     *
     * @param subject  the token subject (e.g., merchant ID)
     * @param tenantId the tenant identifier for multi-tenancy
     * @param roles    the list of roles to embed in the token
     * @return the compact JWT string (e.g., "eyJhbG...")
     */
    public String generateToken(String subject, String tenantId, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        String token = Jwts.builder()
                .subject(subject)
                .claim("tenantId", tenantId)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();

        log.info("JWT token generated: subject={}, tenantId={}, roles={}, expiresAt={}",
                subject, tenantId, roles, expiry);

        return token;
    }

    /**
     * Validates a JWT token and extracts its claims.
     *
     * <p>Verifies the signature and checks that the token has not expired.
     *
     * @param token the compact JWT string
     * @return the parsed claims
     * @throws JwtException if the token is invalid, expired, or tampered with
     */
    public Claims validateAndExtract(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            log.debug("JWT token validated: subject={}, tenantId={}",
                    claims.getSubject(), claims.get("tenantId"));

            return claims;
        } catch (JwtException e) {
            log.warn("JWT token validation failed: reason={}", e.getMessage());
            throw e;
        }
    }

    /**
     * Extracts the tenant ID from parsed JWT claims.
     *
     * @param claims the parsed JWT claims
     * @return the tenant identifier
     */
    public String getTenantId(Claims claims) {
        return claims.get("tenantId", String.class);
    }

    /**
     * Extracts the list of roles from parsed JWT claims.
     *
     * @param claims the parsed JWT claims
     * @return the list of role strings
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        return claims.get("roles", List.class);
    }
}
