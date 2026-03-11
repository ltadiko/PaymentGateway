package com.fintech.gateway.application.service;

import com.fintech.gateway.application.dto.AuditEntryResponse;
import com.fintech.gateway.application.dto.PaymentResponse;
import com.fintech.gateway.domain.exception.PaymentNotFoundException;
import com.fintech.gateway.domain.model.AuditEntry;
import com.fintech.gateway.domain.model.Money;
import com.fintech.gateway.domain.model.Payment;
import com.fintech.gateway.domain.port.out.AuditTrailStore;
import com.fintech.gateway.domain.port.out.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentQueryService}.
 */
@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private AuditTrailStore auditTrailStore;

    private PaymentQueryService service;

    @BeforeEach
    void setUp() {
        service = new PaymentQueryService(paymentRepository, auditTrailStore);
    }

    private Payment createTestPayment(String tenantId) {
        return Payment.initiate(tenantId,
                new Money(new BigDecimal("250.00"), Currency.getInstance("USD")),
                "NL91ABNA0417164300", "DE89370400440532013000", "CARD");
    }

    @Test
    @DisplayName("Get payment: returns masked response")
    void shouldReturnMaskedPayment() {
        Payment payment = createTestPayment("tenant-001");
        when(paymentRepository.findByIdAndTenantId(payment.getId(), "tenant-001"))
                .thenReturn(Optional.of(payment));

        PaymentResponse response = service.getPayment(payment.getId(), "tenant-001");

        assertThat(response.paymentId()).isEqualTo(payment.getId());
        assertThat(response.status()).isEqualTo("SUBMITTED");
        assertThat(response.maskedCreditorAccount()).isEqualTo("****4300");
        assertThat(response.maskedDebtorAccount()).isEqualTo("****3000");
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(response.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Get payment: not found throws PaymentNotFoundException")
    void shouldThrowWhenPaymentNotFound() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByIdAndTenantId(paymentId, "tenant-001"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayment(paymentId, "tenant-001"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    @DisplayName("Get payment: wrong tenant throws PaymentNotFoundException (tenant isolation)")
    void shouldThrowWhenWrongTenant() {
        Payment payment = createTestPayment("tenant-001");
        when(paymentRepository.findByIdAndTenantId(payment.getId(), "tenant-OTHER"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayment(payment.getId(), "tenant-OTHER"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    @DisplayName("Get audit trail: returns entries in order")
    void shouldReturnAuditTrail() {
        Payment payment = createTestPayment("tenant-001");
        UUID paymentId = payment.getId();
        when(paymentRepository.findByIdAndTenantId(paymentId, "tenant-001"))
                .thenReturn(Optional.of(payment));

        List<AuditEntry> entries = List.of(
                AuditEntry.of(paymentId, "tenant-001", null, "SUBMITTED",
                        "PAYMENT_CREATED", null, "service"),
                AuditEntry.of(paymentId, "tenant-001", "SUBMITTED", "FRAUD_APPROVED",
                        "FRAUD_CHECK_PASSED", "{\"score\":10}", "service")
        );
        when(auditTrailStore.findByPaymentIdAndTenantId(paymentId, "tenant-001"))
                .thenReturn(entries);

        List<AuditEntryResponse> trail = service.getAuditTrail(paymentId, "tenant-001");

        assertThat(trail).hasSize(2);
        assertThat(trail.get(0).newStatus()).isEqualTo("SUBMITTED");
        assertThat(trail.get(1).newStatus()).isEqualTo("FRAUD_APPROVED");
        assertThat(trail.get(1).metadata()).isEqualTo("{\"score\":10}");
    }

    @Test
    @DisplayName("Get audit trail: payment not found throws exception")
    void shouldThrowWhenAuditTrailPaymentNotFound() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByIdAndTenantId(paymentId, "tenant-001"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAuditTrail(paymentId, "tenant-001"))
                .isInstanceOf(PaymentNotFoundException.class);
    }
}

