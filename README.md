# 🏦 Secure Event-Driven Payment Gateway

A production-grade backend engine for a payment gateway built with **Java 21**, **Spring Boot 4.0.3**, **Kafka**, and **Spring Security**. Designed using **Hexagonal Architecture** with an **event-driven asynchronous processing pipeline**.

## 📋 Table of Contents

- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Build & Test](#build--test)
- [Run Locally](#run-locally)
- [API Collections (Postman & Bruno)](#api-collections-postman--bruno)
- [API Usage (cURL)](#api-usage-curl)
- [H2 Database Console](#h2-database-console)
- [Swagger UI (OpenAPI)](#swagger-ui-openapi)
- [Transactional Outbox Pattern](#transactional-outbox-pattern)
- [Architecture Documentation](#architecture-documentation)
- [Architectural Decision Records](#architectural-decision-records)

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                            REST API Layer                                    │
│  POST /api/v1/payments  │  GET /api/v1/payments/{id}  │  GET .../audit      │
│  POST /api/v1/auth/token                                                     │
└──────────────┬───────────────────┬───────────────────────────────────────────┘
               │ JWT AuthN/AuthZ  │ Tenant Isolation
┌──────────────▼───────────────────▼───────────────────────────────────────────┐
│                         Spring Security Filter Chain                         │
│  JwtAuthenticationFilter → TenantContext → @PreAuthorize                     │
└──────────────┬───────────────────────────────────────────────────────────────┘
               │
┌──────────────▼───────────────────────────────────────────────────────────────┐
│                        Application Services                                  │
│  PaymentIngestionService → Idempotency Check → Save → Publish Event          │
│  PaymentQueryService     → Tenant-scoped lookup → Masked response            │
│  FraudProcessingService  → Fraud check → State transition → Audit            │
│  BankProcessingService   → Bank call → State transition → Audit              │
└──────────┬──────────┬──────────┬──────────────────────────────────────────────┘
           │          │          │
    ┌──────▼──┐ ┌─────▼────┐ ┌──▼──────────────────────────────────────┐
    │   H2    │ │  Kafka   │ │         External Services               │
    │   DB    │ │  Broker  │ │  MockFraudController (OpenAPI via HTTP)  │
    │ (AES-   │ │          │ │  SimulatedBankGateway (70% success)      │
    │  256-   │ │ Topics:  │ └──────────────────────────────────────────┘
    │  GCM)   │ │ payment. │
    │         │ │ submitted│
    └─────────┘ │ payment. │
                │ fraud-   │
                │ assessed │
                │ payment. │
                │ completed│
                └──────────┘
```

### Payment Processing Pipeline

```
POST /api/v1/payments (202 Accepted)
    → Idempotency check
    → Save payment (SUBMITTED) + Write event to outbox (same transaction)
    → OutboxPoller (every 100ms) relays to Kafka: payment.submitted
        → FraudAssessmentConsumer
            → Call fraud API (OpenAPI/HTTP)
            → FRAUD_APPROVED or FRAUD_REJECTED
            → Write event to outbox (same transaction as state change)
            → OutboxPoller relays to Kafka: payment.fraud-assessed or payment.completed
                → BankProcessingConsumer (if approved)
                    → Call bank simulator
                    → COMPLETED or FAILED
                    → Write event to outbox (same transaction as state change)
                    → OutboxPoller relays to Kafka: payment.completed

Every state transition → Immutable audit trail entry
Event publishing → Transactional Outbox Pattern (DB + Kafka atomicity)
Failures → 3 retries → Dead Letter Topic (<topic>.DLT)
```

---

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Language (sealed types, records, pattern matching) |
| Spring Boot | 4.0.3 | Framework |
| Spring Security | 7.x | JWT authentication & role-based authorization |
| Spring Kafka | 4.x | Event-driven messaging |
| Spring Data JPA | 4.x | Persistence |
| Hibernate | 7.x | ORM |
| H2 Database | 2.4.x | Embedded in-memory database |
| JJWT | 0.12.6 | JWT token generation & validation |
| Jackson (tools.jackson) | 3.0.4 | JSON serialization (Spring Boot 4) |
| JUnit 5 | 5.12.x | Testing framework |
| Awaitility | 4.x | Async test polling |
| EmbeddedKafka | 4.x | In-memory Kafka for tests |

---

## Prerequisites

- **Java 21+** (verify: `java -version`)
- **Maven 3.9+** (or use the included `./mvnw` wrapper)
- **Docker Desktop** (for running Kafka locally via docker-compose)

---

## Build & Test

```bash
# Build and run all 241 tests (unit + integration + E2E)
./mvnw clean verify

# Run only unit tests (fast, no Kafka needed)
./mvnw test -Dgroups="unit"

# Run a specific test class
./mvnw test -Dtest="FullPipelineIntegrationTest"
```

---

## Run Locally

### Option A: Kafka in Docker + App via Maven (recommended for development)

```bash
# 1. Start Kafka
docker-compose up -d

# Wait for Kafka to be healthy
docker-compose ps
# kafka should show "healthy"

# 2. Start the Application
./mvnw spring-boot:run
# App starts on http://localhost:8080

# 3. Stop
# Stop the app: Ctrl+C
docker-compose down
```

### Option B: Everything in Docker (Kafka + App)

```bash
# Build and start both Kafka and the Spring Boot app
docker-compose --profile app up -d --build

# App available at http://localhost:8080
# Wait ~30s for the app to start and become healthy

docker-compose --profile app ps
# Both kafka and payment-gateway-app should be healthy

# Stop everything
docker-compose --profile app down
```

### Docker Compose Services

| Service | Container | Port | Profile |
|---|---|---|---|
| Kafka (KRaft) | `payment-gateway-kafka` | 9092 | *(default)* |
| Spring Boot App | `payment-gateway-app` | 8080 | `app` |

---

## API Collections (Postman & Bruno)

Pre-built API collections are available in [`api-collections/`](api-collections/):

### Postman

1. Open Postman → **Import** → **Upload Files**
2. Select `api-collections/postman/Payment-Gateway-API.postman_collection.json`
3. Run requests in order: **Auth → Payments → Fraud Scenarios → Security**
4. Variables (`token`, `paymentId`) are auto-populated by test scripts

### Bruno

1. Open Bruno → **Open Collection**
2. Select the `api-collections/bruno/Payment-Gateway` folder
3. Set environment to **Local** (`http://localhost:8080`)
4. Run "Get JWT Token" first, then explore the other folders

### Collection Coverage

| Folder | Requests | Tests |
|---|---|---|
| **Auth** | Get JWT Token, View-Only Token, Tenant B Token | Token issuance |
| **Payments** | Submit (Success/Random), Get Status, Get Audit | Core CRUD |
| **Fraud Scenarios** | High Amount, Bank Failure, Borderline | Pipeline outcomes |
| **Idempotency** | First Call (202), Duplicate (200) | Idempotency proof |
| **Security Tests** | No Token (401), Wrong Role (403), Tenant Isolation (404) | Security posture |

> See [`api-collections/README.md`](api-collections/README.md) for detailed instructions and test hook reference.

---

## API Usage (cURL)

### 1. Obtain a JWT Token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "merchant-abc",
    "tenantId": "tenant-001",
    "roles": ["PAYMENT_SUBMIT", "PAYMENT_VIEW"]
  }' | jq -r '.token')

echo $TOKEN
```

### 2. Submit a Payment

```bash
# Successful payment (amount 11.11 → bank always succeeds)
PAYMENT_ID=$(curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "amount": 11.11,
    "currency": "USD",
    "creditorAccount": "NL91ABNA0417164300",
    "debtorAccount": "DE89370400440532013000",
    "paymentMethod": "BANK_TRANSFER"
  }' | jq -r '.paymentId')

echo "Payment ID: $PAYMENT_ID"
# Response: 202 Accepted with paymentId
```

### 3. Query Payment Status

```bash
# Wait a few seconds for async processing, then:
curl -s http://localhost:8080/api/v1/payments/$PAYMENT_ID \
  -H "Authorization: Bearer $TOKEN" | jq

# Expected response:
# {
#   "paymentId": "...",
#   "status": "COMPLETED",
#   "amount": 11.11,
#   "currency": "USD",
#   "creditorAccount": "****4300",    ← masked
#   "debtorAccount": "****3000",      ← masked
#   "paymentMethod": "BANK_TRANSFER",
#   "createdAt": "..."
# }
```

### 4. Query Audit Trail

```bash
curl -s http://localhost:8080/api/v1/payments/$PAYMENT_ID/audit \
  -H "Authorization: Bearer $TOKEN" | jq '.[].newStatus'

# Expected: ["SUBMITTED", "FRAUD_CHECK_IN_PROGRESS", "FRAUD_APPROVED", "PROCESSING_BY_BANK", "COMPLETED"]
```

### 5. Test Idempotency

```bash
IDEMP_KEY=$(uuidgen)

# First call → 202 Accepted
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMP_KEY" \
  -d '{"amount":50,"currency":"USD","creditorAccount":"NL91ABNA0417164300","debtorAccount":"DE89370400440532013000","paymentMethod":"CARD"}'
# Output: 202

# Second call with SAME key → 200 OK (same paymentId returned)
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMP_KEY" \
  -d '{"amount":50,"currency":"USD","creditorAccount":"NL91ABNA0417164300","debtorAccount":"DE89370400440532013000","paymentMethod":"CARD"}'
# Output: 200
```

### 6. Test Fraud Rejection

```bash
# Amount >= 10,000 → fraud rejected
FRAUD_ID=$(curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount":15000,"currency":"USD","creditorAccount":"NL91ABNA0417164300","debtorAccount":"DE89370400440532013000","paymentMethod":"CARD"}' \
  | jq -r '.paymentId')

sleep 3
curl -s http://localhost:8080/api/v1/payments/$FRAUD_ID \
  -H "Authorization: Bearer $TOKEN" | jq '.status'
# Expected: "FRAUD_REJECTED"
```

### 7. Test Bank Failure

```bash
# Amount 99.99 → bank always fails
FAIL_ID=$(curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount":99.99,"currency":"USD","creditorAccount":"NL91ABNA0417164300","debtorAccount":"DE89370400440532013000","paymentMethod":"CARD"}' \
  | jq -r '.paymentId')

sleep 3
curl -s http://localhost:8080/api/v1/payments/$FAIL_ID \
  -H "Authorization: Bearer $TOKEN" | jq '.status'
# Expected: "FAILED"
```

### 8. Test Tenant Isolation

```bash
# Get token for a different tenant
TOKEN_B=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"subject":"other","tenantId":"tenant-002","roles":["PAYMENT_VIEW"]}' | jq -r '.token')

# Try to view tenant-001's payment → 404
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/payments/$PAYMENT_ID \
  -H "Authorization: Bearer $TOKEN_B"
# Output: 404
```

### 9. Test Security

```bash
# No token → 401
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/payments/$PAYMENT_ID
# Output: 401

# Wrong role (PAYMENT_VIEW only, no PAYMENT_SUBMIT) → 403
VIEW_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"subject":"viewer","tenantId":"t1","roles":["PAYMENT_VIEW"]}' | jq -r '.token')

curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $VIEW_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount":10,"currency":"USD","creditorAccount":"NL91ABNA0417164300","debtorAccount":"DE89370400440532013000","paymentMethod":"CARD"}'
# Output: 403
```

---

## H2 Database Console

Available at **http://localhost:8080/h2-console** when running locally.

| Setting | Value |
|---|---|
| JDBC URL | `jdbc:h2:mem:paymentdb` |
| Username | `sa` |
| Password | *(empty)* |

**Tables:**
- `payments` — Payment records (account fields encrypted with AES-256-GCM)
- `idempotency_keys` — Unique constraint on `(tenant_id, idempotency_key)`
- `audit_trail` — Append-only immutable audit log

---

## Swagger UI (OpenAPI)

Interactive API documentation available at **http://localhost:8080/swagger-ui.html** when running locally.

| Resource | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI 3.0 spec (JSON) | http://localhost:8080/v3/api-docs |

**How to use:**
1. Open Swagger UI in your browser
2. Run **POST /api/v1/auth/token** (no auth required) to get a JWT token
3. Click the **Authorize 🔒** button at the top
4. Paste the token and click **Authorize**
5. Now all payment endpoints are authenticated — try them out!

---

## Transactional Outbox Pattern

All domain events (payment submitted, fraud assessed, bank processed) are written to an **`outbox_events`** table inside the **same DB transaction** as the domain state change. A scheduled poller relays them to Kafka.

### Why?

In a payment system, losing events is unacceptable. Without the outbox, a crash between `DB COMMIT` and `Kafka publish` would leave payments stuck forever.

```
Without Outbox (UNSAFE):                   With Outbox (SAFE):
┌─────────────────────┐                    ┌─────────────────────────────────┐
│ @Transactional       │                    │ @Transactional                   │
│ 1. Save payment ✓    │                    │ 1. Save payment ✓                │
│ 2. Save audit   ✓    │                    │ 2. Save audit   ✓                │
│ --- COMMIT ---       │                    │ 3. Save outbox  ✓ (same TX!)     │
│ 3. Kafka publish  💥 │ ← crash = lost!   │ --- COMMIT ---                   │
└─────────────────────┘                    └─────────────────────────────────┘
                                            Poller (every 100ms) → Kafka ✓
```

### Current Implementation (Polling)

| Component | Role |
|---|---|
| `OutboxPaymentEventPublisher` | `@Primary` — writes to `outbox_events` table inside the transaction |
| `OutboxPollerService` | `@Scheduled(100ms)` — reads unpublished rows → publishes to Kafka → marks published |
| `KafkaPaymentEventPublisher` | Called by the poller to do the actual Kafka send |

**Latency:** ~100ms (poll interval). Configurable via `app.outbox.poll-interval-ms`.

### Production Upgrade: Debezium CDC

For near-zero latency (~10-50ms), replace the polling outbox with **Debezium Change Data Capture**:

```
Current (Polling, ~100ms):                  Production (Debezium CDC, ~10-50ms):
┌──────────┐  poll every   ┌───────┐        ┌──────────┐  WAL stream  ┌───────────┐  auto  ┌───────┐
│  outbox   │ ──100ms────► │ Kafka │        │  outbox   │ ──────────► │ Debezium  │ ─────► │ Kafka │
│  table    │              │       │        │  table    │   ~10ms     │ Connector │        │       │
└──────────┘              └───────┘        └──────────┘              └───────────┘        └───────┘
```

**Why Debezium is not used here:**
- **H2 not supported** — Debezium needs PostgreSQL/MySQL WAL. Assignment requires H2.
- **Infrastructure** — Requires Kafka Connect cluster. Assignment says "no complex infra."

**Migration steps to Debezium:**
1. Switch H2 → PostgreSQL (`wal_level=logical`)
2. Deploy Kafka Connect + Debezium PostgreSQL connector
3. Point connector at `outbox_events` table
4. Use [Outbox Event Router SMT](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html) for topic routing
5. Remove `OutboxPollerService` — Debezium replaces it
6. `OutboxPaymentEventPublisher` and `outbox_events` table remain **unchanged**

> See [ADR-003](docs/adr/ADR-003-event-driven-design.md) for the full design rationale.

---

## Architecture Documentation

Full documentation is in the [`docs/`](docs/) directory:

| Document | Description | Read Time |
|---|---|---|
| [Reading Guide](docs/READING-GUIDE.md) | Recommended reading order for all docs | 5 min |
| [Implementation Report](docs/IMPLEMENTATION-REPORT.md) | Step-by-step build log with metrics | 15 min |
| [Implementation Plan](docs/IMPLEMENTATION-PLAN.md) | Detailed 12-step plan with testable milestones | 20 min |

---

## Architectural Decision Records

| ADR | Title | Key Decision |
|---|---|---|
| [ADR-001](docs/adr/ADR-001-security-design.md) | Security Architecture | JWT (HMAC-SHA512) + RBAC + AES-256-GCM encryption |
| [ADR-002](docs/adr/ADR-002-idempotency-strategy.md) | Idempotency Strategy | DB unique constraint + tenant-scoped keys (Redis cache in production) |
| [ADR-003](docs/adr/ADR-003-event-driven-design.md) | Event-Driven Design | Kafka + Transactional Outbox Pattern + DLT |
| [ADR-004](docs/adr/ADR-004-domain-modeling-patterns.md) | Domain Modeling | Sealed types + pattern matching state machine |
| [ADR-005](docs/adr/ADR-005-immutable-audit-trail.md) | Immutable Audit Trail | Append-only JPA entity, no UPDATE/DELETE |
| [ADR-006](docs/adr/ADR-006-testing-strategy.md) | Testing Strategy | Unit + Integration + E2E with EmbeddedKafka |
| [ADR-007](docs/adr/ADR-007-fraud-openapi-integration.md) | Fraud Assessment | OpenAPI 3.1 spec + in-app mock via HTTP |
| [ADR-008](docs/adr/ADR-008-design-tradeoffs.md) | Design Trade-offs | Scope decisions for 8-hour timebox |

---

## Modern Java 21 Features Used

| Feature | Where |
|---|---|
| **Virtual threads** | Enabled globally via `spring.threads.virtual.enabled=true` — Tomcat, Kafka listeners, async tasks all run on virtual threads |
| **Sealed interfaces** | `PaymentStatus` (7 states), `TransitionEvent` (6 events), `PaymentEvent` (3 events) |
| **Records** | All DTOs, value objects, commands, events (~22 record types) |
| **Pattern matching** | Exhaustive `switch` in `PaymentStatusTransitionEngine`, `KafkaPaymentEventPublisher` |
| **Compact constructors** | Validation in `Money`, `FraudRejected`, etc. |

## Security Posture

| Layer | Protection |
|---|---|
| Authentication | JWT Bearer tokens (HMAC-SHA512) |
| Authorization | `@PreAuthorize` role-based access control |
| Multi-tenancy | Tenant ID in JWT + tenant-scoped DB queries |
| Data at rest | AES-256-GCM encryption (account numbers) |
| API responses | Masked account numbers (`****4300`) |
| Log output | Regex-based PII masking (IBAN, PAN, JWT) |
| Error handling | No stack traces or internal details leaked |
| Kafka events | No PCI data in event payloads (IDs only) |

## Test Summary

| Category | Count |
|---|---|
| Unit tests | ~171 |
| Integration tests (MockMvc + JPA) | ~65 |
| E2E tests (RestClient + Kafka) | 14 |
| **Total** | **250** |
| Failures | **0** |

---

*Built with ❤️ using Java 21, Spring Boot 4.0.3, and Hexagonal Architecture*

