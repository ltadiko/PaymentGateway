package com.fintech.gateway.infrastructure.adapter.out.persistence;

import com.fintech.gateway.infrastructure.adapter.out.persistence.entity.AuditTrailEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AuditTrailEntity}.
 *
 * <p>Supports tenant-scoped retrieval of audit entries
 * ordered chronologically.
 */
public interface SpringDataAuditTrailRepository extends JpaRepository<AuditTrailEntity, UUID> {

    /**
     * Finds all audit entries for a payment, ordered by creation time ascending.
     *
     * <p>Results are scoped by tenant ID to enforce multi-tenancy isolation.
     *
     * @param paymentId the payment identifier
     * @param tenantId  the tenant identifier
     * @return chronologically ordered audit entries
     */
    List<AuditTrailEntity> findByPaymentIdAndTenantIdOrderByCreatedAtAsc(UUID paymentId, String tenantId);
}

