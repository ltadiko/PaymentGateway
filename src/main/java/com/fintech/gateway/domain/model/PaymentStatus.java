package com.fintech.gateway.domain.model;

/**
 * Sealed interface representing all possible payment states.
 * Uses Java 21 sealed types for exhaustive pattern matching.
 * Terminal states: Completed, Failed, FraudRejected.
 */
public sealed interface PaymentStatus {

    record Submitted() implements PaymentStatus {}

    record FraudCheckInProgress() implements PaymentStatus {}

    record FraudApproved() implements PaymentStatus {}

    record FraudRejected(String reason) implements PaymentStatus {
        public FraudRejected {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("FraudRejected reason must not be null or blank");
            }
        }
    }

    record ProcessingByBank() implements PaymentStatus {}

    record Completed(String bankReference) implements PaymentStatus {
        public Completed {
            if (bankReference == null || bankReference.isBlank()) {
                throw new IllegalArgumentException("Completed bankReference must not be null or blank");
            }
        }
    }

    record Failed(String reason) implements PaymentStatus {
        public Failed {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Failed reason must not be null or blank");
            }
        }
    }

    /**
     * Returns true if this status is a terminal state (no further transitions allowed).
     */
    default boolean isTerminal() {
        return switch (this) {
            case Completed c -> true;
            case Failed f -> true;
            case FraudRejected r -> true;
            default -> false;
        };
    }

    /**
     * Converts this sealed type to a persistent string representation.
     */
    default String toDbValue() {
        return switch (this) {
            case Submitted s            -> "SUBMITTED";
            case FraudCheckInProgress f -> "FRAUD_CHECK_IN_PROGRESS";
            case FraudApproved a        -> "FRAUD_APPROVED";
            case FraudRejected r        -> "FRAUD_REJECTED";
            case ProcessingByBank p     -> "PROCESSING_BY_BANK";
            case Completed c            -> "COMPLETED";
            case Failed f               -> "FAILED";
        };
    }

    /**
     * Returns the detail string (reason or bank reference) if applicable, null otherwise.
     */
    default String detail() {
        return switch (this) {
            case FraudRejected r -> r.reason();
            case Completed c    -> c.bankReference();
            case Failed f       -> f.reason();
            default             -> null;
        };
    }

    /**
     * Reconstructs a PaymentStatus from its DB string representation and optional detail.
     */
    static PaymentStatus fromDbValue(String status, String detail) {
        return switch (status) {
            case "SUBMITTED"               -> new Submitted();
            case "FRAUD_CHECK_IN_PROGRESS" -> new FraudCheckInProgress();
            case "FRAUD_APPROVED"          -> new FraudApproved();
            case "FRAUD_REJECTED"          -> new FraudRejected(detail);
            case "PROCESSING_BY_BANK"      -> new ProcessingByBank();
            case "COMPLETED"               -> new Completed(detail);
            case "FAILED"                  -> new Failed(detail);
            default -> throw new IllegalArgumentException("Unknown payment status: " + status);
        };
    }
}

