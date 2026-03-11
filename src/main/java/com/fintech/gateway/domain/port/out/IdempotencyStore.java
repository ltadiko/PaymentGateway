package com.fintech.gateway.domain.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for managing idempotency keys.
 *
 * <p>Ensures that duplicate payment submissions (caused by client retries
 * or network partitions) return the original response without creating
 * a new payment. Keys are scoped by {@code tenantId} so different tenants
 * can independently use the same key value.
 *
 * <p>The store is backed by a database table with a unique constraint on
 * {@code (tenant_id, idempotency_key)} to handle race conditions at the
 * database level.
 *
 * @see com.fintech.gateway.application.service.PaymentIngestionService
 */
public interface IdempotencyStore {

    /**
     * Looks up a previously stored response for the given idempotency key.
     *
     * @param tenantId       the tenant making the request
     * @param idempotencyKey the client-provided idempotency key (UUID v4)
     * @return the serialized original response body if found, empty otherwise
     */
    Optional<String> findResponse(String tenantId, String idempotencyKey);

    /**
     * Stores an idempotency key with its associated response.
     *
     * <p>If a concurrent request inserts the same key simultaneously, the
     * database unique constraint will cause a {@code DataIntegrityViolationException},
     * which the caller must handle by re-querying the existing response.
     *
     * @param tenantId       the tenant making the request
     * @param idempotencyKey the client-provided idempotency key
     * @param paymentId      the ID of the created payment
     * @param httpStatus     the HTTP status code of the original response (e.g., 202)
     * @param responseBody   the serialized JSON response body
     */
    void store(String tenantId, String idempotencyKey, UUID paymentId,
               int httpStatus, String responseBody);
}

