package com.fintech.gateway.infrastructure.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DataMaskingUtil}.
 */
class DataMaskingUtilTest {

    @Test
    @DisplayName("Masks standard IBAN — shows last 4")
    void shouldMaskStandardIban() {
        assertThat(DataMaskingUtil.maskAccountNumber("NL91ABNA0417164300"))
                .isEqualTo("****4300");
    }

    @Test
    @DisplayName("Masks long German IBAN — shows last 4")
    void shouldMaskLongIban() {
        assertThat(DataMaskingUtil.maskAccountNumber("DE89370400440532013000"))
                .isEqualTo("****3000");
    }

    @Test
    @DisplayName("Masks card number — shows last 4")
    void shouldMaskCardNumber() {
        assertThat(DataMaskingUtil.maskAccountNumber("4111111111111111"))
                .isEqualTo("****1111");
    }

    @Test
    @DisplayName("Short input (exactly 4 chars) — fully masked")
    void shouldFullyMaskFourCharInput() {
        assertThat(DataMaskingUtil.maskAccountNumber("1234"))
                .isEqualTo("****");
    }

    @Test
    @DisplayName("Short input (2 chars) — fully masked")
    void shouldFullyMaskShortInput() {
        assertThat(DataMaskingUtil.maskAccountNumber("12"))
                .isEqualTo("****");
    }

    @Test
    @DisplayName("Exactly 5 chars — shows last 4")
    void shouldMaskFiveCharInput() {
        assertThat(DataMaskingUtil.maskAccountNumber("12345"))
                .isEqualTo("****2345");
    }

    @Test
    @DisplayName("Null input returns null")
    void shouldReturnNullForNullInput() {
        assertThat(DataMaskingUtil.maskAccountNumber(null)).isNull();
    }

    @Test
    @DisplayName("Empty input returns empty")
    void shouldReturnEmptyForEmptyInput() {
        assertThat(DataMaskingUtil.maskAccountNumber("")).isEmpty();
    }
}

