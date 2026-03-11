package com.fintech.gateway.infrastructure.adapter.out.persistence;

import com.fintech.gateway.domain.port.out.IdempotencyStore;
import com.fintech.gateway.infrastructure.adapter.out.persistence.entity.IdempotencyKeyEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter implementing the {@link IdempotencyStore} domain port using Spring Data JPA.
 *
 * <p>The database unique constraint on {@code (tenant_id, idempotency_key)} provides
 * the ultimate guard against duplicate submissions. If two concurrent requests
 * attempt to insert the same key, one will succeed and the other will receive
 * a {@code DataIntegrityViolationException}, which the calling service handles
 * by re-querying the existing response.
 *
 * @see IdempotencyStore
 */
@Component
public class IdempotencyStoreAdapter implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyStoreAdapter.class);

    private final SpringDataIdempotencyKeyRepository repository;

    /**
     * Constructs the adapter.
     *
     * @param repository the Spring Data JPA repository
     */
    public IdempotencyStoreAdapter(SpringDataIdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> findResponse(String tenantId, String idempotencyKey) {
        Optional<String> result = repository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                .map(IdempotencyKeyEntity::getResponseBody);
        log.debug("Idempotency lookup: tenantId={}, key={}, found={}", tenantId, idempotencyKey, result.isPresent());
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Persists the idempotency key with a generated UUID and current timestamp.
     */
    @Override
    public void store(String tenantId, String idempotencyKey, UUID paymentId,
                      int httpStatus, String responseBody) {
        var entity = new IdempotencyKeyEntity(
                UUID.randomUUID(),
                tenantId,
                idempotencyKey,
                paymentId,
                httpStatus,
                responseBody,
                Instant.now()
        );
        repository.save(entity);
        log.debug("Idempotency key stored: tenantId={}, key={}, paymentId={}", tenantId, idempotencyKey, paymentId);
    }
}

