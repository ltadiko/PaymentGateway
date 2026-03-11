package com.fintech.gateway.domain.port.in;

import com.fintech.gateway.application.dto.PaymentResponse;
import com.fintech.gateway.application.dto.SubmitPaymentCommand;

/**
 * Inbound port for submitting a new payment.
 *
 * <p>This is the primary use case for payment ingestion. The implementation must:
 * <ul>
 *   <li>Check idempotency to prevent duplicate charges</li>
 *   <li>Validate the payment request</li>
 *   <li>Persist the payment in SUBMITTED state</li>
 *   <li>Record an audit trail entry</li>
 *   <li>Publish a {@code PaymentSubmitted} event to the message broker</li>
 * </ul>
 *
 * <p>The endpoint acknowledges the request immediately (HTTP 202) and returns
 * a tracking identifier. Actual processing happens asynchronously via the
 * event-driven pipeline.
 *
 * @see com.fintech.gateway.application.dto.SubmitPaymentCommand
 * @see com.fintech.gateway.application.dto.PaymentResponse
 */
public interface SubmitPaymentUseCase {

    /**
     * Submits a new payment for asynchronous processing.
     *
     * <p>If an identical idempotency key is found for the same tenant, the original
     * response is returned without creating a new payment.
     *
     * @param command the payment submission command containing all required fields
     * @return a response containing the payment ID and initial status
     * @throws com.fintech.gateway.domain.exception.DuplicatePaymentException
     *         if a race condition occurs during idempotency check
     */
    PaymentResponse submit(SubmitPaymentCommand command);
}

