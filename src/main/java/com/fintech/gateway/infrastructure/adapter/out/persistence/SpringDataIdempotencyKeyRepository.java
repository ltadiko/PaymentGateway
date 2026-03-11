package com.fintech.gateway.infrastructure.adapter.out.persistence;

import com.fintech.gateway.infrastructure.adapter.out.persistence.entity.IdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link IdempotencyKeyEntity}.
 *
 * <p>Supports tenant-scoped idempotency key lookups and
 * TTL-based cleanup of expired keys.
 */
public interface SpringDataIdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

    /**
     * Finds an idempotency key entry by tenant and key.
     *
     * @param tenantId       the tenant identifier
     * @param idempotencyKey the client-provided idempotency key
     * @return the stored entry if found
     */
    Optional<IdempotencyKeyEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    /**
     * Deletes expired idempotency keys (created before the given cutoff).
     *
     * <p>Called by a scheduled cleanup job to prevent unbounded table growth.
     *
     * @param cutoff the expiry cutoff timestamp
     */
    void deleteByCreatedAtBefore(Instant cutoff);
}

