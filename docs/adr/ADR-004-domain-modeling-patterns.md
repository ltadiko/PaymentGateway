# ADR-004: Domain Modeling & Design Patterns

**Status:** Accepted  
**Date:** 2026-03-10  
**Deciders:** Senior Development Team

---

## Context

The payment lifecycle is a **finite state machine** with well-defined transitions. The domain logic must be:

- Independent of frameworks (Spring, JPA, Kafka).
- Easily testable with plain unit tests.
- Expressive — leveraging modern Java 21+ features.
- Protected against illegal state transitions.

---

## Decision

### 1. Hexagonal Architecture (Ports & Adapters)

The project follows a **three-layer hexagonal architecture**:

```
┌─────────────────────────────────────────────────────────────┐
│                    INFRASTRUCTURE                           │
│  ┌─────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐  │
│  │  REST   │  │  Kafka   │  │   JPA    │  │  Security  │  │
│  │Adapters │  │ Adapters │  │ Adapters │  │  Filters   │  │
│  └────┬────┘  └────┬─────┘  └────┬─────┘  └─────┬──────┘  │
│       │            │             │               │          │
│  ┌────┴────────────┴─────────────┴───────────────┴──────┐  │
│  │              APPLICATION LAYER                        │  │
│  │  ┌───────────────────┐  ┌──────────────────────────┐ │  │
│  │  │  Use Case Services │  │  Application DTOs       │ │  │
│  │  │  (Orchestration)   │  │  (Commands, Responses)  │ │  │
│  │  └─────────┬─────────┘  └──────────────────────────┘ │  │
│  │            │                                          │  │
│  │  ┌─────────┴──────────────────────────────────────┐  │  │
│  │  │              DOMAIN LAYER                       │  │  │
│  │  │  ┌─────────┐  ┌──────────┐  ┌──────────────┐  │  │  │
│  │  │  │  Models  │  │  Events  │  │    Ports     │  │  │  │
│  │  │  │ (Sealed) │  │(Records) │  │ (Interfaces) │  │  │  │
│  │  │  └─────────┘  └──────────┘  └──────────────┘  │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**Dependency Rule:** Dependencies point inward. The domain layer has **zero** framework dependencies — no Spring annotations, no JPA annotations. Only pure Java.

### 2. Payment Status — Sealed Interface + Records (Finite State Machine)

```java
public sealed interface PaymentStatus
    permits PaymentStatus.Submitted,
            PaymentStatus.FraudCheckInProgress,
            PaymentStatus.FraudApproved,
            PaymentStatus.FraudRejected,
            PaymentStatus.ProcessingByBank,
            PaymentStatus.Completed,
            PaymentStatus.Failed {

    record Submitted()            implements PaymentStatus {}
    record FraudCheckInProgress() implements PaymentStatus {}
    record FraudApproved()        implements PaymentStatus {}
    record FraudRejected(String reason)   implements PaymentStatus {}
    record ProcessingByBank()     implements PaymentStatus {}
    record Completed(String bankRef)      implements PaymentStatus {}
    record Failed(String reason)          implements PaymentStatus {}
}
```

**Why sealed?**
- The compiler enforces **exhaustive pattern matching** in `switch` expressions — if a new status is added, every switch must be updated or the code won't compile.
- Prevents invalid "in-between" states that could exist with an open enum.

### 3. State Transitions — Pattern Matching

```java
public PaymentStatus transition(PaymentStatus current, PaymentEvent event) {
    return switch (current) {
        case Submitted s -> switch (event) {
            case StartFraudCheck e  -> new FraudCheckInProgress();
            default -> throw new IllegalStateTransitionException(current, event);
        };
        case FraudCheckInProgress f -> switch (event) {
            case FraudApproved e  -> new FraudApproved();
            case FraudRejected e  -> new FraudRejected(e.reason());
            default -> throw new IllegalStateTransitionException(current, event);
        };
        case FraudApproved a -> switch (event) {
            case SendToBank e -> new ProcessingByBank();
            default -> throw new IllegalStateTransitionException(current, event);
        };
        case ProcessingByBank p -> switch (event) {
            case BankApproved e -> new Completed(e.bankReference());
            case BankRejected e -> new Failed(e.reason());
            default -> throw new IllegalStateTransitionException(current, event);
        };
        case FraudRejected r  -> throw new IllegalStateTransitionException(current, event);
        case Completed c      -> throw new IllegalStateTransitionException(current, event);
        case Failed f         -> throw new IllegalStateTransitionException(current, event);
    };
}
```

This is **pure domain logic** — no framework dependencies, trivially unit-testable.

### 4. Value Objects — Records

```java
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}

