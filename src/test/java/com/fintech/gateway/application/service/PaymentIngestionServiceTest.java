package com.fintech.gateway.application.service;

import com.fintech.gateway.application.dto.PaymentResponse;
import com.fintech.gateway.application.dto.SubmitPaymentCommand;
import com.fintech.gateway.domain.event.PaymentEvent;
import com.fintech.gateway.domain.model.AuditEntry;
import com.fintech.gateway.domain.model.Payment;
import com.fintech.gateway.domain.port.out.AuditTrailStore;
import com.fintech.gateway.domain.port.out.IdempotencyStore;
import com.fintech.gateway.domain.port.out.PaymentEventPublisher;
import com.fintech.gateway.domain.port.out.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentIngestionService}.
 *
 * <p>All outbound ports are mocked. Tests verify the orchestration logic:
 * idempotency, persistence, audit trail, and event publishing.
 */
@ExtendWith(MockitoExtension.class)
class PaymentIngestionServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private IdempotencyStore idempotencyStore;
    @Mock private AuditTrailStore auditTrailStore;
    @Mock private PaymentEventPublisher eventPublisher;

    private PaymentIngestionService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        service = new PaymentIngestionService(
                paymentRepository, idempotencyStore, auditTrailStore,
                eventPublisher, objectMapper);
    }

    private SubmitPaymentCommand createCommand() {
        return new SubmitPaymentCommand(
                "tenant-001", UUID.randomUUID().toString(),
                new BigDecimal("100.00"), "EUR",
                "NL91ABNA0417164300", "DE89370400440532013000",
                "BANK_TRANSFER");
    }

    @Test
    @DisplayName("New payment: saves, audits, stores idempotency key, publishes event")
    void shouldSubmitNewPayment() {
        SubmitPaymentCommand command = createCommand();
        when(idempotencyStore.findResponse(anyString(), anyString())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = service.submit(command);

        assertThat(response.paymentId()).isNotNull();
        assertThat(response.status()).isEqualTo("SUBMITTED");
        assertThat(response.maskedCreditorAccount()).isEqualTo("****4300");
        assertThat(response.maskedDebtorAccount()).isEqualTo("****3000");
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.currency()).isEqualTo("EUR");
        assertThat(response.isDuplicate()).isFalse();

        verify(paymentRepository).save(any(Payment.class));
        verify(auditTrailStore).append(any(AuditEntry.class));
        verify(idempotencyStore).store(eq("tenant-001"), eq(command.idempotencyKey()),
                any(UUID.class), eq(202), anyString());
        verify(eventPublisher).publish(any(PaymentEvent.PaymentSubmitted.class));
    }

    @Test
    @DisplayName("Audit entry records SUBMITTED status with correct event type")
    void shouldCreateCorrectAuditEntry() {
        SubmitPaymentCommand command = createCommand();
        when(idempotencyStore.findResponse(anyString(), anyString())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.submit(command);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditTrailStore).append(captor.capture());
        AuditEntry entry = captor.getValue();

        assertThat(entry.previousStatus()).isNull();
        assertThat(entry.newStatus()).isEqualTo("SUBMITTED");
        assertThat(entry.eventType()).isEqualTo("PAYMENT_CREATED");
        assertThat(entry.performedBy()).isEqualTo("payment-ingestion-service");
    }

    @Test
    @DisplayName("Duplicate idempotency key: returns original response, no new payment")
    void shouldReturnExistingResponseForDuplicateKey() {
        SubmitPaymentCommand command = createCommand();

        PaymentResponse original = new PaymentResponse(
                UUID.randomUUID(), "SUBMITTED", null,
                "****4300", "****3000",
                new BigDecimal("100.00"), "EUR", "BANK_TRANSFER",
                Instant.now(), Instant.now());

        String serialized = objectMapper.writeValueAsString(original);
        when(idempotencyStore.findResponse("tenant-001", command.idempotencyKey()))
                .thenReturn(Optional.of(serialized));

        PaymentResponse response = service.submit(command);

        assertThat(response.paymentId()).isEqualTo(original.paymentId());
        assertThat(response.isDuplicate()).isTrue();

        verify(paymentRepository, never()).save(any());
        verify(auditTrailStore, never()).append(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Race condition: catches DataIntegrityViolation and returns stored response")
    void shouldHandleRaceConditionGracefully() {
        SubmitPaymentCommand command = createCommand();
        when(idempotencyStore.findResponse("tenant-001", command.idempotencyKey()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(objectMapper.writeValueAsString(
                        new PaymentResponse(
                                UUID.randomUUID(), "SUBMITTED", null,
                                "****4300", "****3000",
                                new BigDecimal("100.00"), "EUR", "BANK_TRANSFER",
                                Instant.now(), Instant.now())
                )));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new DataIntegrityViolationException("Unique constraint violation"))
                .when(idempotencyStore).store(anyString(), anyString(), any(), anyInt(), anyString());

        PaymentResponse response = service.submit(command);

        assertThat(response).isNotNull();
        assertThat(response.isDuplicate()).isTrue();
    }

    @Test
    @DisplayName("Outbox write failure rolls back the entire transaction (Transactional Outbox guarantee)")
    void shouldFailWhenOutboxWriteFails() {
        SubmitPaymentCommand command = createCommand();
        when(idempotencyStore.findResponse(anyString(), anyString())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Outbox write failed")).when(eventPublisher).publish(any());

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Outbox write failed");
    }

    @Test
    @DisplayName("Published event contains correct payment ID and tenant ID")
    void shouldPublishEventWithCorrectPayload() {
        SubmitPaymentCommand command = createCommand();
        when(idempotencyStore.findResponse(anyString(), anyString())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = service.submit(command);

        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventPublisher).publish(captor.capture());

        PaymentEvent.PaymentSubmitted event = (PaymentEvent.PaymentSubmitted) captor.getValue();
        assertThat(event.paymentId()).isEqualTo(response.paymentId());
        assertThat(event.tenantId()).isEqualTo("tenant-001");
        assertThat(event.eventId()).isNotNull();
        assertThat(event.timestamp()).isNotNull();
    }
}

