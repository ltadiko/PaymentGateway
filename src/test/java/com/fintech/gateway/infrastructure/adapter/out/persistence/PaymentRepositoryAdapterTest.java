package com.fintech.gateway.infrastructure.adapter.out.persistence;

import com.fintech.gateway.domain.model.Money;
import com.fintech.gateway.domain.model.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PaymentRepositoryAdapter}.
 *
 * <p>Uses {@code @SpringBootTest} with H2 to test the full persistence stack
 * including AES encryption of sensitive fields.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Round-trip save → find preserves all domain fields</li>
 *   <li>Tenant isolation: wrong tenant cannot access another tenant's payment</li>
 *   <li>Account numbers are encrypted at rest in the database</li>
 * </ul>
 */
@SpringBootTest
@Transactional
@EmbeddedKafka(partitions = 1)
class PaymentRepositoryAdapterTest {

    @Autowired
    private PaymentRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Payment createTestPayment(String tenantId) {
        return Payment.initiate(
                tenantId,
                new Money(new BigDecimal("99.99"), Currency.getInstance("EUR")),
                "NL91ABNA0417164300",
                "DE89370400440532013000",
                "BANK_TRANSFER"
        );
    }

    @Test
    @DisplayName("Save and find by ID and tenant — round-trip preserves all fields")
    void shouldSaveAndFindPayment() {
        Payment original = createTestPayment("tenant-001");

        Payment saved = adapter.save(original);

        Optional<Payment> found = adapter.findByIdAndTenantId(saved.getId(), "tenant-001");

        assertThat(found).isPresent();
        Payment loaded = found.get();
        assertThat(loaded.getId()).isEqualTo(original.getId());
        assertThat(loaded.getTenantId()).isEqualTo("tenant-001");
        assertThat(loaded.getAmount().amount()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(loaded.getAmount().currency()).isEqualTo(Currency.getInstance("EUR"));
        assertThat(loaded.getCreditorAccount()).isEqualTo("NL91ABNA0417164300");
        assertThat(loaded.getDebtorAccount()).isEqualTo("DE89370400440532013000");
        assertThat(loaded.getPaymentMethod()).isEqualTo("BANK_TRANSFER");
        assertThat(loaded.getStatus().toDbValue()).isEqualTo("SUBMITTED");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Find with wrong tenant ID returns empty — tenant isolation")
    void shouldReturnEmptyForWrongTenant() {
        Payment payment = createTestPayment("tenant-001");
        adapter.save(payment);

        Optional<Payment> result = adapter.findByIdAndTenantId(payment.getId(), "tenant-OTHER");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Find non-existent payment returns empty")
    void shouldReturnEmptyForNonExistent() {
        Optional<Payment> result = adapter.findByIdAndTenantId(
                java.util.UUID.randomUUID(), "tenant-001");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Account numbers are encrypted in the database — raw column is not plaintext")
    void shouldEncryptAccountNumbersAtRest() {
        Payment payment = createTestPayment("tenant-001");
        adapter.save(payment);
        entityManager.flush(); // Ensure data is written to H2 before JDBC query
        String rawCreditor = jdbcTemplate.queryForObject(
                "SELECT creditor_account FROM payments WHERE id = ?",
                String.class, payment.getId());

        String rawDebtor = jdbcTemplate.queryForObject(
                "SELECT debtor_account FROM payments WHERE id = ?",
                String.class, payment.getId());

        // Raw values should NOT be the plaintext account numbers
        assertThat(rawCreditor).isNotEqualTo("NL91ABNA0417164300");
        assertThat(rawDebtor).isNotEqualTo("DE89370400440532013000");

        // Raw values should be Base64-encoded ciphertext
        assertThat(rawCreditor).matches("[A-Za-z0-9+/=]+");
        assertThat(rawDebtor).matches("[A-Za-z0-9+/=]+");
    }

    @Test
    @DisplayName("Multiple payments with different tenants are isolated")
    void shouldIsolatePaymentsBetweenTenants() {
        Payment paymentA = createTestPayment("tenant-A");
        Payment paymentB = createTestPayment("tenant-B");
        adapter.save(paymentA);
        adapter.save(paymentB);

        // Tenant A can see their own payment
        assertThat(adapter.findByIdAndTenantId(paymentA.getId(), "tenant-A")).isPresent();
        // Tenant A cannot see Tenant B's payment
        assertThat(adapter.findByIdAndTenantId(paymentB.getId(), "tenant-A")).isEmpty();
        // Tenant B can see their own payment
        assertThat(adapter.findByIdAndTenantId(paymentB.getId(), "tenant-B")).isPresent();
        // Tenant B cannot see Tenant A's payment
        assertThat(adapter.findByIdAndTenantId(paymentA.getId(), "tenant-B")).isEmpty();
    }
}
