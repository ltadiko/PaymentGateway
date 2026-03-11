package com.fintech.gateway.infrastructure.adapter.out.persistence;

import com.fintech.gateway.domain.model.Money;
import com.fintech.gateway.domain.model.Payment;
import com.fintech.gateway.domain.model.PaymentStatus;
import com.fintech.gateway.infrastructure.adapter.out.persistence.entity.PaymentJpaEntity;

import java.util.Currency;

/**
 * Mapper between {@link Payment} domain aggregate and {@link PaymentJpaEntity} JPA entity.
 *
 * <p>This mapper is a pure utility class with no dependencies — it bridges the
 * domain model and the persistence layer without either side knowing about the other.
 *
 * <p>Key conversions:
 * <ul>
 *   <li>{@link PaymentStatus} sealed type ↔ String (via {@code toDbValue()} / {@code fromDbValue()})</li>
 *   <li>{@link Money} value object ↔ separate {@code amount} and {@code currency} columns</li>
 * </ul>
 */
public final class PaymentEntityMapper {

    private PaymentEntityMapper() {
        // Utility class — no instantiation
    }

    /**
     * Converts a domain {@link Payment} to a JPA entity for persistence.
     *
     * @param payment the domain payment aggregate
     * @return the corresponding JPA entity
     */
    public static PaymentJpaEntity toEntity(Payment payment) {
        return new PaymentJpaEntity(
                payment.getId(),
                payment.getTenantId(),
                payment.getAmount().amount(),
                payment.getAmount().currency().getCurrencyCode(),
                payment.getCreditorAccount(),
                payment.getDebtorAccount(),
                payment.getPaymentMethod(),
                payment.getStatus().toDbValue(),
                payment.getStatusDetail(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    /**
     * Reconstitutes a domain {@link Payment} from a JPA entity.
     *
     * <p>Uses {@link Payment#reconstitute} to create the domain object
     * without triggering domain events or validation side effects.
     *
     * @param entity the JPA entity loaded from the database
     * @return the reconstituted domain payment aggregate
     */
    public static Payment toDomain(PaymentJpaEntity entity) {
        Money money = new Money(entity.getAmount(), Currency.getInstance(entity.getCurrency()));
        PaymentStatus status = PaymentStatus.fromDbValue(entity.getStatus(), entity.getStatusDetail());

        return Payment.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                money,
                entity.getCreditorAccount(),
                entity.getDebtorAccount(),
                entity.getPaymentMethod(),
                status,
                entity.getStatusDetail(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

