package com.fintech.gateway.domain.model;

import com.fintech.gateway.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStatusTransitionEngineTest {

    // ── Valid Transitions ──

    static Stream<Arguments> validTransitions() {
        return Stream.of(
                Arguments.of(
                        new PaymentStatus.Submitted(),
                        new TransitionEvent.StartFraudCheck(),
                        "FRAUD_CHECK_IN_PROGRESS"
                ),
                Arguments.of(
                        new PaymentStatus.FraudCheckInProgress(),
                        new TransitionEvent.FraudCheckPassed(15),
                        "FRAUD_APPROVED"
                ),
                Arguments.of(
                        new PaymentStatus.FraudCheckInProgress(),
                        new TransitionEvent.FraudCheckFailed("High risk", 92),
                        "FRAUD_REJECTED"
                ),
                Arguments.of(
                        new PaymentStatus.FraudApproved(),
                        new TransitionEvent.SendToBank(),
                        "PROCESSING_BY_BANK"
                ),
                Arguments.of(
                        new PaymentStatus.ProcessingByBank(),
                        new TransitionEvent.BankApproved("BNK-123"),
                        "COMPLETED"
                ),
                Arguments.of(
                        new PaymentStatus.ProcessingByBank(),
                        new TransitionEvent.BankRejected("Insufficient funds"),
                        "FAILED"
                )
        );
    }

    @ParameterizedTest(name = "{0} + {1} → {2}")
    @MethodSource("validTransitions")
    @DisplayName("Should transition to expected state")
    void shouldTransitionToExpectedState(PaymentStatus current, TransitionEvent event, String expectedDbValue) {
        PaymentStatus result = PaymentStatusTransitionEngine.transition(current, event);
        assertThat(result.toDbValue()).isEqualTo(expectedDbValue);
    }

    // ── Invalid Transitions ──

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                // Submitted only accepts StartFraudCheck
                Arguments.of(new PaymentStatus.Submitted(), new TransitionEvent.BankApproved("x")),
                Arguments.of(new PaymentStatus.Submitted(), new TransitionEvent.FraudCheckPassed(10)),
                Arguments.of(new PaymentStatus.Submitted(), new TransitionEvent.SendToBank()),
                // FraudCheckInProgress only accepts FraudCheckPassed/FraudCheckFailed
                Arguments.of(new PaymentStatus.FraudCheckInProgress(), new TransitionEvent.StartFraudCheck()),
                Arguments.of(new PaymentStatus.FraudCheckInProgress(), new TransitionEvent.SendToBank()),
                // FraudApproved only accepts SendToBank
                Arguments.of(new PaymentStatus.FraudApproved(), new TransitionEvent.FraudCheckPassed(10)),
                Arguments.of(new PaymentStatus.FraudApproved(), new TransitionEvent.BankApproved("x")),
                // ProcessingByBank only accepts BankApproved/BankRejected
                Arguments.of(new PaymentStatus.ProcessingByBank(), new TransitionEvent.StartFraudCheck()),
                Arguments.of(new PaymentStatus.ProcessingByBank(), new TransitionEvent.SendToBank()),
                // Terminal states reject everything
                Arguments.of(new PaymentStatus.Completed("ref"), new TransitionEvent.StartFraudCheck()),
                Arguments.of(new PaymentStatus.Completed("ref"), new TransitionEvent.SendToBank()),
                Arguments.of(new PaymentStatus.Failed("reason"), new TransitionEvent.StartFraudCheck()),
                Arguments.of(new PaymentStatus.Failed("reason"), new TransitionEvent.BankApproved("x")),
                Arguments.of(new PaymentStatus.FraudRejected("reason"), new TransitionEvent.SendToBank()),
                Arguments.of(new PaymentStatus.FraudRejected("reason"), new TransitionEvent.StartFraudCheck())
        );
    }

    @ParameterizedTest(name = "{0} + {1} → throws")
    @MethodSource("invalidTransitions")
    @DisplayName("Should reject invalid transition")
    void shouldRejectInvalidTransition(PaymentStatus current, TransitionEvent event) {
        assertThatThrownBy(() -> PaymentStatusTransitionEngine.transition(current, event))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining(current.toDbValue());
    }

    // ── Detail Propagation ──

    @Nested
    @DisplayName("Detail propagation")
    class DetailPropagation {

        @Test
        @DisplayName("FraudRejected carries reason from event")
        void fraudRejectedCarriesReason() {
            PaymentStatus result = PaymentStatusTransitionEngine.transition(
                    new PaymentStatus.FraudCheckInProgress(),
                    new TransitionEvent.FraudCheckFailed("Suspicious pattern", 88)
            );
            assertThat(result).isInstanceOf(PaymentStatus.FraudRejected.class);
            assertThat(result.detail()).isEqualTo("Suspicious pattern");
        }

        @Test
        @DisplayName("Completed carries bank reference from event")
        void completedCarriesBankReference() {
            PaymentStatus result = PaymentStatusTransitionEngine.transition(
                    new PaymentStatus.ProcessingByBank(),
                    new TransitionEvent.BankApproved("BNK-ABC-123")
            );
            assertThat(result).isInstanceOf(PaymentStatus.Completed.class);
            assertThat(result.detail()).isEqualTo("BNK-ABC-123");
        }

        @Test
        @DisplayName("Failed carries reason from event")
        void failedCarriesReason() {
            PaymentStatus result = PaymentStatusTransitionEngine.transition(
                    new PaymentStatus.ProcessingByBank(),
                    new TransitionEvent.BankRejected("Insufficient funds")
            );
            assertThat(result).isInstanceOf(PaymentStatus.Failed.class);
            assertThat(result.detail()).isEqualTo("Insufficient funds");
        }
    }
}

