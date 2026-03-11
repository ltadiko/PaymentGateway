package com.fintech.gateway.domain.model;

import com.fintech.gateway.domain.exception.InvalidStateTransitionException;

/**
 * Pure-function state machine: (currentStatus, triggeringEvent) → newStatus.
 * No side effects, no framework dependencies — only pattern matching on sealed types.
 *
 * <p>Transition table:
 * <pre>
 * Current State          + Event               → New State
 * ─────────────────────────────────────────────────────────
 * Submitted              + StartFraudCheck      → FraudCheckInProgress
 * FraudCheckInProgress   + FraudCheckPassed     → FraudApproved
 * FraudCheckInProgress   + FraudCheckFailed     → FraudRejected(reason)
 * FraudApproved          + SendToBank           → ProcessingByBank
 * ProcessingByBank       + BankApproved         → Completed(bankRef)
 * ProcessingByBank       + BankRejected         → Failed(reason)
 * Completed/Failed/FraudRejected + (any)        → IllegalStateTransition
 * </pre>
 */
public final class PaymentStatusTransitionEngine {

    private PaymentStatusTransitionEngine() {
        // Utility class — no instantiation
    }

    /**
     * Computes the next payment status given the current status and a triggering event.
     *
     * @param current the current payment status
     * @param event   the event that triggers the transition
     * @return the new payment status
     * @throws InvalidStateTransitionException if the transition is not allowed
     */
    public static PaymentStatus transition(PaymentStatus current, TransitionEvent event) {
        return switch (current) {
            case PaymentStatus.Submitted s            -> handleSubmitted(event);
            case PaymentStatus.FraudCheckInProgress f -> handleFraudCheckInProgress(event);
            case PaymentStatus.FraudApproved a        -> handleFraudApproved(event);
            case PaymentStatus.ProcessingByBank p     -> handleProcessingByBank(event);
            // Terminal states — no transitions allowed
            case PaymentStatus.Completed c      -> throw new InvalidStateTransitionException(c, event);
            case PaymentStatus.Failed f         -> throw new InvalidStateTransitionException(f, event);
            case PaymentStatus.FraudRejected r  -> throw new InvalidStateTransitionException(r, event);
        };
    }

    private static PaymentStatus handleSubmitted(TransitionEvent event) {
        return switch (event) {
            case TransitionEvent.StartFraudCheck s -> new PaymentStatus.FraudCheckInProgress();
            default -> throw new InvalidStateTransitionException(new PaymentStatus.Submitted(), event);
        };
    }

    private static PaymentStatus handleFraudCheckInProgress(TransitionEvent event) {
        return switch (event) {
            case TransitionEvent.FraudCheckPassed p -> new PaymentStatus.FraudApproved();
            case TransitionEvent.FraudCheckFailed f -> new PaymentStatus.FraudRejected(f.reason());
            default -> throw new InvalidStateTransitionException(new PaymentStatus.FraudCheckInProgress(), event);
        };
    }

    private static PaymentStatus handleFraudApproved(TransitionEvent event) {
        return switch (event) {
            case TransitionEvent.SendToBank s -> new PaymentStatus.ProcessingByBank();
            default -> throw new InvalidStateTransitionException(new PaymentStatus.FraudApproved(), event);
        };
    }

    private static PaymentStatus handleProcessingByBank(TransitionEvent event) {
        return switch (event) {
            case TransitionEvent.BankApproved b  -> new PaymentStatus.Completed(b.bankReference());
            case TransitionEvent.BankRejected b  -> new PaymentStatus.Failed(b.reason());
            default -> throw new InvalidStateTransitionException(new PaymentStatus.ProcessingByBank(), event);
        };
    }
}


