package com.fintech.gateway.infrastructure.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MaskingPatternLayout}.
 *
 * <p>Verifies that all masking rules correctly redact sensitive data
 * while preserving non-sensitive log content. Tests the {@code mask()}
 * method directly without requiring Logback event infrastructure.
 */
class MaskingPatternLayoutTest {

    private MaskingPatternLayout layout;

    @BeforeEach
    void setUp() {
        layout = new MaskingPatternLayout();
    }

    @Nested
    @DisplayName("IBAN masking")
    class IbanMasking {

        @Test
        @DisplayName("Dutch IBAN → ****4300")
        void shouldMaskDutchIban() {
            String result = layout.mask("Account: NL91ABNA0417164300");

            assertThat(result).contains("****4300");
            assertThat(result).doesNotContain("NL91ABNA0417164300");
        }

        @Test
        @DisplayName("German IBAN → ****3000")
        void shouldMaskGermanIban() {
            String result = layout.mask("Debtor account: DE89370400440532013000");

            assertThat(result).contains("****3000");
            assertThat(result).doesNotContain("DE89370400440532013000");
        }

        @Test
        @DisplayName("Multiple IBANs in one line")
        void shouldMaskMultipleIbans() {
            String result = layout.mask(
                    "Transfer from NL91ABNA0417164300 to DE89370400440532013000");

            assertThat(result).contains("****4300");
            assertThat(result).contains("****3000");
            assertThat(result).doesNotContain("NL91ABNA0417164300");
            assertThat(result).doesNotContain("DE89370400440532013000");
        }
    }

    @Nested
    @DisplayName("Card number masking")
    class CardNumberMasking {

        @Test
        @DisplayName("Card with dashes → ****1111")
        void shouldMaskDashedCardNumber() {
            String result = layout.mask("Card: 4111-1111-1111-1111");

            assertThat(result).contains("****1111");
            assertThat(result).doesNotContain("4111-1111-1111-1111");
        }

        @Test
        @DisplayName("Card with spaces → ****4242")
        void shouldMaskSpacedCardNumber() {
            String result = layout.mask("Card: 4242 4242 4242 4242");

            assertThat(result).contains("****4242");
            assertThat(result).doesNotContain("4242 4242 4242 4242");
        }

        @Test
        @DisplayName("Card without separators → ****1111")
        void shouldMaskContinuousCardNumber() {
            String result = layout.mask("Card: 4111111111111111");

            assertThat(result).contains("****1111");
            assertThat(result).doesNotContain("4111111111111111");
        }
    }

    @Nested
    @DisplayName("JSON field masking")
    class JsonFieldMasking {

        @Test
        @DisplayName("creditorAccount JSON field → masked")
        void shouldMaskCreditorAccountJson() {
            String result = layout.mask(
                    "{\"creditorAccount\":\"NL91ABNA0417164300\",\"amount\":100}");

            assertThat(result).contains("\"creditorAccount\":\"****4300\"");
            assertThat(result).doesNotContain("NL91ABNA0417164300");
        }

        @Test
        @DisplayName("debtorAccount JSON field → masked")
        void shouldMaskDebtorAccountJson() {
            String result = layout.mask(
                    "{\"debtorAccount\":\"DE89370400440532013000\"}");

            assertThat(result).contains("\"debtorAccount\":\"****3000\"");
        }

        @Test
        @DisplayName("accountNumber JSON field → masked")
        void shouldMaskAccountNumberJson() {
            String result = layout.mask(
                    "{\"accountNumber\":\"GB82WEST12345698765432\"}");

            assertThat(result).contains("\"accountNumber\":\"****5432\"");
        }
    }

    @Nested
    @DisplayName("JWT token masking")
    class JwtTokenMasking {

        @Test
        @DisplayName("Bearer token → Bearer ****")
        void shouldMaskBearerToken() {
            String token = "Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ1c2VyIiwidGVuYW50SWQiOiJ0MSJ9.abc123";
            String result = layout.mask(token);

            assertThat(result).isEqualTo("Bearer ****");
            assertThat(result).doesNotContain("eyJhbGci");
        }

        @Test
        @DisplayName("Authorization header in log → masked")
        void shouldMaskAuthHeaderInLog() {
            String result = layout.mask(
                    "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ1c2VyIn0.signature");

            assertThat(result).contains("Bearer ****");
            assertThat(result).doesNotContain("eyJhbGci");
        }
    }

    @Nested
    @DisplayName("Non-sensitive content")
    class NonSensitiveContent {

        @Test
        @DisplayName("Normal log message is not altered")
        void shouldNotAlterNormalMessages() {
            String message = "Payment submitted: paymentId=550e8400-e29b-41d4-a716-446655440000, amount=100.00";
            String result = layout.mask(message);

            assertThat(result).isEqualTo(message);
        }

        @Test
        @DisplayName("UUID is not masked")
        void shouldNotMaskUuid() {
            String result = layout.mask("paymentId=550e8400-e29b-41d4-a716-446655440000");

            assertThat(result).contains("550e8400-e29b-41d4-a716-446655440000");
        }

        @Test
        @DisplayName("Short numbers are not masked")
        void shouldNotMaskShortNumbers() {
            String result = layout.mask("amount=100.00, count=5");

            assertThat(result).contains("100.00");
            assertThat(result).contains("5");
        }
    }

    @Nested
    @DisplayName("Combined masking")
    class CombinedMasking {

        @Test
        @DisplayName("Multiple sensitive values in one line")
        void shouldMaskMultipleSensitiveValues() {
            String message = "Payment from NL91ABNA0417164300 card 4111-1111-1111-1111 " +
                    "with Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ1c2VyIn0.sig";
            String result = layout.mask(message);

            assertThat(result).doesNotContain("NL91ABNA0417164300");
            assertThat(result).doesNotContain("4111-1111-1111-1111");
            assertThat(result).doesNotContain("eyJhbGci");
            assertThat(result).contains("****4300");
            assertThat(result).contains("****1111");
            assertThat(result).contains("Bearer ****");
        }
    }
}

