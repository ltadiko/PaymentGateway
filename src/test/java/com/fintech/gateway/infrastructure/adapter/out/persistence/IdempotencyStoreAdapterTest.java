package com.fintech.gateway.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link IdempotencyStoreAdapter}.
 *
 * <p>Verifies idempotency key storage, retrieval, tenant isolation,
 * and the database unique constraint that prevents duplicates.
 */
@SpringBootTest
@Transactional
@EmbeddedKafka(partitions = 1)
class IdempotencyStoreAdapterTest {

    @Autowired
    private IdempotencyStoreAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Store and find response — round-trip works")
    void shouldStoreAndFindResponse() {
        UUID paymentId = UUID.randomUUID();
        String responseBody = "{\"paymentId\":\"" + paymentId + "\",\"status\":\"SUBMITTED\"}";

        adapter.store("tenant-001", "idem-key-1", paymentId, 202, responseBody);

        Optional<String> found = adapter.findResponse("tenant-001", "idem-key-1");

        assertThat(found).isPresent().hasValue(responseBody);
    }

    @Test
    @DisplayName("Find with non-existent key returns empty")
    void shouldReturnEmptyForNonExistentKey() {
        Optional<String> result = adapter.findResponse("tenant-001", "non-existent-key");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Find with wrong tenant returns empty — tenant isolation")
    void shouldReturnEmptyForWrongTenant() {
        UUID paymentId = UUID.randomUUID();
        adapter.store("tenant-001", "idem-key-1", paymentId, 202, "{}");

        Optional<String> result = adapter.findResponse("tenant-OTHER", "idem-key-1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Same key for different tenants is allowed")
    void shouldAllowSameKeyForDifferentTenants() {
        UUID paymentIdA = UUID.randomUUID();
        UUID paymentIdB = UUID.randomUUID();

        adapter.store("tenant-A", "shared-key", paymentIdA, 202, "{\"tenant\":\"A\"}");
        adapter.store("tenant-B", "shared-key", paymentIdB, 202, "{\"tenant\":\"B\"}");

        assertThat(adapter.findResponse("tenant-A", "shared-key"))
                .isPresent().hasValue("{\"tenant\":\"A\"}");
        assertThat(adapter.findResponse("tenant-B", "shared-key"))
                .isPresent().hasValue("{\"tenant\":\"B\"}");
    }

    @Test
    @DisplayName("Duplicate key for same tenant throws exception on flush")
    void shouldRejectDuplicateKeyForSameTenant() {
        UUID paymentId = UUID.randomUUID();
        adapter.store("tenant-001", "idem-key-dup", paymentId, 202, "{}");
        entityManager.flush(); // Force first insert to DB

        assertThatThrownBy(() -> {
            adapter.store("tenant-001", "idem-key-dup", UUID.randomUUID(), 202, "{}");
            entityManager.flush(); // Force second insert to trigger constraint
        }).isInstanceOf(Exception.class); // DataIntegrityViolationException or PersistenceException
    }
}
