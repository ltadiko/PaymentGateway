package com.fintech.gateway.domain.exception;

import com.fintech.gateway.domain.model.PaymentStatus;
import com.fintech.gateway.domain.model.TransitionEvent;

/**
 * Thrown when a state transition is not allowed from the current payment status.
 */
public class InvalidStateTransitionException extends RuntimeException {

    private final String currentStatus;
    private final String event;

    public InvalidStateTransitionException(PaymentStatus current, TransitionEvent event) {
        super("Invalid state transition: cannot apply " + event.getClass().getSimpleName()
                + " to status " + current.toDbValue());
        this.currentStatus = current.toDbValue();
        this.event = event.getClass().getSimpleName();
    }

    public String getCurrentStatus() { return currentStatus; }
    public String getEvent() { return event; }
}

