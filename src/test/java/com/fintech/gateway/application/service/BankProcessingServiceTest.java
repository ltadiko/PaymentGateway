package com.fintech.gateway.application.service;

import com.fintech.gateway.domain.event.PaymentEvent;
import com.fintech.gateway.domain.exception.PaymentNotFoundException;
import com.fintech.gateway.domain.model.AuditEntry;
import com.fintech.gateway.domain.model.Money;
import com.fintech.gateway.domain.model.Payment;
import com.fintech.gateway.domain.model.TransitionEvent;
import com.fintech.gateway.domain.port.out.AuditTrailStore;
import com.fintech.gateway.domain.port.out.BankGatewayPort;
import com.fintech.gateway.domain.port.out.BankGatewayPort.BankProcessingRequest;
import com.fintech.gateway.domain.port.out.BankGatewayPort.BankProcessingResult;
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
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BankProcessingService}.
 */
@ExtendWith(MockitoExtension.class)
class BankProcessingServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private AuditTrailStore auditTrailStore;
    @Mock private BankGatewayPort bankGatewayPort;
    @Mock private PaymentEventPublisher eventPublisher;

    private BankProcessingService service;

    @BeforeEach
    void setUp() {
        service = new BankProcessingService(
                paymentRepository, auditTrailStore, bankGatewayPort, eventPublisher);
    }

    /**
     * Creates a payment that has passed fraud check (FRAUD_APPROVED),
     * ready for bank processing.
     */
    private Payment createFraudApprovedPayment() {
        Payment payment = Payment.initiate("tenant-001",
                new Money(new BigDecimal("750.00"), Currency.getInstance("GBP")),
                "GB29NWBK60161331926819", "DE89370400440532013000", "BANK_TRANSFER");
        payment.transitionTo(new TransitionEvent.StartFraudCheck());
        payment.transitionTo(new TransitionEvent.FraudCheckPassed(10));
        return payment;
    }

    @Test
    @DisplayName("Bank approved — transitions to COMPLETED and publishes event")
    void shouldHandleBankApproved() {
        Payment payment = createFraudApprovedPayment();
        UUID paymentId = payment.getId();

        when(paymentRepository.findByIdAndTenantId(paymentId, "tenant-001"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bankGatewayPort.process(any(BankProcessingRequest.class)))
                .thenReturn(new BankProcessingResult(paymentId, true, "BANK-REF-12345", null));

        service.processBankPayment(paymentId, "tenant-001");

        assertThat(payment.getStatus().toDbValue()).isEqualTo("COMPLETED");
        verify(paymentRepository, times(2)).save(any(Payment.class));
        verify(auditTrailStore, times(2)).append(any(AuditEntry.class));

        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventPublisher).publish(captor.capture());
        PaymentEvent.BankProcessingCompleted event =
                (PaymentEvent.BankProcessingCompleted) captor.getValue();
        assertThat(event.success()).isTrue();
        assertThat(event.bankReference()).isEqualTo("BANK-REF-12345");
        assertThat(event.paymentId()).isEqualTo(paymentId);
    }

    @Test
    @DisplayName("Bank rejected — transitions to FAILED and publishes event")
    void shouldHandleBankRejected() {
        Payment payment = createFraudApprovedPayment();
        UUID paymentId = payment.getId();

        when(paymentRepository.findByIdAndTenantId(paymentId, "tenant-001"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bankGatewayPort.process(any(BankProcessingRequest.class)))
                .thenReturn(new BankProcessingResult(paymentId, false, null, "Insufficient funds"));

        service.processBankPayment(paymentId, "tenant-001");

        assertThat(payment.getStatus().toDbValue()).isEqualTo("FAILED");

        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventPublisher).publish(captor.capture());
        PaymentEvent.BankProcessingCompleted event =
                (PaymentEvent.BankProcessingCompleted) captor.getValue();
        assertThat(event.success()).isFalse();
        assertThat(event.reason()).isEqualTo("Insufficient funds");
    }

    @Test
    @DisplayName("Bank throws exception — propagates to caller for retry")
    void shouldPropagateExceptionFromBank() {
        Payment payment = createFraudApprovedPayment();
        UUID paymentId = payment.getId();

        when(paymentRepository.findByIdAndTenantId(paymentId, "tenant-001"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bankGatewayPort.process(any(BankProcessingRequest.class)))
                .thenThrow(new RuntimeException("Bank gateway timeout"));

        assertThatThrownBy(() -> service.processBankPayment(paymentId, "tenant-001"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Bank gateway timeout");
    }

    @Test
    @DisplayName("Payment not found — throws PaymentNotFoundException")
    void shouldThrowWhenPaymentNotFound() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByIdAndTenantId(paymentId, "tenant-001"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processBankPayment(paymentId, "tenant-001"))
                .isInstanceOf(PaymentNotFoundException.class);
    }
}

