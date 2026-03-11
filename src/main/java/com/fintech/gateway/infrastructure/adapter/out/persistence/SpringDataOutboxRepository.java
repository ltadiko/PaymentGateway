package com.fintech.gateway.infrastructure.adapter.out.persistence;

import com.fintech.gateway.infrastructure.adapter.out.persistence.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for outbox events.
 *
 * <p>Provides query methods for the outbox poller:
 * <ul>
 *   <li>{@link #findTop100ByPublishedFalseOrderByCreatedAtAsc()} — batch polling</li>
 *   <li>{@link #deleteByPublishedTrueAndCreatedAtBefore(Instant)} — cleanup of old events</li>
 * </ul>
 *
 * @see OutboxEventEntity
 */
@Repository
public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Finds the oldest 100 unpublished events for batch relay to Kafka.
     *
     * @return up to 100 unpublished events ordered by creation time (FIFO)
     */
    List<OutboxEventEntity> findTop100ByPublishedFalseOrderByCreatedAtAsc();

    /**
     * Deletes published events older than the given cutoff for storage cleanup.
     *
     * @param cutoff events created before this time are deleted
     * @return the number of deleted events
     */
    long deleteByPublishedTrueAndCreatedAtBefore(Instant cutoff);
}

