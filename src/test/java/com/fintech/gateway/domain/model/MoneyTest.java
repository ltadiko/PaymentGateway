package com.fintech.gateway.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    @DisplayName("Should create Money with valid amount and currency")
    void shouldCreateWithValidValues() {
        Money money = new Money(BigDecimal.TEN, Currency.getInstance("USD"));
        assertThat(money.amount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(money.currency().getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Should create Money with decimal amount")
    void shouldCreateWithDecimalAmount() {
        Money money = new Money(new BigDecimal("99.99"), Currency.getInstance("EUR"));
        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(money.currency().getCurrencyCode()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Should reject null amount")
    void shouldRejectNullAmount() {
        assertThatThrownBy(() -> new Money(null, Currency.getInstance("USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must not be null");
    }

    @Test
    @DisplayName("Should reject zero amount")
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> new Money(BigDecimal.ZERO, Currency.getInstance("USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    @DisplayName("Should reject negative amount")
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-10.00"), Currency.getInstance("USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    @DisplayName("Should reject null currency")
    void shouldRejectNullCurrency() {
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency must not be null");
    }

    @Test
    @DisplayName("Records guarantee equals/hashCode")
    void shouldHaveValueEquality() {
        Money a = new Money(BigDecimal.TEN, Currency.getInstance("USD"));
        Money b = new Money(BigDecimal.TEN, Currency.getInstance("USD"));
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("Different amounts are not equal")
    void shouldNotBeEqualForDifferentAmounts() {
        Money a = new Money(BigDecimal.TEN, Currency.getInstance("USD"));
        Money b = new Money(BigDecimal.ONE, Currency.getInstance("USD"));
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Different currencies are not equal")
    void shouldNotBeEqualForDifferentCurrencies() {
        Money a = new Money(BigDecimal.TEN, Currency.getInstance("USD"));
        Money b = new Money(BigDecimal.TEN, Currency.getInstance("EUR"));
        assertThat(a).isNotEqualTo(b);
    }
}

