package com.fintech.gateway.infrastructure.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer error handling configuration.
 *
 * <p>Configures a {@link DefaultErrorHandler} with:
 * <ul>
 *   <li><strong>Retry policy:</strong> 3 attempts with 1-second fixed backoff</li>
 *   <li><strong>Dead Letter Topic (DLT):</strong> After retries exhausted, the failed
 *       message is published to {@code <original-topic>.DLT}</li>
 * </ul>
 *
 * @see DefaultErrorHandler
 * @see DeadLetterPublishingRecoverer
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);
    private static final long MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 1000;

    /**
     * Creates the common error handler with dead letter queue support.
     *
     * @param kafkaTemplate the Kafka template used to publish to DLT
     * @return the configured error handler
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) -> {
                    log.error("Message exhausted retries - sending to DLT: topic={}, key={}, error={}",
                            record.topic(), record.key(), ex.getMessage());
                    return new org.apache.kafka.common.TopicPartition(
                            record.topic() + ".DLT", record.partition());
                });

        FixedBackOff backOff = new FixedBackOff(RETRY_BACKOFF_MS, MAX_RETRY_ATTEMPTS);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Kafka consumer retry: topic={}, key={}, attempt={}, error={}",
                        record.topic(), record.key(), deliveryAttempt, ex.getMessage()));

        return errorHandler;
    }
}