public record PaymentMethod(String type, String maskedNumber, String encryptedNumber) {}
```

Records provide immutability, `equals`/`hashCode`, and `toString` — ideal for value objects.

### 5. Domain Events — Sealed Interface + Records

```java
public sealed interface PaymentEvent {
    record PaymentSubmitted(UUID paymentId, String tenantId, Instant timestamp)
        implements PaymentEvent {}
    record FraudAssessmentCompleted(UUID paymentId, String tenantId, boolean approved,
        int fraudScore, String reason, Instant timestamp)
        implements PaymentEvent {}
    record BankProcessingCompleted(UUID paymentId, String tenantId, boolean success,
        String bankReference, String reason, Instant timestamp)
        implements PaymentEvent {}
}
```

### 6. Design Patterns Summary

| Pattern | Where Applied | Purpose |
|---|---|---|
| **Hexagonal Architecture** | Overall structure | Framework independence, testability |
| **State Machine** | `PaymentStatus` sealed hierarchy + transition logic | Enforce valid state transitions, exhaustive handling |
| **Strategy** | `FraudAssessmentPort` interface | Pluggable fraud check implementations |
| **Repository** | `PaymentRepository` (domain port) | Abstract persistence mechanism |
| **Factory** | `Payment.initiate(...)` static factory method | Encapsulate creation logic with validation |
| **Value Object** | `Money`, `PaymentMethod` records | Immutability, self-validating types |
| **Domain Event** | `PaymentEvent` sealed hierarchy | Decouple state changes from side effects |
| **Adapter** | JPA entities ↔ domain models | Translate between domain and infrastructure |
| **Converter** | `AesEncryptionConverter` | Transparent encryption at the JPA layer |
| **Template Method** | Kafka consumers (common error handling, specific processing) | Reduce boilerplate in event consumers |

### 7. Package Structure

```
com.fintech.gateway/
├── domain/           ← Pure Java, ZERO framework dependencies
│   ├── model/        ← Payment, PaymentStatus (sealed), Money, PaymentMethod
│   ├── event/        ← PaymentEvent (sealed), records
│   ├── exception/    ← Domain exceptions
│   └── port/         ← Interfaces (inbound use cases, outbound repos/services)
├── application/      ← Use case orchestration, Spring @Service
│   ├── service/      ← PaymentIngestionService, FraudAssessmentService, etc.
│   └── dto/          ← Command/response records
├── infrastructure/   ← Framework adapters
│   ├── adapter/      ← REST controllers, Kafka producers/consumers, JPA repos
│   ├── security/     ← JWT filter, SecurityConfig
│   ├── crypto/       ← AES encryption converter
│   ├── logging/      ← Log masking
│   └── config/       ← Kafka, application configs
└── PaymentGatewayApplication.java
```

---

## Alternatives Considered

| Alternative | Why Rejected |
|---|---|
| **Enum for PaymentStatus** | Cannot carry per-state data (e.g., `reason` for rejection). Pattern matching with enums is less expressive. Sealed interfaces with records combine type safety with data. |
| **Spring State Machine library** | Over-engineered for this use case. A simple sealed hierarchy with a `transition()` method is more readable, debuggable, and testable. |
| **Anemic domain model** | Services doing all logic with data-only entities violates DDD principles. Rich domain objects with behavior are more maintainable. |
| **Single-module/flat package** | Doesn't enforce architectural boundaries. Hexagonal layering makes dependencies explicit and violations visible. |
| **Lombok for DTOs** | Records are a first-class language feature in Java 21 — Lombok `@Value` is redundant for immutable data carriers. Lombok is still used for JPA entities where Records don't work well. |

---

## Consequences

### Positive
- Domain logic is **100% framework-free** — can be tested with plain JUnit, no Spring context needed.
- Sealed types make illegal states **unrepresentable** — the compiler enforces correctness.
- Pattern matching makes state transition logic **readable and exhaustive**.
- Hexagonal architecture makes it trivial to swap infrastructure (e.g., replace Kafka with RabbitMQ, H2 with PostgreSQL).

### Negative
- Slight mapping overhead between domain models and JPA entities (handled by mapper classes).
- Sealed types require all implementations in the same compilation unit (acceptable for a finite set of statuses).
- Team members must be familiar with modern Java features (records, sealed types, pattern matching).

