package com.fintech.gateway.infrastructure.adapter.out.messaging;

import com.fintech.gateway.domain.event.PaymentEvent;
import com.fintech.gateway.domain.port.out.PaymentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logging-only fallback implementation of {@link PaymentEventPublisher}.
 *
 * <p>Used when Kafka is not available (e.g., in unit tests or local development
 * without Docker). The primary implementation is
 * {@link com.fintech.gateway.infrastructure.adapter.out.messaging.KafkaPaymentEventPublisher}.
 *
 * <p>Activate by setting {@code spring.kafka.enabled=false} or by using a
 * {@code @ConditionalOnMissingBean} annotation on the Kafka publisher.
 */
public class LoggingPaymentEventPublisher implements PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingPaymentEventPublisher.class);

    /**
     * Logs the event details. In later steps, this will publish to Kafka topics.
     *
     * @param event the domain event to publish
     */
    @Override
    public void publish(PaymentEvent event) {
        String topic = resolveTopicName(event);
        log.info("Publishing event to [{}]: type={}, paymentId={}, tenantId={}",
                topic, event.getClass().getSimpleName(), event.paymentId(), event.tenantId());
    }

    /**
     * Determines the Kafka topic for a given event using pattern matching.
     *
     * @param event the domain event
     * @return the topic name
     */
    private String resolveTopicName(PaymentEvent event) {
        return switch (event) {
            case PaymentEvent.PaymentSubmitted ps -> "payment.submitted";
            case PaymentEvent.FraudAssessmentCompleted fac ->
                    fac.approved() ? "payment.fraud-assessed" : "payment.completed";
            case PaymentEvent.BankProcessingCompleted bpc -> "payment.completed";
        };
    }
}

