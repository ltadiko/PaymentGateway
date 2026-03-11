package com.fintech.gateway.infrastructure.adapter.out.persistence;

import com.fintech.gateway.domain.model.AuditEntry;
import com.fintech.gateway.domain.port.out.AuditTrailStore;
import com.fintech.gateway.infrastructure.adapter.out.persistence.entity.AuditTrailEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Adapter implementing the {@link AuditTrailStore} domain port using Spring Data JPA.
 *
 * <p>Provides append-only semantics — the {@link AuditTrailEntity} is immutable
 * (no setters) and this adapter only exposes {@code append} and {@code find}.
 *
 * @see AuditTrailStore
 */
@Component
public class AuditTrailStoreAdapter implements AuditTrailStore {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailStoreAdapter.class);

    private final SpringDataAuditTrailRepository repository;

    /**
     * Constructs the adapter.
     *
     * @param repository the Spring Data JPA repository
     */
    public AuditTrailStoreAdapter(SpringDataAuditTrailRepository repository) {
        this.repository = repository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void append(AuditEntry entry) {
        var entity = toEntity(entry);
        repository.save(entity);
        log.debug("Audit entry appended: paymentId={}, {} → {}, event={}",
                entry.paymentId(), entry.previousStatus(), entry.newStatus(), entry.eventType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AuditEntry> findByPaymentIdAndTenantId(UUID paymentId, String tenantId) {
        List<AuditEntry> entries = repository.findByPaymentIdAndTenantIdOrderByCreatedAtAsc(paymentId, tenantId)
                .stream()
                .map(this::toDomain)
                .toList();
        log.debug("Audit trail queried: paymentId={}, tenantId={}, entries={}", paymentId, tenantId, entries.size());
        return entries;
    }

    /**
     * Converts a domain {@link AuditEntry} to a JPA entity.
     *
     * @param entry the domain audit entry
     * @return the corresponding JPA entity
     */
    private AuditTrailEntity toEntity(AuditEntry entry) {
        return new AuditTrailEntity(
                entry.id(),
                entry.paymentId(),
                entry.tenantId(),
                entry.previousStatus(),
                entry.newStatus(),
                entry.eventType(),
                entry.metadata(),
                entry.performedBy(),
                entry.createdAt()
        );
    }

    /**
     * Reconstitutes a domain {@link AuditEntry} from a JPA entity.
     *
     * @param entity the JPA entity
     * @return the domain audit entry
     */
    private AuditEntry toDomain(AuditTrailEntity entity) {
        return new AuditEntry(
                entity.getId(),
                entity.getPaymentId(),
                entity.getTenantId(),
                entity.getPreviousStatus(),
                entity.getNewStatus(),
                entity.getEventType(),
                entity.getMetadata(),
                entity.getPerformedBy(),
                entity.getCreatedAt()
        );
    }
}

