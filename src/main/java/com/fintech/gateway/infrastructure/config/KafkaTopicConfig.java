package com.fintech.gateway.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration — creates topics on application startup.
 *
 * <p>Topic design follows the event-driven architecture:
 * <ul>
 *   <li>{@code payment.submitted} — triggers fraud assessment pipeline</li>
 *   <li>{@code payment.fraud-assessed} — triggers bank processing (only for approved payments)</li>
 *   <li>{@code payment.completed} — terminal events (completed, failed, fraud-rejected)</li>
 * </ul>
 *
 * <p>Each topic has 3 partitions for parallelism. Messages are keyed by
 * {@code paymentId} to guarantee per-payment ordering within a partition.
 *
 * <p>Dead letter topics ({@code *.DLT}) are automatically created by the
 * {@link KafkaConsumerConfig} error handler when retries are exhausted.
 */
@Configuration
public class KafkaTopicConfig {

    /** Topic for newly submitted payments — consumed by fraud assessment. */
    public static final String TOPIC_PAYMENT_SUBMITTED = "payment.submitted";

    /** Topic for fraud-approved payments — consumed by bank processing. */
    public static final String TOPIC_FRAUD_ASSESSED = "payment.fraud-assessed";

    /** Topic for terminal payment events (completed, failed, rejected). */
    public static final String TOPIC_PAYMENT_COMPLETED = "payment.completed";

    /**
     * Creates the {@code payment.submitted} topic.
     *
     * @return the topic configuration
     */
    @Bean
    public NewTopic paymentSubmittedTopic() {
        return TopicBuilder.name(TOPIC_PAYMENT_SUBMITTED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Creates the {@code payment.fraud-assessed} topic.
     *
     * @return the topic configuration
     */
    @Bean
    public NewTopic fraudAssessedTopic() {
        return TopicBuilder.name(TOPIC_FRAUD_ASSESSED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Creates the {@code payment.completed} topic.
     *
     * @return the topic configuration
     */
    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(TOPIC_PAYMENT_COMPLETED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

