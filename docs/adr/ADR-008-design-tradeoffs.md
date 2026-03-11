# ADR-008: Design Trade-offs & Scope Management

**Status:** Accepted  
**Date:** 2026-03-10  
**Deciders:** Senior Development Team

---

## Context

The assignment has a suggested timebox of ~8-10 hours. The requirement explicitly states: *"focus on core architectural quality and clean code over 100% feature completeness if time is tight."*

This ADR documents the conscious trade-offs made to balance architectural quality with delivery within the timebox.

---

## Decision

### What We Prioritized (Built Well)

| Area | Rationale |
|---|---|
| **Domain model with sealed types & pattern matching** | This is the core of the assignment — demonstrating modern Java 21+ mastery. Investing here shows clean, type-safe domain logic. |
| **Hexagonal architecture** | Clean separation ensures testability and maintainability. The domain layer has zero framework dependencies. |
| **Security posture** | FinTech context demands security be first-class: JWT auth, tenant isolation, field-level encryption, log masking. |
| **Idempotency with DB-backed guarantees** | Payment deduplication is a core business requirement — getting this wrong has direct financial impact. |
| **Event-driven pipeline with Kafka** | The async processing pipeline is the architectural centerpiece. Topics, DLT, retry configuration, and consumer design are implemented thoughtfully. |
| **Immutable audit trail** | Compliance requirement — every state transition is recorded in the same transaction. |
| **Comprehensive unit tests for domain logic** | State transition logic must be provably correct. Parameterized tests cover all valid and invalid transitions. |
| **Integration tests with Testcontainers** | Proves the real pipeline works (REST → Kafka → DB), not just mocked interfaces. |

### What We Simplified (Conscious Shortcuts)

| Area | What We Did | What Production Would Need |
|---|---|---|
| **JWT token issuance** | Mock endpoint `POST /api/auth/token` issues tokens locally with HMAC-SHA256. | Real IdP integration (Keycloak/Auth0) with RSA/EC key pairs and JWKS endpoint. |
| **Database** | H2 in-memory. No migrations, no connection pooling tuning. | PostgreSQL with Flyway migrations, connection pooling (HikariCP tuning), read replicas. |
| **Encryption key management** | AES key stored in `application.properties`. | AWS KMS / HashiCorp Vault with automatic key rotation. |
| **Fraud API** | Hand-written `RestClient` + mock adapter. No codegen. | OpenAPI Generator Maven plugin with generated client, circuit breaker (Resilience4j). |
| **Bank simulation** | Simple `Thread.sleep` + random success/fail. | Real bank integration with circuit breaker, timeout policies, idempotent retry logic. |
| **Monitoring & observability** | Not implemented. | Micrometer metrics, distributed tracing (OpenTelemetry), Grafana dashboards, alerting on DLT messages. |
| **API rate limiting** | Not implemented. | Spring Cloud Gateway or Bucket4j rate limiting per tenant. |
| **Pagination** | Status inquiry returns single payment. | List endpoints with cursor-based pagination. |
| **API versioning** | URL-based (`/api/v1/`). | Content negotiation or URL versioning with deprecation strategy. |
| **CORS / CSRF** | Disabled (API-only, no browser clients). | Proper CORS configuration for allowed origins. |
| **Audit trail archival** | Table grows indefinitely. | Time-based partitioning, S3 archival for records older than N months. |
| **Kafka exactly-once semantics** | At-least-once with consumer idempotency. | Kafka transactions + idempotent producer for exactly-once. |
| **Error response standardization** | Basic `GlobalExceptionHandler`. | RFC 7807 Problem Details responses with error codes catalog. |

### What We Deliberately Did Not Build

| Area | Why Not |
|---|---|
| **Kubernetes/Docker manifests** | Explicitly out of scope per constraints. |
| **UI / Dashboard** | Explicitly out of scope. |
| **Real IAM integration** | Explicitly out of scope — mock security within Spring Security filter chain. |
| **Multi-region / HA design** | Out of scope for a single-service timebox. Would need Kafka MirrorMaker, DB replication, etc. |
| **Payment refund/chargeback flow** | Not in requirements — would extend the state machine significantly. |
| **Webhook notifications** | Would consume the `payment.completed` topic and POST to merchant callback URLs. Logical next feature but out of scope. |

---

## Key Architectural Decisions vs. Timebox

| Decision | Time Cost | Value Delivered |
|---|---|---|
| Hexagonal architecture | +1 hour (vs. flat package) | High — clean boundaries, testable domain |
| Sealed interfaces for state machine | +30 min (vs. enum) | High — compiler-enforced exhaustiveness, per-state data |
| JPA `AttributeConverter` for encryption | +45 min (vs. plain text) | High — demonstrates PCI-aware data handling |
| Testcontainers for Kafka | +1 hour (vs. `@EmbeddedKafka` or mocking) | High — proves real pipeline works |
| OpenAPI spec YAML file | +30 min (vs. no spec) | Medium — demonstrates contract-first thinking |
| Custom Logback masking layout | +30 min (vs. no masking) | Medium — demonstrates security mindset |
| Idempotency race condition handling | +30 min (vs. simple check) | High — prevents duplicate charges under concurrency |

---

## Consequences

### What This Means for Evaluators
- The codebase demonstrates **production-shaped architecture** with clear extension points.
- Every simplification is a **conscious choice**, not an oversight — this ADR documents the reasoning.
- The code is structured so that upgrading any simplified area (e.g., H2 → PostgreSQL, mock JWT → real IdP) requires changes only in the `infrastructure` layer — the domain and application layers remain untouched.

### If More Time Were Available (Priority Order)
1. Add Resilience4j circuit breaker to fraud API and bank calls.
2. Implement RFC 7807 Problem Details for error responses.
3. Add OpenTelemetry distributed tracing.
4. Add Micrometer metrics + Prometheus endpoint.
5. Replace H2 with PostgreSQL Testcontainer and add Flyway migrations.
6. Add OpenAPI codegen for fraud client.
7. Add pagination to status inquiry endpoints.
8. Add webhook notification consumer for `payment.completed`.

