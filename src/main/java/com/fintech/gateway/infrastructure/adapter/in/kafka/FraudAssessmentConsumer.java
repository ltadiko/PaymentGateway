package com.fintech.gateway.infrastructure.adapter.in.kafka;

import com.fintech.gateway.application.service.FraudProcessingService;
import com.fintech.gateway.domain.event.PaymentEvent;
import com.fintech.gateway.infrastructure.config.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that triggers fraud assessment for newly submitted payments.
 *
 * <p>Listens on the {@code payment.submitted} topic and delegates to
 * {@link FraudProcessingService} for each event. If the fraud service
 * throws an exception, the error propagates to the Kafka error handler
 * which retries up to 3 times before sending the message to the DLT.
 *
 * <p>Consumer group: {@code fraud-assessment-group} — ensures each
 * submitted payment is assessed exactly once even with multiple instances.
 *
 * @see FraudProcessingService
 * @see com.fintech.gateway.infrastructure.config.KafkaConsumerConfig
 */
@Component
public class FraudAssessmentConsumer {

    private static final Logger log = LoggerFactory.getLogger(FraudAssessmentConsumer.class);

    private final FraudProcessingService fraudProcessingService;

    /**
     * Constructs the fraud assessment consumer.
     *
     * @param fraudProcessingService the service that orchestrates fraud checks
     */
    public FraudAssessmentConsumer(FraudProcessingService fraudProcessingService) {
        this.fraudProcessingService = fraudProcessingService;
    }

    /**
     * Consumes a {@code PaymentSubmitted} event and triggers fraud assessment.
     *
     * <p>If the fraud check fails with a transient error (e.g., network timeout),
     * the exception propagates and the Kafka error handler retries the message.
     * After 3 failed attempts, the message is sent to the dead letter topic
     * ({@code payment.submitted.DLT}).
     *
     * @param event the submitted payment event
     */
    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_PAYMENT_SUBMITTED,
            groupId = "fraud-assessment-group"
    )
    public void consume(PaymentEvent.PaymentSubmitted event) {
        log.info("Received PaymentSubmitted event: paymentId={}, tenantId={}",
                event.paymentId(), event.tenantId());

        fraudProcessingService.processFraudCheck(event.paymentId(), event.tenantId());

        log.info("Fraud assessment completed for paymentId={}", event.paymentId());
    }
}

