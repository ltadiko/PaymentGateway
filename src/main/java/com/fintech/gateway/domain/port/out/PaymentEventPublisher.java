package com.fintech.gateway.domain.port.out;

import com.fintech.gateway.domain.event.PaymentEvent;

/**
 * Outbound port for publishing domain events to the message broker.
 *
 * <p>Events are published after the corresponding database transaction commits
 * to avoid the "published but not persisted" problem. If publishing fails,
 * the payment is still safely persisted and can be retried.
 *
 * <p>Event routing is determined by the event type using pattern matching
 * on the {@link PaymentEvent} sealed hierarchy:
 * <ul>
 *   <li>{@code PaymentSubmitted} → {@code payment.submitted} topic</li>
 *   <li>{@code FraudAssessmentCompleted} (approved) → {@code payment.fraud-assessed} topic</li>
 *   <li>{@code FraudAssessmentCompleted} (rejected) → {@code payment.completed} topic</li>
 *   <li>{@code BankProcessingCompleted} → {@code payment.completed} topic</li>
 * </ul>
 *
 * <p>All messages are keyed by {@code paymentId} to guarantee per-payment
 * ordering within Kafka partitions.
 *
 * @see com.fintech.gateway.domain.event.PaymentEvent
 */
public interface PaymentEventPublisher {

    /**
     * Publishes a domain event to the appropriate message broker topic.
     *
     * @param event the domain event to publish (must not be null)
     */
    void publish(PaymentEvent event);
}

