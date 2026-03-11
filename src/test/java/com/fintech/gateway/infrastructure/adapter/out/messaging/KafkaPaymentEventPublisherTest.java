package com.fintech.gateway.infrastructure.adapter.out.messaging;

import com.fintech.gateway.domain.event.PaymentEvent;
import com.fintech.gateway.infrastructure.config.KafkaTopicConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KafkaPaymentEventPublisher}.
 *
 * <p>Verifies correct topic routing for each event type in the
 * {@link PaymentEvent} sealed hierarchy.
 */
@ExtendWith(MockitoExtension.class)
class KafkaPaymentEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaPaymentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaPaymentEventPublisher(kafkaTemplate);

        // Mock kafkaTemplate.send to return a completed future
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("test", 0), 0, 0, 0L, 0, 0);
        SendResult<String, Object> sendResult = new SendResult<>(
                new ProducerRecord<>("test", "key", "value"), metadata);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
    }

    @Nested
    @DisplayName("Topic routing")
    class TopicRouting {

        @Test
        @DisplayName("PaymentSubmitted → payment.submitted topic")
        void shouldRoutePaymentSubmittedToCorrectTopic() {
            UUID paymentId = UUID.randomUUID();
            var event = new PaymentEvent.PaymentSubmitted(
                    UUID.randomUUID(), paymentId, "tenant-001", Instant.now());

            publisher.publish(event);

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), any());

            assertThat(topicCaptor.getValue()).isEqualTo(KafkaTopicConfig.TOPIC_PAYMENT_SUBMITTED);
            assertThat(keyCaptor.getValue()).isEqualTo(paymentId.toString());
        }

        @Test
        @DisplayName("FraudAssessmentCompleted (approved) → payment.fraud-assessed topic")
        void shouldRouteFraudApprovedToFraudAssessedTopic() {
            var event = new PaymentEvent.FraudAssessmentCompleted(
                    UUID.randomUUID(), UUID.randomUUID(), "tenant-001",
                    true, 15, null, Instant.now());

            publisher.publish(event);

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), any());

            assertThat(topicCaptor.getValue()).isEqualTo(KafkaTopicConfig.TOPIC_FRAUD_ASSESSED);
        }

        @Test
        @DisplayName("FraudAssessmentCompleted (rejected) → payment.completed topic")
        void shouldRouteFraudRejectedToCompletedTopic() {
            var event = new PaymentEvent.FraudAssessmentCompleted(
                    UUID.randomUUID(), UUID.randomUUID(), "tenant-001",
                    false, 85, "High risk", Instant.now());

            publisher.publish(event);

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), any());

            assertThat(topicCaptor.getValue()).isEqualTo(KafkaTopicConfig.TOPIC_PAYMENT_COMPLETED);
        }

        @Test
        @DisplayName("BankProcessingCompleted → payment.completed topic")
        void shouldRouteBankCompletedToCompletedTopic() {
            var event = new PaymentEvent.BankProcessingCompleted(
                    UUID.randomUUID(), UUID.randomUUID(), "tenant-001",
                    true, "BANK-REF-123", null, Instant.now());

            publisher.publish(event);

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), any());

            assertThat(topicCaptor.getValue()).isEqualTo(KafkaTopicConfig.TOPIC_PAYMENT_COMPLETED);
        }
    }

    @Nested
    @DisplayName("Message key")
    class MessageKey {

        @Test
        @DisplayName("Uses paymentId as message key for partition ordering")
        void shouldUsePaymentIdAsKey() {
            UUID paymentId = UUID.randomUUID();
            var event = new PaymentEvent.PaymentSubmitted(
                    UUID.randomUUID(), paymentId, "tenant-001", Instant.now());

            publisher.publish(event);

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(anyString(), keyCaptor.capture(), any());

            assertThat(keyCaptor.getValue()).isEqualTo(paymentId.toString());
        }
    }
}

