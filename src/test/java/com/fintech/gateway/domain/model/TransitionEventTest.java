package com.fintech.gateway.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransitionEventTest {

    @Test
    @DisplayName("FraudCheckPassed rejects score out of range")
    void fraudCheckPassedRejectsInvalidScore() {
        assertThatThrownBy(() -> new TransitionEvent.FraudCheckPassed(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransitionEvent.FraudCheckPassed(101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FraudCheckPassed accepts boundary scores 0 and 100")
    void fraudCheckPassedAcceptsBoundaryScores() {
        assertThatCode(() -> new TransitionEvent.FraudCheckPassed(0)).doesNotThrowAnyException();
        assertThatCode(() -> new TransitionEvent.FraudCheckPassed(100)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FraudCheckFailed rejects null/blank reason")
    void fraudCheckFailedRejectsInvalidReason() {
        assertThatThrownBy(() -> new TransitionEvent.FraudCheckFailed(null, 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransitionEvent.FraudCheckFailed("", 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FraudCheckFailed rejects score out of range")
    void fraudCheckFailedRejectsInvalidScore() {
        assertThatThrownBy(() -> new TransitionEvent.FraudCheckFailed("reason", -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransitionEvent.FraudCheckFailed("reason", 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("BankApproved rejects null/blank reference")
    void bankApprovedRejectsInvalidReference() {
        assertThatThrownBy(() -> new TransitionEvent.BankApproved(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransitionEvent.BankApproved(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("BankRejected rejects null/blank reason")
    void bankRejectedRejectsInvalidReason() {
        assertThatThrownBy(() -> new TransitionEvent.BankRejected(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransitionEvent.BankRejected("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

