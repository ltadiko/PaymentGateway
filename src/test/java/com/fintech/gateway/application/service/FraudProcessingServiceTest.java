package com.fintech.gateway.application.service;

import com.fintech.gateway.domain.event.PaymentEvent;
import com.fintech.gateway.domain.exception.PaymentNotFoundException;
import com.fintech.gateway.domain.model.AuditEntry;
import com.fintech.gateway.domain.model.Money;
import com.fintech.gateway.domain.model.Payment;
import com.fintech.gateway.domain.port.out.AuditTrailStore;
import com.fintech.gateway.domain.port.out.FraudAssessmentPort;
import com.fintech.gateway.domain.port.out.FraudAssessmentPort.FraudAssessmentRequest;
import com.fintech.gateway.domain.port.out.FraudAssessmentPort.FraudAssessmentResult;
import com.fintech.gateway.domain.port.out.PaymentEventPublisher;
import com.fintech.gateway.domain.port.out.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FraudProcessingService}.
 */
@ExtendWith(MockitoExtension.class)
class FraudProcessingServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private AuditTrailStore auditTrailStore;
    @Mock private FraudAssessmentPort fraudAssessmentPort;
    @Mock private PaymentEventPublisher eventPublisher;

    private FraudProcessingService service;

    @BeforeEach
    void setUp() {
        service = new FraudProcessingService(
                paymentRepository, auditTrailStore, fraudAssessmentPort, eventPublisher);
    }

    private Payment createSubmittedPayment() {
        return Payment.initiate("tenant-001",
                new Money(new BigDecimal("500.00"), Currency.getInstance("EUR")),
                "NL91ABNA0417164300", "DE89370400440532013000", "BANK_TRANSFER");
    }

    @Test
    @DisplayName("Fraud approved: transitions to FRAUD_APPROVED and publishes event")
    void shouldHandleFraudApproved() {
        Payment payment = createSubmittedPayment();
        UUID paymentId = payment.getId();

        when(paymentRepository.findByIdAndTenantId(paymentId, "tenant-001"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fraudAssessmentPort.assess(any(FraudAssessmentRequest.class)))
                .thenReturn(new FraudAssessmentResult(paymentId, true, 15, null, Instant.now()));

        service.processFraudCheck(paymentId, "tenant-001");

        assertThat(payment.getStatus().toDbValue()).isEqualTo("FRAUD_APPROVED");
        verify(paymentRepository, times(2)).save(any(Payment.class));
        verify(auditTrailStore, times(2)).append(any(AuditEntry.class));

        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventPublisher).publish(captor.capture());
        PaymentEvent.FraudAssessmentCompleted event =
                (PaymentEvent.FraudAssessmentCompleted) captor.getValue();
        assertThat(event.approved()).isTrue();
        assertThat(event.fraudScore()).isEqualTo(15);
        assertThat(event.paymentId()).isEqualTo(paymentId);
    }

    @Test
    @DisplayName("Fraud rejected: transitions to FRAUD_REJECTED and publishes event")
    void shouldHandleFraudRejected() {
        Payment payment = createSubmittedPayment();
        UUID paymentId = payment.getId();

        when(paymentRepository.findByIdAndTenantId(paymentId, "tenant-001"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fraudAssessmentPort.assess(any(FraudAssessmentRequest.class)))
                .thenReturn(new FraudAssessmentResult(paymentId, false, 85,
                        "Suspicious transaction pattern", Instant.now()));

        service.processFraudCheck(paymentId, "tenant-001");

        assertThat(payment.getStatus().toDbValue()).isEqualTo("FRAUD_REJECTED");

        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventPublisher).publish(captor.capture());
        PaymentEvent.FraudAssessmentCompleted event =
                (PaymentEvent.FraudAssessmentCompleted) captor.getValue();
        assertThat(event.approved()).isFalse();
        assertThat(event.fraudScore()).isEqualTo(85);
        assertThat(event.reason()).isEqualTo("Suspicious transaction pattern");
    }

    @Test
    @DisplayName("Fraud API throws exception: propagates to caller for retry")
    void shouldPropagateExceptionFromFraudService() {
        Payment payment = createSubmittedPayment();
        UUID paymentId = payment.getId();

        when(paymentRepository.findByIdAndTenantId(paymentId, "tenant-001"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fraudAssessmentPort.assess(any(FraudAssessmentRequest.class)))
                .thenThrow(new RuntimeException("Fraud service unavailable"));

        assertThatThrownBy(() -> service.processFraudCheck(paymentId, "tenant-001"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Fraud service unavailable");
    }

    @Test
    @DisplayName("Payment not found: throws PaymentNotFoundException")
    void shouldThrowWhenPaymentNotFound() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByIdAndTenantId(paymentId, "tenant-001"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processFraudCheck(paymentId, "tenant-001"))
                .isInstanceOf(PaymentNotFoundException.class);
    }
}

