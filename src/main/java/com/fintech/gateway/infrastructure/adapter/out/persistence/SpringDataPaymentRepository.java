package com.fintech.gateway.infrastructure.adapter.out.persistence;

import com.fintech.gateway.infrastructure.adapter.out.persistence.entity.PaymentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PaymentJpaEntity}.
 *
 * <p>Provides CRUD operations and a tenant-scoped query method
 * that enforces data isolation between tenants.
 */
public interface SpringDataPaymentRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    /**
     * Finds a payment by its ID, scoped to a specific tenant.
     *
     * <p>Returns empty if the payment does not exist or belongs to a different tenant.
     *
     * @param id       the payment identifier
     * @param tenantId the tenant identifier
     * @return the payment entity if found and owned by the tenant
     */
    Optional<PaymentJpaEntity> findByIdAndTenantId(UUID id, String tenantId);
}

