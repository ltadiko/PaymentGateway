# ADR-006: Testing Strategy

**Status:** Accepted  
**Date:** 2026-03-10  
**Deciders:** Senior Development Team

---

## Context

The payment gateway handles financial transactions where correctness is critical. The testing strategy must:

- Prove that domain rules and state transitions are correct.
- Verify that the full processing pipeline (REST → Kafka → DB) works end-to-end.
- Run reliably in CI/CD without requiring external infrastructure.
- Provide fast feedback during development.

The assignment specifically requires:
- **Unit tests** for core domain rules and state transitions.
- **Integration tests** using `@SpringBootTest` and Testcontainers that verify the critical path from REST controller through the event broker to the database.

---

## Decision

### Testing Pyramid

```
                    ┌─────────────┐
                    │  E2E Tests  │  ← Few: full pipeline verification
                    ├─────────────┤
                 ┌──┴─────────────┴──┐
                 │ Integration Tests  │  ← Moderate: Spring context + Testcontainers
                 ├───────────────────┤
              ┌──┴───────────────────┴──┐
              │      Unit Tests         │  ← Many: domain logic, state machine, validation
              └─────────────────────────┘
```

### 1. Unit Tests (Domain Layer)

**Scope:** Pure domain logic — no Spring context, no database, no Kafka.

| Test Class | What It Tests |
|---|---|
| `PaymentStatusTransitionTest` | All valid state transitions via pattern matching |
| `PaymentStatusTransitionTest` (negative) | All **invalid** transitions throw `IllegalStateTransitionException` |
| `MoneyTest` | Value object validation (null, zero, negative amounts) |
| `PaymentTest` | `Payment.initiate()` factory, domain invariants |
| `PaymentEventTest` | Event creation, immutability (record contracts) |

**Approach:**
- Plain JUnit 5 + AssertJ — no Spring annotations.
- Parameterized tests (`@ParameterizedTest` + `@MethodSource`) for exhaustive state transition coverage.
- Tests are **fast** (milliseconds) and run without any infrastructure.

**Example coverage matrix for state transitions:**

```
Current State              × Event                  → Expected Result
─────────────────────────────────────────────────────────────────────
SUBMITTED                  × StartFraudCheck        → FRAUD_CHECK_IN_PROGRESS ✅
SUBMITTED                  × BankApproved           → IllegalStateTransition  ✅
FRAUD_CHECK_IN_PROGRESS    × FraudApproved          → FRAUD_APPROVED          ✅
FRAUD_CHECK_IN_PROGRESS    × FraudRejected          → FRAUD_REJECTED          ✅
FRAUD_APPROVED             × SendToBank             → PROCESSING_BY_BANK      ✅
PROCESSING_BY_BANK         × BankApproved           → COMPLETED               ✅
PROCESSING_BY_BANK         × BankRejected           → FAILED                  ✅
COMPLETED                  × (any event)            → IllegalStateTransition  ✅
FAILED                     × (any event)            → IllegalStateTransition  ✅
FRAUD_REJECTED             × (any event)            → IllegalStateTransition  ✅
```

### 2. Integration Tests (Application + Infrastructure Layers)

**Scope:** Spring context with real Kafka and H2 database.

**Infrastructure:**
- **Kafka:** [Testcontainers](https://testcontainers.com/) `KafkaContainer` — a real Kafka broker in Docker.
- **Database:** H2 in-memory — fast, no container needed.
- **Fraud API Mock:** WireMock or `@MockBean` for the OpenAPI client.

| Test Class | What It Tests |
|---|---|
| `PaymentIngestionIntegrationTest` | `POST /payments` → DB record created → Kafka event published |
| `IdempotencyIntegrationTest` | Duplicate request with same key → returns original response, no new payment |
| `FraudAssessmentIntegrationTest` | Kafka consume → fraud API call → status updated → next event published |
| `BankProcessingIntegrationTest` | Kafka consume → bank simulation → final status updated |
| `FullPipelineIntegrationTest` | End-to-end: `POST /payments` → fraud → bank → `GET /payments/{id}` returns COMPLETED |
| `TenantIsolationIntegrationTest` | Tenant A's payment is invisible to Tenant B |
| `SecurityIntegrationTest` | Missing/invalid JWT → 401; wrong role → 403 |

**Testcontainers Setup:**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FullPipelineIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    );

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
```

**Key patterns:**
- `@DynamicPropertySource` wires Testcontainer ports into Spring config.
- `Awaitility` is used to wait for async Kafka processing to complete before asserting.
- `TestRestTemplate` or `MockMvc` drives the REST API.
- Each test uses a unique `tenantId` and `idempotencyKey` to avoid cross-test interference.

### 3. Test Categories & Execution

| Category | Annotation | Runs In CI | Speed |
|---|---|---|---|
| Unit tests | `@Tag("unit")` | Always | < 1 second per test |
| Integration tests | `@Tag("integration")` | Always (Docker required) | 5-30 seconds per test |
| Full pipeline | `@Tag("e2e")` | Always (Docker required) | 10-60 seconds per test |

**Maven Surefire/Failsafe:**
- `mvn test` — runs unit tests (Surefire).
- `mvn verify` — runs unit + integration tests (Failsafe).

### 4. What We Do NOT Test

| Concern | Why Not Tested |
|---|---|
| Kafka internals | Kafka's ordering/delivery guarantees are well-proven; we test our consumer logic, not Kafka itself. |
| H2 SQL dialect edge cases | H2 is a stand-in; production would use PostgreSQL with its own integration tests. |
| JWT cryptographic security | We test token validation logic, not HMAC-SHA256 correctness (that's a library concern). |
| Spring Security framework | We test our security configuration, not Spring Security's core functionality. |

---

## Alternatives Considered

| Alternative | Why Rejected |
|---|---|
| **Embedded Kafka (`@EmbeddedKafka`)** | Less realistic than Testcontainers; has known issues with consumer group rebalancing in tests. Testcontainers provides a real broker. |
| **Mocking Kafka in integration tests** | Defeats the purpose — we need to verify real producer/consumer behavior, serialization, and topic routing. |
| **Separate test database (PostgreSQL Testcontainer)** | Adds complexity. H2 is sufficient for validating JPA logic in this assignment. In production, PostgreSQL Testcontainers would be used. |
| **Contract tests (Pact)** | Valuable for multi-service environments but overkill for this single-service scope. The fraud API is mocked, not a separate deployable. |
| **Mutation testing (PIT)** | Excellent for measuring test quality but adds significant build time. Can be added as an optional CI step later. |

---

## Consequences

### Positive
- Domain logic has **fast, exhaustive unit tests** — every state transition is verified.
- Integration tests prove the **real pipeline works** — not just mocked interfaces.
- Testcontainers ensures tests run against a **real Kafka broker** — catches serialization and configuration issues early.
- Tests are **deterministic** — no shared state, no external dependencies.

### Negative
- Testcontainers requires Docker — CI runners must have Docker available.
- Integration tests are slower (seconds vs milliseconds) — must be balanced with unit test coverage.
- Async assertions with `Awaitility` can be flaky if timeouts are too short — requires tuning.

### Test Coverage Goals
- **Domain layer:** >95% line coverage (state transitions, validations, factories).
- **Application layer:** >80% branch coverage (service orchestration, error handling).
- **Infrastructure layer:** Key paths covered by integration tests (not exhaustive line coverage).

