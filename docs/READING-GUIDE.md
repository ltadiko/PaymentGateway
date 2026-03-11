# Reading Guide — Understanding the Full Design

This guide walks you through the architecture documentation in a logical order — from the big picture down to implementation-level detail. Follow this sequence **before** writing any code.

---

## Phase 1: Understand the Big Picture (20 min)

### Step 1 → [Sequence Diagrams](diagrams/sequence-diagrams.md)

**Start here.** Skip the ADRs for now — visuals first.

Read these diagrams in order:

| # | Diagram | What You'll Learn |
|---|---------|-------------------|
| **10** | Payment State Machine | The core mental model — all 7 states and valid transitions. Everything else flows from this. |
| **9** | End-to-End Flow Overview | How all components connect: REST → Kafka → Consumers → External APIs → DB. |
| **1** | Payment Ingestion — Happy Path | The synchronous entry point: client → JWT auth → idempotency check → DB → Kafka → 202 response. |
| **3** | Async Processing Pipeline — Happy Path | The async journey: Kafka → Fraud Consumer → Bank Consumer → COMPLETED. |

After these 4 diagrams, you should be able to sketch the entire system on a whiteboard.

### Step 2 → [ADR-004: Domain Modeling & Design Patterns](adr/ADR-004-domain-modeling-patterns.md)

**Read next.** This is the heart of the system:

- How `PaymentStatus` is modeled as a sealed interface with records.
- How state transitions use pattern matching.
- The hexagonal architecture layers (domain → application → infrastructure).
- The package structure you'll be creating.

**Key takeaway:** The domain layer has ZERO framework dependencies. Understand why.

---

## Phase 2: Understand the Core Business Logic (20 min)

### Step 3 → [ADR-002: Idempotency Strategy](adr/ADR-002-idempotency-strategy.md)

Payments cannot be duplicated. This ADR explains:
- The `Idempotency-Key` header contract.
- The DB-backed deduplication mechanism.
- How race conditions are handled with unique constraints.
- The processing flow diagram for both new and duplicate requests.

