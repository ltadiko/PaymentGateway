package com.fintech.gateway.infrastructure.adapter.in.kafka;

import com.fintech.gateway.application.service.BankProcessingService;
import com.fintech.gateway.domain.event.PaymentEvent;
import com.fintech.gateway.infrastructure.config.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that triggers bank processing for fraud-approved payments.
 *
 * <p>Listens on the {@code payment.fraud-assessed} topic. Only approved
 * payments reach this topic (rejected payments go directly to
 * {@code payment.completed}).
 *
 * <p>If the bank gateway throws an exception, the Kafka error handler
 * retries up to 3 times before sending the message to the DLT
 * ({@code payment.fraud-assessed.DLT}).
 *
 * <p>Consumer group: {@code bank-processing-group} — ensures each
 * approved payment is processed by the bank exactly once.
 *
 * @see BankProcessingService
 * @see com.fintech.gateway.infrastructure.config.KafkaConsumerConfig
 */
@Component
public class BankProcessingConsumer {

    private static final Logger log = LoggerFactory.getLogger(BankProcessingConsumer.class);

    private final BankProcessingService bankProcessingService;

    /**
     * Constructs the bank processing consumer.
     *
     * @param bankProcessingService the service that orchestrates bank processing
     */
    public BankProcessingConsumer(BankProcessingService bankProcessingService) {
        this.bankProcessingService = bankProcessingService;
    }

    /**
     * Consumes a {@code FraudAssessmentCompleted} event and triggers bank processing.
     *
     * <p>Only approved fraud assessments reach this topic. The bank processing
     * service handles the variable-latency bank call and updates the payment
     * to either COMPLETED or FAILED.
     *
     * @param event the fraud assessment completed event
     */
    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_FRAUD_ASSESSED,
            groupId = "bank-processing-group"
    )
    public void consume(PaymentEvent.FraudAssessmentCompleted event) {
        log.info("Received FraudAssessmentCompleted event: paymentId={}, tenantId={}, approved={}",
                event.paymentId(), event.tenantId(), event.approved());

        bankProcessingService.processBankPayment(event.paymentId(), event.tenantId());

        log.info("Bank processing completed for paymentId={}", event.paymentId());
    }
}

