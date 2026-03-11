package com.fintech.gateway.infrastructure.adapter.out.messaging;

import com.fintech.gateway.domain.event.PaymentEvent;
import com.fintech.gateway.domain.port.out.PaymentEventPublisher;
import com.fintech.gateway.infrastructure.config.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka implementation of {@link PaymentEventPublisher}.
 *
 * <p>Routes domain events to the appropriate Kafka topic using pattern matching
 * on the {@link PaymentEvent} sealed hierarchy:
 * <ul>
 *   <li>{@code PaymentSubmitted} → {@code payment.submitted} topic</li>
 *   <li>{@code FraudAssessmentCompleted} (approved) → {@code payment.fraud-assessed} topic</li>
 *   <li>{@code FraudAssessmentCompleted} (rejected) → {@code payment.completed} topic</li>
 *   <li>{@code BankProcessingCompleted} → {@code payment.completed} topic</li>
 * </ul>
 *
 * <p>All messages are keyed by {@code paymentId.toString()} to guarantee
 * per-payment ordering within Kafka partitions.
 *
 * <p>Publishing is fire-and-forget with logging on failure. The application
 * service has already committed the database transaction before publishing,
 * so a failed publish does not affect data consistency. The event can be
 * replayed from the audit trail if needed.
 *
 * @see KafkaTopicConfig
 */
@Component
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaPaymentEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Constructs the Kafka event publisher.
     *
     * @param kafkaTemplate the Spring Kafka template for sending messages
     */
    public KafkaPaymentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a domain event to the appropriate Kafka topic.
     *
     * <p>Uses pattern matching on the sealed {@link PaymentEvent} hierarchy
     * to determine the target topic. The message key is the payment ID,
     * ensuring all events for a single payment land in the same partition.
     *
     * @param event the domain event to publish
     */
    @Override
    public void publish(PaymentEvent event) {
        String topic = resolveTopicName(event);
        String key = event.paymentId().toString();

        log.info("Publishing event to Kafka: topic={}, key={}, type={}",
                topic, key, event.getClass().getSimpleName());

        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event to Kafka: topic={}, key={}, type={}, error={}",
                                topic, key, event.getClass().getSimpleName(), ex.getMessage(), ex);
                    } else {
                        log.debug("Event published successfully: topic={}, key={}, partition={}, offset={}",
                                topic, key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Determines the Kafka topic for a given event using pattern matching.
     *
     * <p>Routing logic:
     * <ul>
     *   <li>{@code PaymentSubmitted} → triggers fraud assessment</li>
     *   <li>{@code FraudAssessmentCompleted} (approved) → triggers bank processing</li>
     *   <li>{@code FraudAssessmentCompleted} (rejected) → terminal, no further processing</li>
     *   <li>{@code BankProcessingCompleted} → terminal event</li>
     * </ul>
     *
     * @param event the domain event
     * @return the target Kafka topic name
     */
    private String resolveTopicName(PaymentEvent event) {
        return switch (event) {
            case PaymentEvent.PaymentSubmitted ps ->
                    KafkaTopicConfig.TOPIC_PAYMENT_SUBMITTED;
            case PaymentEvent.FraudAssessmentCompleted fac ->
                    fac.approved() ? KafkaTopicConfig.TOPIC_FRAUD_ASSESSED
                                   : KafkaTopicConfig.TOPIC_PAYMENT_COMPLETED;
            case PaymentEvent.BankProcessingCompleted bpc ->
                    KafkaTopicConfig.TOPIC_PAYMENT_COMPLETED;
        };
    }
}

