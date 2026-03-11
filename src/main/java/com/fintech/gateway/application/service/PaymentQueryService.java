package com.fintech.gateway.application.service;

import com.fintech.gateway.application.dto.AuditEntryResponse;
import com.fintech.gateway.application.dto.PaymentResponse;
import com.fintech.gateway.domain.exception.PaymentNotFoundException;
import com.fintech.gateway.domain.model.Payment;
import com.fintech.gateway.domain.port.in.GetPaymentUseCase;
import com.fintech.gateway.domain.port.out.AuditTrailStore;
import com.fintech.gateway.domain.port.out.PaymentRepository;
import com.fintech.gateway.infrastructure.crypto.DataMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Application service for querying payment status and audit trail.
 *
 * <p>All queries are tenant-scoped — a client can only access payments
 * belonging to their own tenant. Cross-tenant access results in
 * {@link PaymentNotFoundException}.
 *
 * <p><strong>Security:</strong> Account numbers are masked in all responses.
 *
 * @see GetPaymentUseCase
 */
@Service
@Transactional(readOnly = true)
public class PaymentQueryService implements GetPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentQueryService.class);

    private final PaymentRepository paymentRepository;
    private final AuditTrailStore auditTrailStore;

    /**
     * Constructs the query service with required outbound ports.
     *
     * @param paymentRepository repository for loading payments
     * @param auditTrailStore   store for audit trail entries
     */
    public PaymentQueryService(PaymentRepository paymentRepository,
                               AuditTrailStore auditTrailStore) {
        this.paymentRepository = paymentRepository;
        this.auditTrailStore = auditTrailStore;
    }

    /** {@inheritDoc} */
    @Override
    public PaymentResponse getPayment(UUID paymentId, String tenantId) {
        log.debug("Querying payment: paymentId={}, tenantId={}", paymentId, tenantId);
        Payment payment = paymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> {
                    log.warn("Payment not found: paymentId={}, tenantId={}", paymentId, tenantId);
                    return new PaymentNotFoundException(paymentId);
                });
        log.info("Payment retrieved: paymentId={}, tenantId={}, status={}", paymentId, tenantId, payment.getStatus().toDbValue());
        return toResponse(payment);
    }

    /** {@inheritDoc} */
    @Override
    public List<AuditEntryResponse> getAuditTrail(UUID paymentId, String tenantId) {
        log.debug("Querying audit trail: paymentId={}, tenantId={}", paymentId, tenantId);
        paymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> {
                    log.warn("Audit trail query failed — payment not found: paymentId={}, tenantId={}", paymentId, tenantId);
                    return new PaymentNotFoundException(paymentId);
                });
        List<AuditEntryResponse> trail = auditTrailStore.findByPaymentIdAndTenantId(paymentId, tenantId)
                .stream()
                .map(entry -> new AuditEntryResponse(
                        entry.id(), entry.paymentId(), entry.previousStatus(),
                        entry.newStatus(), entry.eventType(), entry.metadata(),
                        entry.performedBy(), entry.createdAt()))
                .toList();
        log.info("Audit trail retrieved: paymentId={}, tenantId={}, entries={}", paymentId, tenantId, trail.size());
        return trail;
    }

    /**
     * Maps a domain {@link Payment} to a {@link PaymentResponse} with masked account numbers.
     *
     * @param payment the domain payment
     * @return the response DTO with sensitive fields masked
     */
    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(), payment.getStatus().toDbValue(), payment.getStatusDetail(),
                DataMaskingUtil.maskAccountNumber(payment.getCreditorAccount()),
                DataMaskingUtil.maskAccountNumber(payment.getDebtorAccount()),
                payment.getAmount().amount(), payment.getAmount().currency().getCurrencyCode(),
                payment.getPaymentMethod(), payment.getCreatedAt(), payment.getUpdatedAt());
    }
}

