package com.fintech.gateway.application.service;

import com.fintech.gateway.domain.event.PaymentEvent;
import com.fintech.gateway.domain.exception.PaymentNotFoundException;
import com.fintech.gateway.domain.model.AuditEntry;
import com.fintech.gateway.domain.model.Payment;
import com.fintech.gateway.domain.model.TransitionEvent;
import com.fintech.gateway.domain.port.out.AuditTrailStore;
import com.fintech.gateway.domain.port.out.FraudAssessmentPort;
import com.fintech.gateway.domain.port.out.FraudAssessmentPort.FraudAssessmentRequest;
import com.fintech.gateway.domain.port.out.FraudAssessmentPort.FraudAssessmentResult;
import com.fintech.gateway.domain.port.out.PaymentEventPublisher;
import com.fintech.gateway.domain.port.out.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service orchestrating the fraud assessment pipeline step.
 *
 * <p>Called by the Kafka consumer when a {@code PaymentSubmitted} event is received.
 * This service:
 * <ol>
 *   <li>Transitions the payment to {@code FRAUD_CHECK_IN_PROGRESS}</li>
 *   <li>Calls the external fraud assessment service (via {@link FraudAssessmentPort})</li>
 *   <li>Transitions to {@code FRAUD_APPROVED} or {@code FRAUD_REJECTED}</li>
 *   <li>Publishes a {@code FraudAssessmentCompleted} event for the next pipeline step</li>
 * </ol>
 *
 * <p>If the fraud service throws an exception, it propagates to the Kafka consumer
 * which handles retries and dead-letter queue routing.
 */
@Service
public class FraudProcessingService {

    private static final Logger log = LoggerFactory.getLogger(FraudProcessingService.class);

    private final PaymentRepository paymentRepository;
    private final AuditTrailStore auditTrailStore;
    private final FraudAssessmentPort fraudAssessmentPort;
    private final PaymentEventPublisher eventPublisher;

    /**
     * Constructs the fraud processing service.
     *
     * @param paymentRepository   repository for loading/saving payments
     * @param auditTrailStore     store for audit trail entries
     * @param fraudAssessmentPort port for calling the external fraud service
     * @param eventPublisher      publisher for domain events
     */
    public FraudProcessingService(PaymentRepository paymentRepository,
                                  AuditTrailStore auditTrailStore,
                                  FraudAssessmentPort fraudAssessmentPort,
                                  PaymentEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.auditTrailStore = auditTrailStore;
        this.fraudAssessmentPort = fraudAssessmentPort;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Processes the fraud check for a payment.
     *
     * @param paymentId the payment identifier
     * @param tenantId  the tenant identifier
     * @throws PaymentNotFoundException if the payment does not exist
     */
    public void processFraudCheck(UUID paymentId, String tenantId) {
        log.info("Starting fraud check: paymentId={}, tenantId={}", paymentId, tenantId);

        // 1. Load payment and transition to FRAUD_CHECK_IN_PROGRESS
        Payment payment = loadPayment(paymentId, tenantId);
        transitionAndSave(payment, new TransitionEvent.StartFraudCheck(),
                "FRAUD_CHECK_STARTED", null);

        // 2. Call fraud assessment service
        FraudAssessmentRequest request = new FraudAssessmentRequest(
                payment.getId(),
                payment.getAmount().amount(),
                payment.getAmount().currency().getCurrencyCode(),
                payment.getTenantId(),
                payment.getPaymentMethod()
        );

        FraudAssessmentResult result = fraudAssessmentPort.assess(request);

        // 3. Apply result
        if (result.approved()) {
            handleFraudApproved(payment, result);
        } else {
            handleFraudRejected(payment, result);
        }
    }

    private void handleFraudApproved(Payment payment, FraudAssessmentResult result) {
        transitionAndSave(payment, new TransitionEvent.FraudCheckPassed(result.fraudScore()),
                "FRAUD_CHECK_PASSED", "{\"fraudScore\":" + result.fraudScore() + "}");

        log.info("Fraud check passed: paymentId={}, score={}", payment.getId(), result.fraudScore());

        eventPublisher.publish(new PaymentEvent.FraudAssessmentCompleted(
                UUID.randomUUID(), payment.getId(), payment.getTenantId(),
                true, result.fraudScore(), null, Instant.now()));
    }

    private void handleFraudRejected(Payment payment, FraudAssessmentResult result) {
        transitionAndSave(payment, new TransitionEvent.FraudCheckFailed(result.reason(), result.fraudScore()),
                "FRAUD_CHECK_FAILED",
                "{\"fraudScore\":" + result.fraudScore() + ",\"reason\":\"" + result.reason() + "\"}");

        log.warn("Fraud check rejected: paymentId={}, score={}, reason={}",
                payment.getId(), result.fraudScore(), result.reason());

        eventPublisher.publish(new PaymentEvent.FraudAssessmentCompleted(
                UUID.randomUUID(), payment.getId(), payment.getTenantId(),
                false, result.fraudScore(), result.reason(), Instant.now()));
    }

    private Payment loadPayment(UUID paymentId, String tenantId) {
        return paymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> {
                    log.error("Payment not found during fraud processing: paymentId={}, tenantId={}", paymentId, tenantId);
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
                eventType, metadata, "fraud-processing-service"));
    }
}

