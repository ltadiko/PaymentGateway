package com.fintech.gateway.infrastructure.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * JPA attribute converter that transparently encrypts/decrypts string fields.
 *
 * <p>Applied to PCI-sensitive columns (e.g., {@code creditorAccount}, {@code debtorAccount})
 * via {@code @Convert(converter = AesAttributeConverter.class)} on the JPA entity field.
 *
 * <p>When writing to the database, the plaintext value is encrypted using AES-GCM.
 * When reading from the database, the ciphertext is decrypted back to plaintext.
 * Null values pass through unchanged.
 *
 * <p>Example database content:
 * <pre>
 * creditor_account: "dGhpcyBpcyBlbmNyeXB0ZWQ="  (Base64-encoded ciphertext)
 * </pre>
 *
 * @see EncryptionService
 */
@Converter
@Component
public class AesAttributeConverter implements AttributeConverter<String, String> {

    private final EncryptionService encryptionService;

    /**
     * Constructs the converter with the encryption service.
     *
     * @param encryptionService the AES encryption service
     */
    public AesAttributeConverter(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    /**
     * Encrypts the entity attribute value before storing in the database.
     *
     * @param attribute the plaintext value (may be null)
     * @return the encrypted value, or null if input is null
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptionService.encrypt(attribute);
    }

    /**
     * Decrypts the database column value when loading into the entity.
     *
     * @param dbData the encrypted value from the database (may be null)
     * @return the decrypted plaintext, or null if input is null
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptionService.decrypt(dbData);
    }
}

