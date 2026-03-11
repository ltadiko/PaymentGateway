package com.fintech.gateway.infrastructure.adapter.out.messaging;

import com.fintech.gateway.domain.event.PaymentEvent;
import com.fintech.gateway.infrastructure.adapter.out.persistence.SpringDataOutboxRepository;
import com.fintech.gateway.infrastructure.adapter.out.persistence.entity.OutboxEventEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link OutboxPaymentEventPublisher}.
 *
 * <p>Verifies that domain events are correctly serialized and written to the outbox
 * table rather than being sent directly to Kafka.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPaymentEventPublisher")
class OutboxPaymentEventPublisherTest {

    @Mock
    private SpringDataOutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private OutboxPaymentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPaymentEventPublisher(outboxRepository, objectMapper);
    }

    @Test
    @DisplayName("Writes PaymentSubmitted event to outbox with correct metadata")
    void shouldWritePaymentSubmittedToOutbox() {
        UUID paymentId = UUID.randomUUID();
        var event = new PaymentEvent.PaymentSubmitted(
                UUID.randomUUID(), paymentId, "tenant-001", Instant.now());

        publisher.publish(event);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEventEntity saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("Payment");
        assertThat(saved.getAggregateId()).isEqualTo(paymentId);
        assertThat(saved.getEventType()).isEqualTo("PaymentSubmitted");
        assertThat(saved.isPublished()).isFalse();
        assertThat(saved.getPayload()).contains(paymentId.toString());
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Writes FraudAssessmentCompleted event to outbox with correct metadata")
    void shouldWriteFraudAssessmentCompletedToOutbox() {
        UUID paymentId = UUID.randomUUID();
        var event = new PaymentEvent.FraudAssessmentCompleted(
                UUID.randomUUID(), paymentId, "tenant-001",
                true, 25, null, Instant.now());

        publisher.publish(event);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEventEntity saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("FraudAssessmentCompleted");
        assertThat(saved.getAggregateId()).isEqualTo(paymentId);
        assertThat(saved.isPublished()).isFalse();
    }

    @Test
    @DisplayName("Writes BankProcessingCompleted event to outbox with correct metadata")
    void shouldWriteBankProcessingCompletedToOutbox() {
        UUID paymentId = UUID.randomUUID();
        var event = new PaymentEvent.BankProcessingCompleted(
                UUID.randomUUID(), paymentId, "tenant-001",
                true, "BNK-REF-123", null, Instant.now());

        publisher.publish(event);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEventEntity saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("BankProcessingCompleted");
        assertThat(saved.getAggregateId()).isEqualTo(paymentId);
        assertThat(saved.getPayload()).contains("BNK-REF-123");
    }

    @Test
    @DisplayName("Serialized payload can be deserialized back to the original event")
    void shouldProduceDeserializablePayload() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        var event = new PaymentEvent.PaymentSubmitted(eventId, paymentId, "tenant-001", timestamp);

        publisher.publish(event);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());

        String payload = captor.getValue().getPayload();
        PaymentEvent.PaymentSubmitted deserialized =
                objectMapper.readValue(payload, PaymentEvent.PaymentSubmitted.class);

        assertThat(deserialized.eventId()).isEqualTo(eventId);
        assertThat(deserialized.paymentId()).isEqualTo(paymentId);
        assertThat(deserialized.tenantId()).isEqualTo("tenant-001");
    }
}

