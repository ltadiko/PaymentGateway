package com.fintech.gateway.infrastructure.crypto;

/**
 * Utility class for masking sensitive data (PCI/PII) in API responses and logs.
 *
 * <p>Masking rules:
 * <ul>
 *   <li>Account numbers: show only the last 4 characters, prefix with "****"</li>
 *   <li>Short values (≤ 4 chars): fully masked as "****"</li>
 *   <li>Null/empty values: returned as-is</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 *   DataMaskingUtil.maskAccountNumber("NL91ABNA0417164300") → "****4300"
 *   DataMaskingUtil.maskAccountNumber("1234")               → "****1234"
 *   DataMaskingUtil.maskAccountNumber("12")                 → "****"
 *   DataMaskingUtil.maskAccountNumber(null)                 → null
 * }</pre>
 */
public final class DataMaskingUtil {

    private static final String MASK = "****";
    private static final int VISIBLE_SUFFIX_LENGTH = 4;

    private DataMaskingUtil() {
        // Utility class — no instantiation
    }

    /**
     * Masks an account number, showing only the last 4 characters.
     *
     * @param accountNumber the raw account number (e.g., "NL91ABNA0417164300")
     * @return the masked account number (e.g., "****4300"), or {@code null} if input is null
     */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isEmpty()) {
            return accountNumber;
        }
        if (accountNumber.length() <= VISIBLE_SUFFIX_LENGTH) {
            return MASK;
        }
        return MASK + accountNumber.substring(accountNumber.length() - VISIBLE_SUFFIX_LENGTH);
    }
}

