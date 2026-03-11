package com.fintech.gateway.infrastructure.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for AES-256 field-level encryption.
 *
 * <p>Bound to the {@code app.encryption} prefix in {@code application.yml}.
 *
 * @param secretKey AES secret key (must be exactly 16 bytes for AES-128 or 32 bytes for AES-256)
 */
@ConfigurationProperties(prefix = "app.encryption")
public record EncryptionProperties(
        String secretKey
) {}

