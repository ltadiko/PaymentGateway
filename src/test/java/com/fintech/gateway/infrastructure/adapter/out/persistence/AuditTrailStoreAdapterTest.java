package com.fintech.gateway.infrastructure.adapter.out.persistence;

import com.fintech.gateway.domain.model.AuditEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AuditTrailStoreAdapter}.
 *
 * <p>Verifies append-only semantics, chronological ordering,
 * and tenant isolation for audit trail entries.
 */
@SpringBootTest
@Transactional
@EmbeddedKafka(partitions = 1)
class AuditTrailStoreAdapterTest {

    @Autowired
    private AuditTrailStoreAdapter adapter;

    @Test
    @DisplayName("Append and retrieve audit entries — ordered by createdAt ASC")
    void shouldAppendAndRetrieveInOrder() {
        UUID paymentId = UUID.randomUUID();
        String tenantId = "tenant-001";

        AuditEntry entry1 = AuditEntry.of(paymentId, tenantId, null, "SUBMITTED",
                "PAYMENT_CREATED", null, "payment-service");
        AuditEntry entry2 = AuditEntry.of(paymentId, tenantId, "SUBMITTED", "FRAUD_CHECK_IN_PROGRESS",
                "FRAUD_CHECK_STARTED", null, "fraud-service");
        AuditEntry entry3 = AuditEntry.of(paymentId, tenantId, "FRAUD_CHECK_IN_PROGRESS", "FRAUD_APPROVED",
                "FRAUD_CHECK_PASSED", "{\"score\":15}", "fraud-service");

        adapter.append(entry1);
        adapter.append(entry2);
        adapter.append(entry3);

        List<AuditEntry> trail = adapter.findByPaymentIdAndTenantId(paymentId, tenantId);

        assertThat(trail).hasSize(3);
        assertThat(trail.get(0).newStatus()).isEqualTo("SUBMITTED");
        assertThat(trail.get(1).newStatus()).isEqualTo("FRAUD_CHECK_IN_PROGRESS");
        assertThat(trail.get(2).newStatus()).isEqualTo("FRAUD_APPROVED");
        assertThat(trail.get(2).metadata()).isEqualTo("{\"score\":15}");
    }

    @Test
    @DisplayName("Find with wrong tenant returns empty list — tenant isolation")
    void shouldReturnEmptyForWrongTenant() {
        UUID paymentId = UUID.randomUUID();

        AuditEntry entry = AuditEntry.of(paymentId, "tenant-001", null, "SUBMITTED",
                "PAYMENT_CREATED", null, "payment-service");
        adapter.append(entry);

        List<AuditEntry> result = adapter.findByPaymentIdAndTenantId(paymentId, "tenant-OTHER");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Find with non-existent payment returns empty list")
    void shouldReturnEmptyForNonExistentPayment() {
        List<AuditEntry> result = adapter.findByPaymentIdAndTenantId(
                UUID.randomUUID(), "tenant-001");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Entries from different payments are not mixed")
    void shouldNotMixEntriesFromDifferentPayments() {
        UUID paymentA = UUID.randomUUID();
        UUID paymentB = UUID.randomUUID();
        String tenantId = "tenant-001";

        adapter.append(AuditEntry.of(paymentA, tenantId, null, "SUBMITTED",
                "PAYMENT_CREATED", null, "service"));
        adapter.append(AuditEntry.of(paymentB, tenantId, null, "SUBMITTED",
                "PAYMENT_CREATED", null, "service"));
        adapter.append(AuditEntry.of(paymentA, tenantId, "SUBMITTED", "FRAUD_APPROVED",
                "FRAUD_PASSED", null, "service"));

        List<AuditEntry> trailA = adapter.findByPaymentIdAndTenantId(paymentA, tenantId);
        List<AuditEntry> trailB = adapter.findByPaymentIdAndTenantId(paymentB, tenantId);

        assertThat(trailA).hasSize(2);
        assertThat(trailB).hasSize(1);
    }
}
