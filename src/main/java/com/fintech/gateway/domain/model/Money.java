package com.fintech.gateway.domain.model;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Value object representing a monetary amount with currency.
 * Immutable, self-validating at construction time.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero, got: " + amount);
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency must not be null");
        }
    }
}

