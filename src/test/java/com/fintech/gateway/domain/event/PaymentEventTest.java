package com.fintech.gateway.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentEventTest {

    @Test
    @DisplayName("PaymentSubmitted creates valid event")
    void paymentSubmittedValid() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();

        var event = new PaymentEvent.PaymentSubmitted(eventId, paymentId, "tenant-1", now);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.paymentId()).isEqualTo(paymentId);
        assertThat(event.tenantId()).isEqualTo("tenant-1");
        assertThat(event.timestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("FraudAssessmentCompleted creates valid event")
    void fraudAssessmentCompletedValid() {
        var event = new PaymentEvent.FraudAssessmentCompleted(
                UUID.randomUUID(), UUID.randomUUID(), "tenant-1",
                false, 85, "High risk", Instant.now()
        );
        assertThat(event.approved()).isFalse();
        assertThat(event.fraudScore()).isEqualTo(85);
        assertThat(event.reason()).isEqualTo("High risk");
    }

    @Test
    @DisplayName("BankProcessingCompleted creates valid event")
    void bankProcessingCompletedValid() {
        var event = new PaymentEvent.BankProcessingCompleted(
                UUID.randomUUID(), UUID.randomUUID(), "tenant-1",
                true, "BNK-REF-123", null, Instant.now()
        );
        assertThat(event.success()).isTrue();
        assertThat(event.bankReference()).isEqualTo("BNK-REF-123");
        assertThat(event.reason()).isNull();
    }

    @Test
    @DisplayName("Rejects null eventId")
    void rejectsNullEventId() {
        assertThatThrownBy(() -> new PaymentEvent.PaymentSubmitted(
                null, UUID.randomUUID(), "tenant-1", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    @DisplayName("Rejects null paymentId")
    void rejectsNullPaymentId() {
        assertThatThrownBy(() -> new PaymentEvent.PaymentSubmitted(
                UUID.randomUUID(), null, "tenant-1", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paymentId");
    }

    @Test
    @DisplayName("Rejects blank tenantId")
    void rejectsBlankTenantId() {
        assertThatThrownBy(() -> new PaymentEvent.PaymentSubmitted(
                UUID.randomUUID(), UUID.randomUUID(), "", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("Rejects null tenantId")
    void rejectsNullTenantId() {
        assertThatThrownBy(() -> new PaymentEvent.PaymentSubmitted(
                UUID.randomUUID(), UUID.randomUUID(), null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("Rejects null timestamp")
    void rejectsNullTimestamp() {
        assertThatThrownBy(() -> new PaymentEvent.PaymentSubmitted(
                UUID.randomUUID(), UUID.randomUUID(), "tenant-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamp");
    }

    @Test
    @DisplayName("Validation applies to all event subtypes")
    void validationAppliesToAllSubtypes() {
        assertThatThrownBy(() -> new PaymentEvent.FraudAssessmentCompleted(
                null, UUID.randomUUID(), "t", true, 10, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PaymentEvent.BankProcessingCompleted(
                UUID.randomUUID(), null, "t", true, "ref", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

