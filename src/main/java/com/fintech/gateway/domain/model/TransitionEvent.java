package com.fintech.gateway.domain.model;

/**
 * Sealed interface representing events that trigger payment state transitions.
 * Used by {@link PaymentStatusTransitionEngine} to determine the next state.
 */
public sealed interface TransitionEvent {

    record StartFraudCheck() implements TransitionEvent {}

    record FraudCheckPassed(int fraudScore) implements TransitionEvent {
        public FraudCheckPassed {
            if (fraudScore < 0 || fraudScore > 100) {
                throw new IllegalArgumentException("Fraud score must be between 0 and 100, got: " + fraudScore);
            }
        }
    }

    record FraudCheckFailed(String reason, int fraudScore) implements TransitionEvent {
        public FraudCheckFailed {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("FraudCheckFailed reason must not be null or blank");
            }
            if (fraudScore < 0 || fraudScore > 100) {
                throw new IllegalArgumentException("Fraud score must be between 0 and 100, got: " + fraudScore);
            }
        }
    }

    record SendToBank() implements TransitionEvent {}

    record BankApproved(String bankReference) implements TransitionEvent {
        public BankApproved {
            if (bankReference == null || bankReference.isBlank()) {
                throw new IllegalArgumentException("BankApproved bankReference must not be null or blank");
            }
        }
    }

    record BankRejected(String reason) implements TransitionEvent {
        public BankRejected {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("BankRejected reason must not be null or blank");
            }
        }
    }
}