**Then revisit** → [Diagram 2: Idempotent Duplicate](diagrams/sequence-diagrams.md#2-payment-ingestion--idempotent-duplicate) to see it in action.

### Step 4 → [ADR-005: Immutable Audit Trail](adr/ADR-005-immutable-audit-trail.md)

Every state change must be recorded. This ADR explains:
- The `audit_trail` table schema.
- What gets audited (every transition with metadata).
- Why audit writes happen in the same DB transaction as state changes.
- How this complements (not replaces) Kafka events.

---

## Phase 3: Understand the Async Pipeline (15 min)

### Step 5 → [ADR-003: Event-Driven Architecture](adr/ADR-003-event-driven-design.md)

The async processing backbone. Read for:
- Why Kafka over RabbitMQ (comparison table).
- Topic design: `payment.submitted` → `payment.fraud-assessed` → `payment.completed`.
- Event payload design (lean — no PII on the bus).
- Failure handling: retries with backoff → Dead Letter Topics.
- Consumer group design.

**Then revisit** → [Diagrams 4, 5, 6](diagrams/sequence-diagrams.md#4-async-processing--fraud-rejection) to see failure scenarios play out.

### Step 6 → [ADR-007: Fraud Assessment — OpenAPI Integration](adr/ADR-007-fraud-openapi-integration.md)

How the fraud check works:
- The OpenAPI 3.1 YAML contract for the external fraud service.
- The `RestClient`-based HTTP client implementation.
- The mock adapter with deterministic scoring rules (amount-based).
- How the Strategy pattern makes it pluggable.

---

## Phase 4: Understand Security (15 min)

### Step 7 → [ADR-001: Security Design](adr/ADR-001-security-design.md)

The security posture for a FinTech API:
- JWT authentication (mocked issuance, production-shaped validation).
- RBAC: `ROLE_PAYMENT_SUBMIT` and `ROLE_PAYMENT_VIEW`.
- Tenant isolation: every query scoped by `tenantId` from JWT.
- Data protection: AES-256 encryption at rest, log masking, API response masking.
- Spring Security filter chain flow.

**Then revisit** → [Diagrams 7, 8](diagrams/sequence-diagrams.md#7-status-inquiry--with-tenant-isolation) to see tenant isolation and cross-tenant rejection.

---

## Phase 5: Understand Testing & Trade-offs (10 min)

### Step 8 → [ADR-006: Testing Strategy](adr/ADR-006-testing-strategy.md)

How we prove correctness:
- Testing pyramid: unit → integration → E2E.
- Unit tests: exhaustive state transition matrix.
- Integration tests: Testcontainers (Kafka) + H2 + WireMock.
- What we do NOT test and why.

### Step 9 → [ADR-008: Design Trade-offs](adr/ADR-008-design-tradeoffs.md)

Read last. This puts everything in context:
- What we prioritized and why.
- What we simplified (conscious shortcuts vs. production needs).
- What we deliberately did not build.
- Time-cost analysis for each architectural decision.
- What we'd do next with more time.

---

## Quick Reference: Reading Path Summary

```
                    ┌─────────────────────────────────────┐
                    │  START HERE                          │
                    │                                     │
 Phase 1            │  Diagrams 10, 9, 1, 3               │
 Big Picture        │  (State Machine → E2E → Ingestion   │
 (20 min)           │   → Async Pipeline)                 │
                    │                                     │
                    │  ADR-004: Domain Modeling            │
                    │  (Sealed types, Hexagonal arch)      │
                    └──────────────┬──────────────────────┘
                                   │
                    ┌──────────────▼──────────────────────┐
 Phase 2            │  ADR-002: Idempotency               │
 Business Logic     │  + Diagram 2                        │
 (20 min)           │                                     │
                    │  ADR-005: Audit Trail               │
                    └──────────────┬──────────────────────┘
                                   │
                    ┌──────────────▼──────────────────────┐
 Phase 3            │  ADR-003: Event-Driven (Kafka)      │
 Async Pipeline     │  + Diagrams 4, 5, 6                 │
 (15 min)           │                                     │
                    │  ADR-007: Fraud OpenAPI              │
                    └──────────────┬──────────────────────┘
                                   │
                    ┌──────────────▼──────────────────────┐
 Phase 4            │  ADR-001: Security                  │
 Security           │  + Diagrams 7, 8                    │
 (15 min)           │                                     │
                    └──────────────┬──────────────────────┘
                                   │
                    ┌──────────────▼──────────────────────┐
 Phase 5            │  ADR-006: Testing Strategy          │
 Testing &          │  ADR-008: Trade-offs                │
 Trade-offs         │                                     │
 (10 min)           │  YOU'RE READY TO CODE               │
                    └─────────────────────────────────────┘
```

---

## After Reading: Implementation Order

Once you understand the design, build in this order:

| Step | What to Build | Depends On Understanding |
|------|---------------|--------------------------|
| 1 | Domain model (`PaymentStatus` sealed, `Payment`, `Money`, events, ports) | ADR-004 |
| 2 | Persistence layer (JPA entities, repos, `AesEncryptionConverter`) | ADR-004, ADR-001 |
| 3 | Security layer (JWT filter, `SecurityConfig`, `TenantContext`) | ADR-001 |
| 4 | Ingestion API (controller, `PaymentIngestionService`, idempotency) | ADR-002, ADR-004 |
| 5 | Kafka config + event publisher | ADR-003 |
| 6 | Fraud consumer + OpenAPI mock client | ADR-003, ADR-007 |
| 7 | Bank consumer + simulator | ADR-003 |
| 8 | Audit trail (entity, repo, writes in each service) | ADR-005 |
| 9 | Log masking (`MaskingPatternLayout`) | ADR-001 |
| 10 | Unit tests (domain state machine, value objects) | ADR-006 |
| 11 | Integration tests (Testcontainers + full pipeline) | ADR-006 |
| 12 | README (run instructions, cURL examples) | All ADRs |

