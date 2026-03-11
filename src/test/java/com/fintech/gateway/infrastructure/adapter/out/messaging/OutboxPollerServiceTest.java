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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OutboxPollerService}.
 *
 * <p>Verifies that the poller correctly reads unpublished events, deserializes them,
 * relays them to Kafka, and marks them as published.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPollerService")
class OutboxPollerServiceTest {

    @Mock
    private SpringDataOutboxRepository outboxRepository;

    @Mock
    private KafkaPaymentEventPublisher kafkaPublisher;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private OutboxPollerService pollerService;

    @BeforeEach
    void setUp() {
        pollerService = new OutboxPollerService(outboxRepository, kafkaPublisher, objectMapper);
    }

    @Test
    @DisplayName("Does nothing when no unpublished events exist")
    void shouldDoNothingWhenNoEvents() {
        when(outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(Collections.emptyList());

        pollerService.pollAndPublish();

        verify(kafkaPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Relays PaymentSubmitted event from outbox to Kafka")
    void shouldRelayPaymentSubmittedEvent() {
        UUID paymentId = UUID.randomUUID();
        var event = new PaymentEvent.PaymentSubmitted(
                UUID.randomUUID(), paymentId, "tenant-001", Instant.now());
        String payload = objectMapper.writeValueAsString(event);

        OutboxEventEntity outboxEvent = new OutboxEventEntity(
                UUID.randomUUID(), "Payment", paymentId,
                "PaymentSubmitted", payload, Instant.now(), false);

        when(outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(outboxEvent));

        pollerService.pollAndPublish();

        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(kafkaPublisher).publish(captor.capture());

        assertThat(captor.getValue()).isInstanceOf(PaymentEvent.PaymentSubmitted.class);
        PaymentEvent.PaymentSubmitted relayed = (PaymentEvent.PaymentSubmitted) captor.getValue();
        assertThat(relayed.paymentId()).isEqualTo(paymentId);
        assertThat(relayed.tenantId()).isEqualTo("tenant-001");
        assertThat(outboxEvent.isPublished()).isTrue();
        verify(outboxRepository).save(outboxEvent);
    }

    @Test
    @DisplayName("Relays FraudAssessmentCompleted event from outbox to Kafka")
    void shouldRelayFraudAssessmentCompletedEvent() {
        UUID paymentId = UUID.randomUUID();
        var event = new PaymentEvent.FraudAssessmentCompleted(
                UUID.randomUUID(), paymentId, "tenant-001",
                false, 85, "High risk", Instant.now());
        String payload = objectMapper.writeValueAsString(event);

        OutboxEventEntity outboxEvent = new OutboxEventEntity(
                UUID.randomUUID(), "Payment", paymentId,
                "FraudAssessmentCompleted", payload, Instant.now(), false);

        when(outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(outboxEvent));

        pollerService.pollAndPublish();

        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(kafkaPublisher).publish(captor.capture());

        assertThat(captor.getValue()).isInstanceOf(PaymentEvent.FraudAssessmentCompleted.class);
        PaymentEvent.FraudAssessmentCompleted relayed =
                (PaymentEvent.FraudAssessmentCompleted) captor.getValue();
        assertThat(relayed.approved()).isFalse();
        assertThat(relayed.fraudScore()).isEqualTo(85);
    }

    @Test
    @DisplayName("Continues processing batch when one event fails")
    void shouldContinueProcessingWhenOneEventFails() {
        UUID paymentId1 = UUID.randomUUID();
        UUID paymentId2 = UUID.randomUUID();

        var event1 = new PaymentEvent.PaymentSubmitted(
                UUID.randomUUID(), paymentId1, "tenant-001", Instant.now());
        var event2 = new PaymentEvent.PaymentSubmitted(
                UUID.randomUUID(), paymentId2, "tenant-001", Instant.now());

        // First event has invalid JSON — will fail deserialization
        OutboxEventEntity failingEvent = new OutboxEventEntity(
                UUID.randomUUID(), "Payment", paymentId1,
                "PaymentSubmitted", "invalid-json", Instant.now(), false);

        OutboxEventEntity goodEvent = new OutboxEventEntity(
                UUID.randomUUID(), "Payment", paymentId2,
                "PaymentSubmitted", objectMapper.writeValueAsString(event2),
                Instant.now(), false);

        when(outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(failingEvent, goodEvent));

        pollerService.pollAndPublish();

        // The good event should still be published despite the first one failing
        verify(kafkaPublisher, times(1)).publish(any());
        assertThat(failingEvent.isPublished()).isFalse();
        assertThat(goodEvent.isPublished()).isTrue();
    }

    @Test
    @DisplayName("Does not mark event as published when Kafka publish fails")
    void shouldNotMarkPublishedWhenKafkaFails() {
        UUID paymentId = UUID.randomUUID();
        var event = new PaymentEvent.PaymentSubmitted(
                UUID.randomUUID(), paymentId, "tenant-001", Instant.now());

        OutboxEventEntity outboxEvent = new OutboxEventEntity(
                UUID.randomUUID(), "Payment", paymentId,
                "PaymentSubmitted", objectMapper.writeValueAsString(event),
                Instant.now(), false);

        when(outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(outboxEvent));
        doThrow(new RuntimeException("Kafka down")).when(kafkaPublisher).publish(any());

        pollerService.pollAndPublish();

        assertThat(outboxEvent.isPublished()).isFalse();
        verify(outboxRepository, never()).save(outboxEvent);
    }
}

