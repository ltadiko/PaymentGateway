package com.fintech.gateway.application.service;


import com.fintech.gateway.application.dto.PaymentResponse;
import com.fintech.gateway.application.dto.SubmitPaymentCommand;
import com.fintech.gateway.domain.event.PaymentEvent;
import com.fintech.gateway.domain.model.AuditEntry;
import com.fintech.gateway.domain.model.Money;
import com.fintech.gateway.domain.model.Payment;
import com.fintech.gateway.domain.port.in.SubmitPaymentUseCase;
import com.fintech.gateway.domain.port.out.AuditTrailStore;
import com.fintech.gateway.domain.port.out.IdempotencyStore;
import com.fintech.gateway.domain.port.out.PaymentEventPublisher;
import com.fintech.gateway.domain.port.out.PaymentRepository;
import com.fintech.gateway.infrastructure.crypto.DataMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service orchestrating payment ingestion.
 *
 * <p>Implements the {@link SubmitPaymentUseCase} port with the following flow:
 * <ol>
 *   <li>Check idempotency store for a previous response</li>
 *   <li>Create domain {@link Payment} in SUBMITTED state</li>
 *   <li>Persist payment, audit entry, and idempotency key in a single transaction</li>
 *   <li>Publish {@code PaymentSubmitted} event after commit</li>
 * </ol>
 *
 * <p><strong>Idempotency race condition:</strong> If two concurrent requests arrive
 * with the same key, one succeeds and the other catches the
 * {@link DataIntegrityViolationException} from the unique constraint and
 * re-queries the stored response.
 *
 * @see SubmitPaymentUseCase
 */
@Service
public class PaymentIngestionService implements SubmitPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentIngestionService.class);

    private final PaymentRepository paymentRepository;
    private final IdempotencyStore idempotencyStore;
    private final AuditTrailStore auditTrailStore;
    private final PaymentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the ingestion service with all required outbound ports.
     *
     * @param paymentRepository repository for persisting payments
     * @param idempotencyStore  store for idempotency key management
     * @param auditTrailStore   store for audit trail entries
     * @param eventPublisher    publisher for domain events
     * @param objectMapper      Jackson mapper for serialising idempotency responses
     */
    public PaymentIngestionService(PaymentRepository paymentRepository,
                                   IdempotencyStore idempotencyStore,
                                   AuditTrailStore auditTrailStore,
                                   PaymentEventPublisher eventPublisher,
                                   ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.idempotencyStore = idempotencyStore;
        this.auditTrailStore = auditTrailStore;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>If the idempotency key has been used before (by the same tenant), the original
     * response is returned and no new payment is created.
     *
     * <p>Events are published <strong>after</strong> the transaction commits to avoid
     * the "published but not persisted" problem.
     */
    @Override
    @Transactional
    public PaymentResponse submit(SubmitPaymentCommand command) {
        // 1. Check for existing idempotency key
        Optional<String> existingResponse = idempotencyStore.findResponse(
                command.tenantId(), command.idempotencyKey());

        if (existingResponse.isPresent()) {
            log.info("Idempotent duplicate detected: tenantId={}, key={}",
                    command.tenantId(), command.idempotencyKey());
            return deserializeResponse(existingResponse.get()).asDuplicate();
        }

        // 2. Create domain payment in SUBMITTED state
        Money money = new Money(command.amount(), Currency.getInstance(command.currency()));
        Payment payment = Payment.initiate(
                command.tenantId(), money,
                command.creditorAccount(), command.debtorAccount(),
                command.paymentMethod()
        );

        // 3. Persist payment + audit + idempotency key in single transaction
        PaymentResponse response = persistNewPayment(command, payment);

        // 4. Publish event AFTER transaction commits
        publishSubmittedEvent(payment);

        return response;
    }

    /**
     * Persists the payment, audit entry, and idempotency key.
     *
     * <p>If a race condition causes a duplicate key violation, catches the
     * exception and returns the previously stored response.
     *
     * @param command the submission command
     * @param payment the domain payment object
     * @return the payment response
     */
    private PaymentResponse persistNewPayment(SubmitPaymentCommand command, Payment payment) {
        PaymentResponse response = toResponse(payment);

        try {
            paymentRepository.save(payment);

            auditTrailStore.append(AuditEntry.of(
                    payment.getId(), payment.getTenantId(),
                    null, "SUBMITTED",
                    "PAYMENT_CREATED", null,
                    "payment-ingestion-service"
            ));

            idempotencyStore.store(
                    command.tenantId(), command.idempotencyKey(),
                    payment.getId(), 202,
                    serializeResponse(response)
            );

            log.info("Payment created: paymentId={}, tenantId={}",
                    payment.getId(), payment.getTenantId());

        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread inserted the same idempotency key
            log.warn("Idempotency race condition: tenantId={}, key={}",
                    command.tenantId(), command.idempotencyKey());

            Optional<String> stored = idempotencyStore.findResponse(
                    command.tenantId(), command.idempotencyKey());

            if (stored.isPresent()) {
                return deserializeResponse(stored.get()).asDuplicate();
            }
            // Should never happen — the key was just inserted by another thread
            throw e;
        }

        return response;
    }

    /**
     * Publishes a {@code PaymentSubmitted} event to the message broker.
     *
     * <p>Failures are logged but do not cause the request to fail — the payment
     * is already persisted and can be picked up by a retry mechanism.
     *
     * @param payment the submitted payment
     */
    private void publishSubmittedEvent(Payment payment) {
        try {
            var event = new PaymentEvent.PaymentSubmitted(
                    UUID.randomUUID(),
                    payment.getId(),
                    payment.getTenantId(),
                    Instant.now()
            );
            eventPublisher.publish(event);
            log.debug("Published PaymentSubmitted event: paymentId={}", payment.getId());
        } catch (Exception e) {
            // Event publishing failure should not fail the request.
            // Payment is persisted; a scheduled job or retry can re-publish.
            log.error("Failed to publish PaymentSubmitted event: paymentId={}, error={}",
                    payment.getId(), e.getMessage(), e);
        }
    }

    /**
     * Maps a domain {@link Payment} to a {@link PaymentResponse} with masked account numbers.
     *
     * @param payment the domain payment
     * @return the response DTO with sensitive fields masked
     */
    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getStatus().toDbValue(),
                payment.getStatusDetail(),
                DataMaskingUtil.maskAccountNumber(payment.getCreditorAccount()),
                DataMaskingUtil.maskAccountNumber(payment.getDebtorAccount()),
                payment.getAmount().amount(),
                payment.getAmount().currency().getCurrencyCode(),
                payment.getPaymentMethod(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    /**
     * Serialises a {@link PaymentResponse} to JSON for idempotency storage.
     *
     * @param response the response to serialise
     * @return the JSON string
     */
    /**
     * Serialises a {@link PaymentResponse} to JSON for idempotency storage.
     *
     * @param response the response to serialise
     * @return the JSON string
     */
    private String serializeResponse(PaymentResponse response) {
        return objectMapper.writeValueAsString(response);
    }

    /**
     * Deserialises a JSON string to a {@link PaymentResponse} from idempotency storage.
     *
     * @param json the JSON string
     * @return the deserialised response
     */
    private PaymentResponse deserializeResponse(String json) {
        return objectMapper.readValue(json, PaymentResponse.class);
    }
}

