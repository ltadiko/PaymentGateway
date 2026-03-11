package com.fintech.gateway.domain.port.out;

import com.fintech.gateway.domain.model.Payment;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and retrieving payment aggregates.
 *
 * <p>Implementations are responsible for mapping between the domain
 * {@link Payment} object and the underlying persistence mechanism
 * (e.g., JPA entities). Sensitive fields (account numbers) must be
 * encrypted at the persistence layer.
 *
 * <p>All queries include a {@code tenantId} filter to enforce tenant
 * isolation at the data access level.
 *
 * @see com.fintech.gateway.domain.model.Payment
 */
public interface PaymentRepository {

    /**
     * Persists a payment aggregate (insert or update).
     *
     * @param payment the payment domain object to persist
     * @return the persisted payment (with any generated fields populated)
     */
    Payment save(Payment payment);

    /**
     * Finds a payment by its ID, scoped to a specific tenant.
     *
     * <p>Returns {@link Optional#empty()} if the payment does not exist
     * or belongs to a different tenant — preventing cross-tenant data leakage.
     *
     * @param id       the unique payment identifier
     * @param tenantId the tenant who owns the payment
     * @return the payment if found and owned by the tenant, empty otherwise
     */
    Optional<Payment> findByIdAndTenantId(UUID id, String tenantId);
}

