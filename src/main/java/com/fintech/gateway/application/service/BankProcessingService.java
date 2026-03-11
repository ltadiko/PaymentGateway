package com.fintech.gateway.application.service;

import com.fintech.gateway.domain.event.PaymentEvent;
import com.fintech.gateway.domain.exception.PaymentNotFoundException;
import com.fintech.gateway.domain.model.AuditEntry;
import com.fintech.gateway.domain.model.Payment;
import com.fintech.gateway.domain.model.TransitionEvent;
import com.fintech.gateway.domain.port.out.AuditTrailStore;
import com.fintech.gateway.domain.port.out.BankGatewayPort;
import com.fintech.gateway.domain.port.out.BankGatewayPort.BankProcessingRequest;
import com.fintech.gateway.domain.port.out.BankGatewayPort.BankProcessingResult;
import com.fintech.gateway.domain.port.out.PaymentEventPublisher;
import com.fintech.gateway.domain.port.out.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service orchestrating the bank processing pipeline step.
 *
 * <p>Called by the Kafka consumer when a {@code FraudAssessmentCompleted} (approved)
 * event is received. This service:
 * <ol>
 *   <li>Transitions the payment to {@code PROCESSING_BY_BANK}</li>
 *   <li>Calls the acquiring bank gateway (via {@link BankGatewayPort})</li>
 *   <li>Transitions to {@code COMPLETED} or {@code FAILED}</li>
 *   <li>Publishes a {@code BankProcessingCompleted} event</li>
 * </ol>
 *
 * <p>The bank gateway simulates variable latency (100-3000ms) and random
 * success/failure outcomes. If the bank call throws an exception, it propagates
 * to the Kafka consumer for retry handling.
 */
@Service
public class BankProcessingService {

    private static final Logger log = LoggerFactory.getLogger(BankProcessingService.class);

    private final PaymentRepository paymentRepository;
    private final AuditTrailStore auditTrailStore;
    private final BankGatewayPort bankGatewayPort;
    private final PaymentEventPublisher eventPublisher;

    /**
     * Constructs the bank processing service.
     *
     * @param paymentRepository repository for loading/saving payments
     * @param auditTrailStore   store for audit trail entries
     * @param bankGatewayPort   port for calling the acquiring bank
     * @param eventPublisher    publisher for domain events
     */
    public BankProcessingService(PaymentRepository paymentRepository,
                                 AuditTrailStore auditTrailStore,
                                 BankGatewayPort bankGatewayPort,
                                 PaymentEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.auditTrailStore = auditTrailStore;
        this.bankGatewayPort = bankGatewayPort;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Processes a payment through the acquiring bank.
     *
     * @param paymentId the payment identifier
     * @param tenantId  the tenant identifier
     * @throws PaymentNotFoundException if the payment does not exist
     */
    public void processBankPayment(UUID paymentId, String tenantId) {
        log.info("Starting bank processing: paymentId={}, tenantId={}", paymentId, tenantId);

        // 1. Load payment and transition to PROCESSING_BY_BANK
        Payment payment = loadPayment(paymentId, tenantId);
        transitionAndSave(payment, new TransitionEvent.SendToBank(),
                "SENT_TO_BANK", null);

        // 2. Call acquiring bank
        BankProcessingRequest request = new BankProcessingRequest(
                payment.getId(),
                payment.getAmount().amount(),
                payment.getAmount().currency().getCurrencyCode(),
                payment.getCreditorAccount()
        );

        BankProcessingResult result = bankGatewayPort.process(request);

        // 3. Apply result
        if (result.success()) {
            handleBankApproved(payment, result);
        } else {
            handleBankRejected(payment, result);
        }
    }

    private void handleBankApproved(Payment payment, BankProcessingResult result) {
        transitionAndSave(payment, new TransitionEvent.BankApproved(result.bankReference()),
                "BANK_APPROVED", "{\"bankReference\":\"" + result.bankReference() + "\"}");

        log.info("Bank processing succeeded: paymentId={}, bankRef={}",
                payment.getId(), result.bankReference());

        eventPublisher.publish(new PaymentEvent.BankProcessingCompleted(
                UUID.randomUUID(), payment.getId(), payment.getTenantId(),
                true, result.bankReference(), null, Instant.now()));
    }

    private void handleBankRejected(Payment payment, BankProcessingResult result) {
        transitionAndSave(payment, new TransitionEvent.BankRejected(result.reason()),
                "BANK_REJECTED", "{\"reason\":\"" + result.reason() + "\"}");

        log.warn("Bank processing failed: paymentId={}, reason={}",
                payment.getId(), result.reason());

        eventPublisher.publish(new PaymentEvent.BankProcessingCompleted(
                UUID.randomUUID(), payment.getId(), payment.getTenantId(),
                false, null, result.reason(), Instant.now()));
    }

    private Payment loadPayment(UUID paymentId, String tenantId) {
        return paymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> {
                    log.error("Payment not found during bank processing: paymentId={}, tenantId={}", paymentId, tenantId);
                    return new PaymentNotFoundException(paymentId);
                });
    }

    /**
     * Transitions a payment, saves it, and appends an audit entry.
     */
    @Transactional
    protected void transitionAndSave(Payment payment, TransitionEvent event,
                                     String eventType, String metadata) {
        String previousStatus = payment.getStatus().toDbValue();
        payment.transitionTo(event);
        paymentRepository.save(payment);
        auditTrailStore.append(AuditEntry.of(
                payment.getId(), payment.getTenantId(),
                previousStatus, payment.getStatus().toDbValue(),
                eventType, metadata, "bank-processing-service"));
    }
}

