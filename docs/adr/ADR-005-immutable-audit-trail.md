# ADR-005: Immutable Audit Trail

**Status:** Accepted  
**Date:** 2026-03-10  
**Deciders:** Senior Development Team

---

## Context

Financial regulations (PCI-DSS, SOX, PSD2) require that every state change of a payment is reliably and immutably recorded for compliance, dispute resolution, and forensic analysis. The audit trail must:

- Capture **every** state transition — not just the current state.
- Be **append-only** — existing entries must never be modified or deleted.
- Be **reliable** — an audit entry must be written in the **same transaction** as the state change (no eventual consistency gap).
- Contain enough information to reconstruct the full lifecycle of any payment.

---

## Decision

### Dedicated `audit_trail` Table (Append-Only Event Log)

```
| Column         | Type         | Description                                     |
|----------------|--------------|--------------------------------------------------|
| id             | UUID (PK)    | Unique audit entry ID                            |
| payment_id     | UUID (FK)    | Reference to the payment                         |
| tenant_id      | VARCHAR      | Tenant who owns this payment                     |
| previous_status| VARCHAR      | Status before this transition (null for creation)|
| new_status     | VARCHAR      | Status after this transition                     |
| event_type     | VARCHAR      | What triggered the transition                    |
| metadata       | TEXT (JSON)  | Additional context (fraud score, bank ref, etc.) |
| performed_by   | VARCHAR      | System component that made the change            |
| created_at     | TIMESTAMP    | When the transition occurred (server time)       |
```

### Immutability Guarantees

| Mechanism | How |
|---|---|
| **No UPDATE/DELETE operations** | The `AuditTrailRepository` port exposes only `save()` and `findByPaymentId()` — no `update()` or `delete()` methods exist. |
| **JPA Entity design** | The `AuditTrailEntity` has no setter methods. All fields are set via the constructor and are effectively final. |
| **Database constraint** | A database trigger (or application-level check) prevents UPDATE/DELETE on the `audit_trail` table in production environments. For H2 (dev), this is enforced purely at the application layer. |
| **Same-transaction write** | Every service method that changes payment status writes the audit entry in the **same `@Transactional` block**. If the audit write fails, the state change is rolled back. |

### What Gets Audited

| State Transition | Event Type | Metadata |
|---|---|---|
| `→ SUBMITTED` | `PAYMENT_CREATED` | Original amount, currency, payment method (masked) |
| `SUBMITTED → FRAUD_CHECK_IN_PROGRESS` | `FRAUD_CHECK_STARTED` | Consumer group, partition, offset |
| `FRAUD_CHECK_IN_PROGRESS → FRAUD_APPROVED` | `FRAUD_CHECK_PASSED` | Fraud score, assessment duration |
| `FRAUD_CHECK_IN_PROGRESS → FRAUD_REJECTED` | `FRAUD_CHECK_FAILED` | Fraud score, rejection reason |
| `FRAUD_APPROVED → PROCESSING_BY_BANK` | `BANK_PROCESSING_STARTED` | Bank endpoint called |
| `PROCESSING_BY_BANK → COMPLETED` | `BANK_APPROVED` | Bank reference number, processing duration |
| `PROCESSING_BY_BANK → FAILED` | `BANK_REJECTED` | Rejection reason, error code |
| `Any → FAILED` (via DLT) | `PROCESSING_ERROR` | Exception message, retry count |

### Querying the Audit Trail

- Audit entries for a payment are retrievable via `GET /api/v1/payments/{id}/audit` (requires `ROLE_PAYMENT_VIEW` and tenant ownership).
- Results are ordered by `created_at ASC` — providing a chronological lifecycle view.
- Sensitive data in metadata is masked before returning to the client.

### Relationship to Kafka Events

The audit trail is **complementary** to Kafka events, not a replacement:

| Concern | Audit Trail (DB) | Kafka Events |
|---|---|---|
| **Purpose** | Compliance, dispute resolution | Inter-service communication |
| **Durability** | Permanent, never deleted | Retained per retention policy |
| **Consistency** | Same-transaction guarantee | At-least-once delivery |
| **Queryability** | SQL queries, indexed by payment_id | Requires consumer replay |
| **Sensitive data** | Metadata may contain masked PII | No PII in event payloads |

---

## Alternatives Considered

| Alternative | Why Rejected |
|---|---|
| **Rely solely on Kafka event log as audit trail** | Kafka retention is configurable and not permanent by default. Querying Kafka for a single payment's history is complex (requires stream processing). DB is simpler and queryable. |
| **Event Sourcing** | Full event sourcing (deriving state from events) is architecturally elegant but significantly more complex. For this scope, storing both current state and an append-only log is a pragmatic middle ground. |
| **Audit columns on the payment table** (e.g., `last_modified_by`, `last_modified_at`) | Only captures the most recent change — not the full history. Insufficient for compliance. |
| **CDC (Change Data Capture)** with Debezium | Powerful but adds infrastructure complexity (Debezium + Kafka Connect). Application-level audit is simpler and more explicit about what is recorded. |

---

## Consequences

### Positive
- Complete payment lifecycle is reconstructable from the audit trail.
- Same-transaction writes guarantee consistency — no "state changed but audit missing" scenarios.
- Append-only design prevents tampering with historical records.
- Audit entries are tenant-scoped, supporting multi-tenant compliance queries.

### Negative
- Additional database writes per state change (one INSERT into `audit_trail` per transition).
- JSON metadata column requires care to keep schema-consistent over time.
- Permanent retention means the `audit_trail` table grows indefinitely — will need archival strategy for production (partitioning by `created_at`).

