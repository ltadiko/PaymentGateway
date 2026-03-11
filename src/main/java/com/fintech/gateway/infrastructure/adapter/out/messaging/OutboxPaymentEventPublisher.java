package com.fintech.gateway.infrastructure.adapter.out.messaging;

import com.fintech.gateway.domain.event.PaymentEvent;
import com.fintech.gateway.domain.port.out.PaymentEventPublisher;
import com.fintech.gateway.infrastructure.adapter.out.persistence.SpringDataOutboxRepository;
import com.fintech.gateway.infrastructure.adapter.out.persistence.entity.OutboxEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox implementation of {@link PaymentEventPublisher}.
 *
 * <p>Instead of publishing directly to Kafka (which would be outside the DB transaction),
 * this publisher writes the event to the {@code outbox_events} table. Because it uses
 * the same JPA {@code EntityManager}, the event write is part of the caller's
 * {@code @Transactional} boundary.
 *
 * <p><strong>Guarantees:</strong> If the DB transaction commits, the event is guaranteed
 * to be in the outbox. If the transaction rolls back, the event is also rolled back.
 * No more "committed but not published" window.
 *
 * <p>The {@link OutboxPollerService} periodically reads unpublished events and relays
 * them to Kafka.
 *
 * <p>This bean is marked {@code @Primary} so it is preferred over
 * {@link KafkaPaymentEventPublisher} when auto-wiring {@link PaymentEventPublisher}.
 *
 * @see OutboxPollerService
 * @see KafkaPaymentEventPublisher
 */
@Component
@Primary
public class OutboxPaymentEventPublisher implements PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPaymentEventPublisher.class);

    private final SpringDataOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the outbox publisher.
     *
     * @param outboxRepository the outbox JPA repository
     * @param objectMapper     Jackson mapper for serialising events to JSON
     */
    public OutboxPaymentEventPublisher(SpringDataOutboxRepository outboxRepository,
                                       ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Writes the event to the outbox table within the current transaction.
     *
     * <p>The event is serialized to JSON and stored with metadata (aggregate type/ID,
     * event type) for the poller to route and deserialize later.
     *
     * @param event the domain event to persist for later Kafka relay
     */
    @Override
    public void publish(PaymentEvent event) {
        String eventType = event.getClass().getSimpleName();
        String payload = objectMapper.writeValueAsString(event);

        OutboxEventEntity entity = new OutboxEventEntity(
                UUID.randomUUID(),
                "Payment",
                event.paymentId(),
                eventType,
                payload,
                Instant.now(),
                false
        );

        outboxRepository.save(entity);

        log.debug("Event written to outbox: eventType={}, paymentId={}, outboxId={}",
                eventType, event.paymentId(), entity.getId());
    }
}

