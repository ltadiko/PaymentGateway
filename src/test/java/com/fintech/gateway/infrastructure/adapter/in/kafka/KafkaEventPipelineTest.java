package com.fintech.gateway.infrastructure.adapter.in.kafka;

import com.fintech.gateway.application.service.FraudProcessingService;
import com.fintech.gateway.application.service.BankProcessingService;
import com.fintech.gateway.domain.event.PaymentEvent;
import com.fintech.gateway.infrastructure.config.KafkaTopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Kafka event publishing and consuming pipeline.
 *
 * <p>Uses {@code @EmbeddedKafka} to verify:
 * <ul>
 *   <li>{@code PaymentSubmitted} events are consumed by {@link FraudAssessmentConsumer}</li>
 *   <li>{@code FraudAssessmentCompleted} events are consumed by {@link BankProcessingConsumer}</li>
 *   <li>Event routing to the correct topics based on sealed type</li>
 * </ul>
 *
 * <p>Replaces the real processing services with spy implementations that
 * use {@link CountDownLatch} to signal when they've been called, avoiding
 * flaky timing-based assertions.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {
        KafkaTopicConfig.TOPIC_PAYMENT_SUBMITTED,
        KafkaTopicConfig.TOPIC_FRAUD_ASSESSED,
        KafkaTopicConfig.TOPIC_PAYMENT_COMPLETED
})
class KafkaEventPipelineTest {

    /** Latch signalled when fraud processing is invoked. */
    static CountDownLatch fraudLatch = new CountDownLatch(1);

    /** Latch signalled when bank processing is invoked. */
    static CountDownLatch bankLatch = new CountDownLatch(1);

    /** Captured payment ID from fraud processing call. */
    static volatile UUID capturedFraudPaymentId;

    /** Captured payment ID from bank processing call. */
    static volatile UUID capturedBankPaymentId;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Test configuration that provides spy implementations of the processing
     * services. These record invocations and signal the latches without
     * performing real database operations.
     */
    @TestConfiguration
    static class SpyConfig {

        @Bean
        @Primary
        public FraudProcessingService spyFraudProcessingService() {
            return new FraudProcessingService(null, null, null, null) {
                @Override
                public void processFraudCheck(UUID paymentId, String tenantId) {
                    capturedFraudPaymentId = paymentId;
                    fraudLatch.countDown();
                }
            };
        }

        @Bean
        @Primary
        public BankProcessingService spyBankProcessingService() {
            return new BankProcessingService(null, null, null, null) {
                @Override
                public void processBankPayment(UUID paymentId, String tenantId) {
                    capturedBankPaymentId = paymentId;
                    bankLatch.countDown();
                }
            };
        }
    }

    @Test
    @DisplayName("PaymentSubmitted event triggers FraudAssessmentConsumer")
    void shouldConsumPaymentSubmittedAndTriggerFraudCheck() throws Exception {
        // Reset latch for this test
        fraudLatch = new CountDownLatch(1);
        UUID paymentId = UUID.randomUUID();

        PaymentEvent.PaymentSubmitted event = new PaymentEvent.PaymentSubmitted(
                UUID.randomUUID(), paymentId, "tenant-001", Instant.now());

        kafkaTemplate.send(KafkaTopicConfig.TOPIC_PAYMENT_SUBMITTED,
                paymentId.toString(), event);

        boolean consumed = fraudLatch.await(15, TimeUnit.SECONDS);
        assertThat(consumed)
                .as("FraudAssessmentConsumer should have processed the event within 15 seconds")
                .isTrue();
        assertThat(capturedFraudPaymentId).isEqualTo(paymentId);
    }

    @Test
    @DisplayName("FraudAssessmentCompleted event triggers BankProcessingConsumer")
    void shouldConsumeFraudAssessedAndTriggerBankProcessing() throws Exception {
        // Reset latch for this test
        bankLatch = new CountDownLatch(1);
        UUID paymentId = UUID.randomUUID();

        PaymentEvent.FraudAssessmentCompleted event = new PaymentEvent.FraudAssessmentCompleted(
                UUID.randomUUID(), paymentId, "tenant-001",
                true, 15, null, Instant.now());

        kafkaTemplate.send(KafkaTopicConfig.TOPIC_FRAUD_ASSESSED,
                paymentId.toString(), event);

        boolean consumed = bankLatch.await(15, TimeUnit.SECONDS);
        assertThat(consumed)
                .as("BankProcessingConsumer should have processed the event within 15 seconds")
                .isTrue();
        assertThat(capturedBankPaymentId).isEqualTo(paymentId);
    }
}

