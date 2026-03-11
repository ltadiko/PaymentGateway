package com.fintech.gateway.domain.model;

import com.fintech.gateway.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Money DEFAULT_AMOUNT = new Money(new BigDecimal("100.00"), Currency.getInstance("USD"));

    @Test
    @DisplayName("initiate() creates payment in SUBMITTED state")
    void initiateShouldCreateSubmittedPayment() {
        Payment payment = Payment.initiate("tenant-1", DEFAULT_AMOUNT, "CR-001", "DR-001", "CARD");

        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getTenantId()).isEqualTo("tenant-1");
        assertThat(payment.getAmount()).isEqualTo(DEFAULT_AMOUNT);
        assertThat(payment.getCreditorAccount()).isEqualTo("CR-001");
        assertThat(payment.getDebtorAccount()).isEqualTo("DR-001");
        assertThat(payment.getPaymentMethod()).isEqualTo("CARD");
        assertThat(payment.getStatus()).isInstanceOf(PaymentStatus.Submitted.class);
        assertThat(payment.getStatus().toDbValue()).isEqualTo("SUBMITTED");
        assertThat(payment.getStatusDetail()).isNull();
        assertThat(payment.getCreatedAt()).isNotNull();
        assertThat(payment.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("initiate() generates unique IDs")
    void initiateShouldGenerateUniqueIds() {
        Payment p1 = Payment.initiate("tenant-1", DEFAULT_AMOUNT, "CR-001", "DR-001", "CARD");
        Payment p2 = Payment.initiate("tenant-1", DEFAULT_AMOUNT, "CR-001", "DR-001", "CARD");
        assertThat(p1.getId()).isNotEqualTo(p2.getId());
    }

    @Test
    @DisplayName("transitionTo() delegates to state machine correctly")
    void transitionToShouldDelegateToStateMachine() {
        Payment payment = Payment.initiate("tenant-1", DEFAULT_AMOUNT, "CR-001", "DR-001", "CARD");

        PaymentStatus result = payment.transitionTo(new TransitionEvent.StartFraudCheck());

        assertThat(result).isInstanceOf(PaymentStatus.FraudCheckInProgress.class);
        assertThat(payment.getStatus()).isInstanceOf(PaymentStatus.FraudCheckInProgress.class);
        assertThat(payment.getUpdatedAt()).isAfterOrEqualTo(payment.getCreatedAt());
    }

    @Test
    @DisplayName("transitionTo() updates statusDetail for terminal states")
    void transitionToShouldUpdateStatusDetail() {
        Payment payment = Payment.initiate("tenant-1", DEFAULT_AMOUNT, "CR-001", "DR-001", "CARD");
        payment.transitionTo(new TransitionEvent.StartFraudCheck());
        payment.transitionTo(new TransitionEvent.FraudCheckPassed(15));
        payment.transitionTo(new TransitionEvent.SendToBank());
        payment.transitionTo(new TransitionEvent.BankApproved("BNK-REF-123"));

        assertThat(payment.getStatus()).isInstanceOf(PaymentStatus.Completed.class);
        assertThat(payment.getStatusDetail()).isEqualTo("BNK-REF-123");
    }

    @Test
    @DisplayName("Full happy path: SUBMITTED -> COMPLETED")
    void fullHappyPath() {
        Payment payment = Payment.initiate("tenant-1", DEFAULT_AMOUNT, "CR-001", "DR-001", "CARD");

        payment.transitionTo(new TransitionEvent.StartFraudCheck());
        assertThat(payment.getStatus().toDbValue()).isEqualTo("FRAUD_CHECK_IN_PROGRESS");

        payment.transitionTo(new TransitionEvent.FraudCheckPassed(15));
        assertThat(payment.getStatus().toDbValue()).isEqualTo("FRAUD_APPROVED");

        payment.transitionTo(new TransitionEvent.SendToBank());
        assertThat(payment.getStatus().toDbValue()).isEqualTo("PROCESSING_BY_BANK");

        payment.transitionTo(new TransitionEvent.BankApproved("BNK-999"));
        assertThat(payment.getStatus().toDbValue()).isEqualTo("COMPLETED");
        assertThat(payment.getStatusDetail()).isEqualTo("BNK-999");
    }

    @Test
    @DisplayName("Fraud rejection path: SUBMITTED -> FRAUD_REJECTED")
    void fraudRejectionPath() {
        Payment payment = Payment.initiate("tenant-1", DEFAULT_AMOUNT, "CR-001", "DR-001", "CARD");

        payment.transitionTo(new TransitionEvent.StartFraudCheck());
        payment.transitionTo(new TransitionEvent.FraudCheckFailed("Suspicious activity", 92));

        assertThat(payment.getStatus().toDbValue()).isEqualTo("FRAUD_REJECTED");
        assertThat(payment.getStatusDetail()).isEqualTo("Suspicious activity");
    }

    @Test
    @DisplayName("Bank failure path: SUBMITTED -> FAILED")
    void bankFailurePath() {
        Payment payment = Payment.initiate("tenant-1", DEFAULT_AMOUNT, "CR-001", "DR-001", "CARD");

        payment.transitionTo(new TransitionEvent.StartFraudCheck());
        payment.transitionTo(new TransitionEvent.FraudCheckPassed(10));
        payment.transitionTo(new TransitionEvent.SendToBank());
        payment.transitionTo(new TransitionEvent.BankRejected("Insufficient funds"));

        assertThat(payment.getStatus().toDbValue()).isEqualTo("FAILED");
        assertThat(payment.getStatusDetail()).isEqualTo("Insufficient funds");
    }

    @Test
    @DisplayName("transitionTo() from terminal state throws exception")
    void transitionFromTerminalStateThrows() {
        Payment payment = Payment.initiate("tenant-1", DEFAULT_AMOUNT, "CR-001", "DR-001", "CARD");
        payment.transitionTo(new TransitionEvent.StartFraudCheck());
        payment.transitionTo(new TransitionEvent.FraudCheckPassed(10));
        payment.transitionTo(new TransitionEvent.SendToBank());
        payment.transitionTo(new TransitionEvent.BankApproved("BNK-001"));

        assertThatThrownBy(() -> payment.transitionTo(new TransitionEvent.StartFraudCheck()))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    @DisplayName("reconstitute() rebuilds payment from stored data")
    void reconstituteShouldRebuildPayment() {
        Payment original = Payment.initiate("tenant-1", DEFAULT_AMOUNT, "CR-001", "DR-001", "CARD");

        Payment reconstituted = Payment.reconstitute(
                original.getId(), original.getTenantId(), original.getAmount(),
                original.getCreditorAccount(), original.getDebtorAccount(),
                original.getPaymentMethod(), new PaymentStatus.FraudApproved(),
                null, original.getCreatedAt(), original.getUpdatedAt()
        );

        assertThat(reconstituted.getId()).isEqualTo(original.getId());
        assertThat(reconstituted.getStatus()).isInstanceOf(PaymentStatus.FraudApproved.class);
    }

    @Test
    @DisplayName("equals() and hashCode() based on id only")
    void equalsShouldBeBasedOnId() {
        Payment p1 = Payment.initiate("tenant-1", DEFAULT_AMOUNT, "CR-001", "DR-001", "CARD");
        Payment p2 = Payment.reconstitute(
                p1.getId(), "tenant-2", DEFAULT_AMOUNT, "CR-002", "DR-002", "TRANSFER",
                new PaymentStatus.Submitted(), null, p1.getCreatedAt(), p1.getUpdatedAt()
        );
        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }
}

