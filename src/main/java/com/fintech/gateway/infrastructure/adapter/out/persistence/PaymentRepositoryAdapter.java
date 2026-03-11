package com.fintech.gateway.infrastructure.adapter.out.persistence;

import com.fintech.gateway.domain.model.Payment;
import com.fintech.gateway.domain.port.out.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter implementing the {@link PaymentRepository} domain port using Spring Data JPA.
 *
 * <p>Delegates to {@link SpringDataPaymentRepository} and maps between the
 * domain {@link Payment} aggregate and the {@link com.fintech.gateway.infrastructure.adapter.out.persistence.entity.PaymentJpaEntity}
 * via {@link PaymentEntityMapper}.
 *
 * <p>Sensitive fields (account numbers) are transparently encrypted at the JPA
 * entity level via {@link com.fintech.gateway.infrastructure.crypto.AesAttributeConverter}.
 *
 * @see PaymentRepository
 * @see PaymentEntityMapper
 */
@Component
public class PaymentRepositoryAdapter implements PaymentRepository {

    private static final Logger log = LoggerFactory.getLogger(PaymentRepositoryAdapter.class);

    private final SpringDataPaymentRepository springDataRepository;

    /**
     * Constructs the adapter.
     *
     * @param springDataRepository the Spring Data JPA repository
     */
    public PaymentRepositoryAdapter(SpringDataPaymentRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Maps the domain aggregate to a JPA entity, persists it, and maps back.
     */
    @Override
    public Payment save(Payment payment) {
        var entity = PaymentEntityMapper.toEntity(payment);
        var saved = springDataRepository.save(entity);
        log.debug("Payment persisted: paymentId={}, tenantId={}, status={}",
                payment.getId(), payment.getTenantId(), payment.getStatus().toDbValue());
        return PaymentEntityMapper.toDomain(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Queries by both ID and tenant ID, ensuring tenant isolation at the data layer.
     */
    @Override
    public Optional<Payment> findByIdAndTenantId(UUID id, String tenantId) {
        Optional<Payment> result = springDataRepository.findByIdAndTenantId(id, tenantId)
                .map(PaymentEntityMapper::toDomain);
        log.debug("Payment lookup: paymentId={}, tenantId={}, found={}", id, tenantId, result.isPresent());
        return result;
    }
}

