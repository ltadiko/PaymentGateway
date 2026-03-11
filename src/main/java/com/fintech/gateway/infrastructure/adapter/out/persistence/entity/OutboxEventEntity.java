package com.fintech.gateway.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an outbox event for the Transactional Outbox Pattern.
 *
 * <p>Events are written to this table inside the same {@code @Transactional} boundary
 * as domain state changes. A scheduled poller reads unpublished events, publishes
 * them to Kafka, and marks them as published. This eliminates the window where a
 * crash between DB commit and Kafka publish would lose events.
 *
 * <p><strong>Lifecycle:</strong>
 * <ol>
 *   <li>Service writes event → {@code published = false}</li>
 *   <li>Poller reads event → publishes to Kafka → {@code published = true}</li>
 *   <li>Cleanup job deletes published events older than retention period</li>
 * </ol>
 *
 * @see com.fintech.gateway.infrastructure.adapter.out.messaging.OutboxPaymentEventPublisher
 * @see com.fintech.gateway.infrastructure.adapter.out.messaging.OutboxPollerService
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_published_created", columnList = "published, createdAt")
})
public class OutboxEventEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Aggregate type (e.g., "Payment"). Used for logical grouping. */
    @Column(nullable = false, updatable = false, length = 100)
    private String aggregateType;

    /** Aggregate ID (e.g., the paymentId). Used as Kafka message key. */
    @Column(nullable = false, updatable = false)
    private UUID aggregateId;

    /** Event type (e.g., "PaymentSubmitted"). Used for deserialization routing. */
    @Column(nullable = false, updatable = false, length = 100)
    private String eventType;

    /** JSON-serialized event payload. */
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    /** When the event was created. */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Whether the event has been successfully published to Kafka. */
    @Column(nullable = false)
    private boolean published;

    /** Protected no-arg constructor for JPA. */
    protected OutboxEventEntity() {
    }

    /**
     * Constructs an outbox event.
     *
     * @param id            unique event identifier
     * @param aggregateType the aggregate type (e.g., "Payment")
     * @param aggregateId   the aggregate ID (e.g., paymentId)
     * @param eventType     the event type name for deserialization
     * @param payload       JSON-serialized event payload
     * @param createdAt     creation timestamp
     * @param published     whether the event has been published
     */
    public OutboxEventEntity(UUID id, String aggregateType, UUID aggregateId,
                             String eventType, String payload,
                             Instant createdAt, boolean published) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
        this.published = published;
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isPublished() { return published; }

    /**
     * Marks this event as successfully published to Kafka.
     */
    public void markPublished() {
        this.published = true;
    }
}

