package com.fintech.gateway.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link PaymentStatus} sealed interface.
 */
class PaymentStatusTest {

    @Test
    @DisplayName("Submitted.toDbValue() returns SUBMITTED")
    void submittedToDb() {
        assertThat(new PaymentStatus.Submitted().toDbValue()).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("FraudCheckInProgress.toDbValue() returns FRAUD_CHECK_IN_PROGRESS")
    void fraudCheckInProgressToDb() {
        assertThat(new PaymentStatus.FraudCheckInProgress().toDbValue()).isEqualTo("FRAUD_CHECK_IN_PROGRESS");
    }

    @Test
    @DisplayName("FraudApproved.toDbValue() returns FRAUD_APPROVED")
    void fraudApprovedToDb() {
        assertThat(new PaymentStatus.FraudApproved().toDbValue()).isEqualTo("FRAUD_APPROVED");
    }

    @Test
    @DisplayName("FraudRejected.toDbValue() returns FRAUD_REJECTED")
    void fraudRejectedToDb() {
        assertThat(new PaymentStatus.FraudRejected("high risk").toDbValue()).isEqualTo("FRAUD_REJECTED");
    }

    @Test
    @DisplayName("ProcessingByBank.toDbValue() returns PROCESSING_BY_BANK")
    void processingByBankToDb() {
        assertThat(new PaymentStatus.ProcessingByBank().toDbValue()).isEqualTo("PROCESSING_BY_BANK");
    }

    @Test
    @DisplayName("Completed.toDbValue() returns COMPLETED")
    void completedToDb() {
        assertThat(new PaymentStatus.Completed("REF-123").toDbValue()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Failed.toDbValue() returns FAILED")
    void failedToDb() {
        assertThat(new PaymentStatus.Failed("timeout").toDbValue()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("isTerminal() returns true for Completed, Failed, FraudRejected")
    void terminalStates() {
        assertThat(new PaymentStatus.Completed("REF-123").isTerminal()).isTrue();
        assertThat(new PaymentStatus.Failed("error").isTerminal()).isTrue();
        assertThat(new PaymentStatus.FraudRejected("fraud").isTerminal()).isTrue();
    }

    @Test
    @DisplayName("isTerminal() returns false for non-terminal states")
    void nonTerminalStates() {
        assertThat(new PaymentStatus.Submitted().isTerminal()).isFalse();
        assertThat(new PaymentStatus.FraudCheckInProgress().isTerminal()).isFalse();
        assertThat(new PaymentStatus.FraudApproved().isTerminal()).isFalse();
        assertThat(new PaymentStatus.ProcessingByBank().isTerminal()).isFalse();
    }

    @Test
    @DisplayName("detail() returns reason/reference for applicable states")
    void detailValues() {
        assertThat(new PaymentStatus.FraudRejected("suspicious").detail()).isEqualTo("suspicious");
        assertThat(new PaymentStatus.Completed("BANK-REF").detail()).isEqualTo("BANK-REF");
        assertThat(new PaymentStatus.Failed("network error").detail()).isEqualTo("network error");
        assertThat(new PaymentStatus.Submitted().detail()).isNull();
    }

    static Stream<Arguments> dbValueProvider() {
        return Stream.of(
                Arguments.of("SUBMITTED", null, PaymentStatus.Submitted.class),
                Arguments.of("FRAUD_CHECK_IN_PROGRESS", null, PaymentStatus.FraudCheckInProgress.class),
                Arguments.of("FRAUD_APPROVED", null, PaymentStatus.FraudApproved.class),
                Arguments.of("FRAUD_REJECTED", "high risk", PaymentStatus.FraudRejected.class),
                Arguments.of("PROCESSING_BY_BANK", null, PaymentStatus.ProcessingByBank.class),
                Arguments.of("COMPLETED", "REF-123", PaymentStatus.Completed.class),
                Arguments.of("FAILED", "timeout", PaymentStatus.Failed.class)
        );
    }

    @ParameterizedTest(name = "fromDbValue(\"{0}\") returns {2}")
    @MethodSource("dbValueProvider")
    @DisplayName("fromDbValue round-trip")
    void fromDbValueRoundTrip(String dbValue, String detail, Class<? extends PaymentStatus> expectedType) {
        PaymentStatus status = PaymentStatus.fromDbValue(dbValue, detail);
        assertThat(status).isInstanceOf(expectedType);
        assertThat(status.toDbValue()).isEqualTo(dbValue);
    }

    @Test
    @DisplayName("fromDbValue throws for unknown status")
    void fromDbValueUnknown() {
        assertThatThrownBy(() -> PaymentStatus.fromDbValue("UNKNOWN", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown payment status");
    }

    @Test
    @DisplayName("FraudRejected rejects null reason")
    void fraudRejectedNullReason() {
        assertThatThrownBy(() -> new PaymentStatus.FraudRejected(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Completed rejects null bankReference")
    void completedNullRef() {
        assertThatThrownBy(() -> new PaymentStatus.Completed(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Failed rejects null reason")
    void failedNullReason() {
        assertThatThrownBy(() -> new PaymentStatus.Failed(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
