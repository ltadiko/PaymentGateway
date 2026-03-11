package com.fintech.gateway.infrastructure.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-GCM encryption service for protecting sensitive data at rest.
 *
 * <p>Used to encrypt PCI-sensitive fields (account numbers) before persisting
 * them to the database. Each encryption generates a unique random IV (Initialization
 * Vector), so encrypting the same plaintext twice produces different ciphertexts.
 *
 * <p><strong>Algorithm:</strong> AES/GCM/NoPadding with:
 * <ul>
 *   <li>128-bit authentication tag</li>
 *   <li>96-bit (12-byte) random IV prepended to the ciphertext</li>
 *   <li>Key derived from the configured secret (padded/truncated to 32 bytes for AES-256)</li>
 * </ul>
 *
 * <p>The output format is: {@code Base64(IV + ciphertext + authTag)}.
 *
 * @see EncryptionProperties
 */
@Component
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int KEY_LENGTH_BYTES = 32; // AES-256

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom;

    /**
     * Constructs the encryption service with the configured secret key.
     *
     * @param properties the encryption configuration properties
     */
    public EncryptionService(EncryptionProperties properties) {
        byte[] keyBytes = deriveKey(properties.secretKey());
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();
        log.info("AES-256-GCM encryption service initialized");
    }

    /**
     * Encrypts a plaintext string using AES-GCM.
     *
     * <p>Each call generates a new random IV, ensuring that the same plaintext
     * produces a different ciphertext every time (semantic security).
     *
     * @param plainText the plaintext to encrypt (may be {@code null})
     * @return the Base64-encoded ciphertext, or {@code null} if input is null
     * @throws EncryptionException if encryption fails
     */
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext: [IV(12) | ciphertext | authTag]
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypts a Base64-encoded AES-GCM ciphertext back to plaintext.
     *
     * @param cipherText the Base64-encoded ciphertext (may be {@code null})
     * @return the original plaintext, or {@code null} if input is null
     * @throws EncryptionException if decryption fails (e.g., tampered data, wrong key)
     */
    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);

            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH_BYTES);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage());
            throw new EncryptionException("Failed to decrypt data", e);
        }
    }

    /**
     * Derives a fixed-length AES key from the configured secret string.
     *
     * <p>Pads with zeros or truncates to exactly {@value KEY_LENGTH_BYTES} bytes.
     *
     * @param secret the raw secret string from configuration
     * @return a 32-byte key suitable for AES-256
     */
    private static byte[] deriveKey(String secret) {
        byte[] keyBytes = new byte[KEY_LENGTH_BYTES];
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(secretBytes, 0, keyBytes, 0, Math.min(secretBytes.length, KEY_LENGTH_BYTES));
        return keyBytes;
    }

    /**
     * Runtime exception for encryption/decryption failures.
     */
    public static class EncryptionException extends RuntimeException {

        /**
         * Constructs an EncryptionException.
         *
         * @param message the error message
         * @param cause   the underlying cause
         */
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

