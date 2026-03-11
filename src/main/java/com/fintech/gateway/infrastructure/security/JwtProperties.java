package com.fintech.gateway.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for JWT token generation and validation.
 *
 * <p>Bound to the {@code app.security.jwt} prefix in {@code application.yml}.
 *
 * @param secret       HMAC-SHA256 signing secret (must be at least 256 bits / 32 characters)
 * @param expirationMs token expiration time in milliseconds (default: 1 hour)
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String secret,
        long expirationMs
) {}

