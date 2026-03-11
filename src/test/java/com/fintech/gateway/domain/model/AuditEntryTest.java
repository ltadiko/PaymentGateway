package com.fintech.gateway.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEntryTest {

    @Test
    @DisplayName("of() factory creates valid audit entry")
    void factoryCreatesValidEntry() {
        UUID paymentId = UUID.randomUUID();

        AuditEntry entry = AuditEntry.of(paymentId, "tenant-1", null, "SUBMITTED",
                "PAYMENT_SUBMITTED", null, "system");

        assertThat(entry.id()).isNotNull();
        assertThat(entry.paymentId()).isEqualTo(paymentId);
        assertThat(entry.tenantId()).isEqualTo("tenant-1");
        assertThat(entry.previousStatus()).isNull();
        assertThat(entry.newStatus()).isEqualTo("SUBMITTED");
        assertThat(entry.eventType()).isEqualTo("PAYMENT_SUBMITTED");
        assertThat(entry.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("of() factory creates entry with all fields")
    void factoryCreatesEntryWithAllFields() {
        UUID paymentId = UUID.randomUUID();

        AuditEntry entry = AuditEntry.of(paymentId, "tenant-1", "SUBMITTED",
                "FRAUD_CHECK_IN_PROGRESS", "FRAUD_CHECK_STARTED",
                "{\"consumer\":\"fraud-group\"}", "FraudAssessmentConsumer");

        assertThat(entry.previousStatus()).isEqualTo("SUBMITTED");
        assertThat(entry.metadata()).isEqualTo("{\"consumer\":\"fraud-group\"}");
        assertThat(entry.performedBy()).isEqualTo("FraudAssessmentConsumer");
    }

    @Test
    @DisplayName("Rejects null paymentId")
    void rejectsNullPaymentId() {
        assertThatThrownBy(() -> AuditEntry.of(null, "tenant-1", null, "SUBMITTED",
                "PAYMENT_SUBMITTED", null, "system"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects blank tenantId")
    void rejectsBlankTenantId() {
        assertThatThrownBy(() -> AuditEntry.of(UUID.randomUUID(), "", null, "SUBMITTED",
                "PAYMENT_SUBMITTED", null, "system"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects null tenantId")
    void rejectsNullTenantId() {
        assertThatThrownBy(() -> AuditEntry.of(UUID.randomUUID(), null, null, "SUBMITTED",
                "PAYMENT_SUBMITTED", null, "system"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects null newStatus")
    void rejectsNullNewStatus() {
        assertThatThrownBy(() -> AuditEntry.of(UUID.randomUUID(), "tenant-1", null, null,
                "PAYMENT_SUBMITTED", null, "system"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects null eventType")
    void rejectsNullEventType() {
        assertThatThrownBy(() -> AuditEntry.of(UUID.randomUUID(), "tenant-1", null, "SUBMITTED",
                null, null, "system"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Allows null previousStatus and performedBy")
    void allowsNullOptionalFields() {
        AuditEntry entry = AuditEntry.of(UUID.randomUUID(), "tenant-1", null, "SUBMITTED",
                "PAYMENT_SUBMITTED", null, null);
        assertThat(entry.previousStatus()).isNull();
        assertThat(entry.performedBy()).isNull();
        assertThat(entry.metadata()).isNull();
    }
}

