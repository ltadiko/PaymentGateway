package com.fintech.gateway.infrastructure.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link EncryptionService}.
 *
 * <p>No Spring context — pure unit test with direct instantiation.
 */
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        EncryptionProperties properties = new EncryptionProperties("AES256BitSecretKey!");
        encryptionService = new EncryptionService(properties);
    }

    @Test
    @DisplayName("Encrypt then decrypt returns original plaintext")
    void encryptDecryptRoundTrip() {
        String plainText = "NL91ABNA0417164300";

        String encrypted = encryptionService.encrypt(plainText);
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("Encrypt produces Base64-encoded output")
    void encryptProducesBase64() {
        String encrypted = encryptionService.encrypt("4111111111111111");

        // Base64 characters only
        assertThat(encrypted).matches("[A-Za-z0-9+/=]+");
    }

    @Test
    @DisplayName("Two encryptions of same plaintext produce different ciphertexts (random IV)")
    void encryptionIsNonDeterministic() {
        String plainText = "4111111111111111";

        String encrypted1 = encryptionService.encrypt(plainText);
        String encrypted2 = encryptionService.encrypt(plainText);

        assertThat(encrypted1).isNotEqualTo(encrypted2);

        // But both decrypt to the same value
        assertThat(encryptionService.decrypt(encrypted1)).isEqualTo(plainText);
        assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(plainText);
    }

    @Test
    @DisplayName("Null input returns null for encrypt")
    void encryptNullReturnsNull() {
        assertThat(encryptionService.encrypt(null)).isNull();
    }

    @Test
    @DisplayName("Null input returns null for decrypt")
    void decryptNullReturnsNull() {
        assertThat(encryptionService.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("Empty string round-trips correctly")
    void emptyStringRoundTrip() {
        String encrypted = encryptionService.encrypt("");
        assertThat(encryptionService.decrypt(encrypted)).isEmpty();
    }

    @Test
    @DisplayName("Tampered ciphertext throws EncryptionException")
    void decryptTamperedCiphertextThrows() {
        String encrypted = encryptionService.encrypt("secret-data");
        String tampered = encrypted.substring(0, encrypted.length() - 3) + "XXX";

        assertThatThrownBy(() -> encryptionService.decrypt(tampered))
                .isInstanceOf(EncryptionService.EncryptionException.class)
                .hasMessageContaining("Failed to decrypt");
    }

    @Test
    @DisplayName("Decrypt with wrong key throws EncryptionException")
    void decryptWithWrongKeyThrows() {
        String encrypted = encryptionService.encrypt("secret-data");

        EncryptionService otherService = new EncryptionService(
                new EncryptionProperties("ACompletelyDifferentKey!!")
        );

        assertThatThrownBy(() -> otherService.decrypt(encrypted))
                .isInstanceOf(EncryptionService.EncryptionException.class);
    }

    @Test
    @DisplayName("Long text round-trips correctly")
    void longTextRoundTrip() {
        String longText = "This is a very long account number or description "
                + "that spans multiple AES blocks to test padding: "
                + "DE89370400440532013000 / NL91ABNA0417164300";

        String encrypted = encryptionService.encrypt(longText);
        assertThat(encryptionService.decrypt(encrypted)).isEqualTo(longText);
    }
}

