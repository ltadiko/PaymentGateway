package com.fintech.gateway.infrastructure.adapter.out.messaging;

import com.fintech.gateway.domain.event.PaymentEvent;
import com.fintech.gateway.infrastructure.adapter.out.persistence.SpringDataOutboxRepository;
import com.fintech.gateway.infrastructure.adapter.out.persistence.entity.OutboxEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled poller that relays outbox events to Kafka.
 *
 * <p>Implements the relay side of the Transactional Outbox Pattern:
 * <ol>
 *   <li>Polls for unpublished events (batch of up to 100)</li>
 *   <li>Deserializes each event from JSON back to the domain {@link PaymentEvent}</li>
 *   <li>Publishes to Kafka via {@link KafkaPaymentEventPublisher}</li>
 *   <li>Marks the outbox entry as published</li>
 * </ol>
 *
 * <p><strong>Failure handling:</strong> Each event is processed independently.
 * If one event fails to publish (e.g., Kafka is temporarily down), it remains
 * unpublished and will be retried on the next poll cycle. Other events in the
 * batch are not affected.
 *
 * <p><strong>At-least-once semantics:</strong> If the app crashes after Kafka
 * publish but before marking the outbox entry as published, the event will be
 * re-published on the next poll. Downstream consumers must be idempotent
 * (which they already are via {@code eventId}).
 *
 * <p><strong>Cleanup:</strong> A separate scheduled job deletes published events
 * older than the configured retention period to prevent unbounded table growth.
 *
 * @see OutboxPaymentEventPublisher
 * @see KafkaPaymentEventPublisher
 */
@Component
public class OutboxPollerService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollerService.class);

    private final SpringDataOutboxRepository outboxRepository;
    private final KafkaPaymentEventPublisher kafkaPublisher;
    private final ObjectMapper objectMapper;

    @Value("${app.outbox.retention-days:7}")
    private int retentionDays;

    /**
     * Constructs the outbox poller.
     *
     * @param outboxRepository the outbox JPA repository
     * @param kafkaPublisher   the Kafka publisher for actual event relay
     * @param objectMapper     Jackson mapper for deserialising outbox payloads
     */
    public OutboxPollerService(SpringDataOutboxRepository outboxRepository,
                               KafkaPaymentEventPublisher kafkaPublisher,
                               ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaPublisher = kafkaPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Polls for unpublished outbox events and relays them to Kafka.
     *
     * <p>Runs on a fixed delay (default: 1 second). Each event is processed
     * independently — a single failure does not block other events.
     */
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    @Transactional
    public void pollAndPublish() {
        List<OutboxEventEntity> events = outboxRepository
                .findTop100ByPublishedFalseOrderByCreatedAtAsc();

        if (events.isEmpty()) {
            return;
        }

        log.info("Outbox poller: found {} unpublished events", events.size());

        int published = 0;
        int failed = 0;

        for (OutboxEventEntity outboxEvent : events) {
            try {
                PaymentEvent domainEvent = deserializeEvent(outboxEvent);
                kafkaPublisher.publish(domainEvent);
                outboxEvent.markPublished();
                outboxRepository.save(outboxEvent);
                published++;

                log.debug("Outbox event relayed: outboxId={}, eventType={}, paymentId={}",
                        outboxEvent.getId(), outboxEvent.getEventType(), outboxEvent.getAggregateId());

            } catch (Exception e) {
                failed++;
                log.error("Outbox relay failed: outboxId={}, eventType={}, paymentId={}, error={}",
                        outboxEvent.getId(), outboxEvent.getEventType(),
                        outboxEvent.getAggregateId(), e.getMessage(), e);
                // Do NOT rethrow — continue processing other events
            }
        }

        log.info("Outbox poller completed: published={}, failed={}", published, failed);
    }

    /**
     * Cleans up published outbox events older than the retention period.
     *
     * <p>Runs once per hour. Prevents the outbox table from growing unbounded.
     */
    @Scheduled(fixedDelayString = "${app.outbox.cleanup-interval-ms:3600000}")
    @Transactional
    public void cleanup() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        long deleted = outboxRepository.deleteByPublishedTrueAndCreatedAtBefore(cutoff);

        if (deleted > 0) {
            log.info("Outbox cleanup: deleted {} published events older than {} days", deleted, retentionDays);
        }
    }

    /**
     * Deserializes an outbox event payload back to the correct {@link PaymentEvent} record.
     *
     * <p>Uses the stored {@code eventType} to determine the concrete sealed type,
     * keeping the domain model free of Jackson polymorphic annotations.
     *
     * @param outboxEvent the outbox entity
     * @return the deserialized domain event
     * @throws IllegalArgumentException if the event type is unknown
     */
    private PaymentEvent deserializeEvent(OutboxEventEntity outboxEvent) {
        String payload = outboxEvent.getPayload();
        String eventType = outboxEvent.getEventType();

        return switch (eventType) {
            case "PaymentSubmitted" ->
                    objectMapper.readValue(payload, PaymentEvent.PaymentSubmitted.class);
            case "FraudAssessmentCompleted" ->
                    objectMapper.readValue(payload, PaymentEvent.FraudAssessmentCompleted.class);
            case "BankProcessingCompleted" ->
                    objectMapper.readValue(payload, PaymentEvent.BankProcessingCompleted.class);
            default -> throw new IllegalArgumentException("Unknown outbox event type: " + eventType);
        };
    }
}

