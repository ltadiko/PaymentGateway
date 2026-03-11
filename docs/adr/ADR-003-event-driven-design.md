# ADR-003: Event-Driven Architecture Design

**Status:** Accepted  
**Date:** 2026-03-10  
**Deciders:** Senior Development Team

---

## Context

The payment gateway must decouple payment ingestion (synchronous, high-throughput) from payment processing (asynchronous, multi-phase). The processing pipeline involves:

1. **Fraud Assessment** — calling an external fraud detection service.
2. **Acquiring Bank Simulation** — calling a downstream bank with variable latency and random failures.

Each phase may fail independently, and the system must handle failures gracefully without losing payment data.

---

## Decision

### Message Broker: Apache Kafka

**Why Kafka over RabbitMQ/ActiveMQ:**

| Criterion | Kafka | RabbitMQ |
|---|---|---|
| **Ordering** | Per-partition ordering guaranteed | Per-queue ordering (single consumer) |
| **Replay** | Messages retained, re-consumable | Messages deleted after ACK |
| **Throughput** | Higher throughput for payment volumes | Sufficient but lower ceiling |
| **Audit** | Natural event log — aligns with immutable audit trail requirement | No built-in retention |
| **Ecosystem** | Spring Kafka is mature and well-integrated | Spring AMQP is also mature |

Kafka's log-based architecture naturally supports the **immutable audit trail** requirement — events are retained and can be replayed for debugging or reconciliation.

### Topic Design

| Topic | Key | Purpose | Producer | Consumer |
|---|---|---|---|---|
| `payment.submitted` | `paymentId` | Payment ingested, ready for fraud check | PaymentIngestionService | FraudAssessmentConsumer |
| `payment.fraud-assessed` | `paymentId` | Fraud check complete (approved), ready for bank | FraudAssessmentConsumer | BankProcessingConsumer |
| `payment.completed` | `paymentId` | Final state reached (success or failure) | BankProcessingConsumer | (Future: notifications, analytics) |
| `*.DLT` (Dead Letter Topics) | `paymentId` | Failed messages after retry exhaustion | Kafka (automatic) | DLT monitoring/alerting |

### Event Payload Design

Events are intentionally **lean** — they carry references, not data:

```json
{
  "eventId": "evt-uuid-001",
  "paymentId": "pay-uuid-001",
  "tenantId": "tenant-001",
  "status": "FRAUD_APPROVED",
  "timestamp": "2026-03-10T14:30:00Z",
  "metadata": {
    "fraudScore": 15,
    "assessmentDuration": "120ms"
  }
}
```

**Why no sensitive data in events?**
- PII/PCI data must not traverse the message bus — consumers re-fetch from the database if needed.
- Reduces the blast radius of a broker compromise.
- Simplifies event schema evolution (no sensitive field migration concerns).

### Message Keying Strategy

- All messages are keyed by `paymentId`.
- Kafka guarantees ordering within a partition — messages with the same key always go to the same partition.
- This ensures that state transitions for a single payment are processed in order, even with multiple consumer instances.

### Consumer Configuration

```yaml
# Retry Policy
spring.kafka.consumer:
  max-poll-records: 10
  auto-offset-reset: earliest
  enable-auto-commit: false

# Retry with backoff
retry:
  max-attempts: 3
  backoff:
    initial-interval: 1000ms
    multiplier: 2.0
    max-interval: 10000ms
```

### Failure Handling Strategy

```
Message arrives at consumer
    │
    ▼
┌──────────────────┐
│ Process message   │
└────────┬─────────┘
         │
    ┌────┴────┐
    │         │
 SUCCESS    FAILURE
    │         │
    ▼         ▼
  Commit   Retry (up to 3x with exponential backoff)
  offset       │
               │
          ┌────┴────┐
          │         │
       SUCCESS    ALL RETRIES EXHAUSTED
          │         │
          ▼         ▼
        Commit   ┌─────────────────────────────┐
        offset   │ 1. Update payment → FAILED   │
                 │ 2. Record audit trail entry   │
                 │ 3. Send to Dead Letter Topic  │
                 └─────────────────────────────┘
```

### Dead Letter Topic (DLT) Handling

- Spring Kafka's `DefaultErrorHandler` with `DeadLetterPublishingRecoverer` is configured.
- Failed messages are published to `<original-topic>.DLT` with additional headers:
  - `x-original-topic`
  - `x-exception-message`
  - `x-original-timestamp`
- DLT messages can be manually inspected and replayed after fixing the root cause.

### Consumer Group Design

| Consumer Group | Subscribes To | Responsibility |
|---|---|---|
| `fraud-assessment-group` | `payment.submitted` | Invoke fraud check, transition state |
| `bank-processing-group` | `payment.fraud-assessed` | Invoke bank simulation, finalize payment |

Each group can be scaled independently by adding more consumer instances (up to the number of partitions).

---

## Processing Pipeline Flow

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐     ┌───────────────┐
│   REST API   │     │  Kafka Topic:    │     │  Kafka Topic:    │     │  Kafka Topic: │
│  (Ingestion) │────►│  payment.        │────►│  payment.        │────►│  payment.     │
│              │     │  submitted       │     │  fraud-assessed  │     │  completed    │
└──────────────┘     └──────────────────┘     └──────────────────┘     └───────────────┘
                           │                        │                        │
                           ▼                        ▼                        ▼
                     ┌───────────┐           ┌───────────┐           ┌───────────┐
                     │  Fraud    │           │   Bank    │           │  (Future) │
                     │  Consumer │           │  Consumer │           │  Consumer │
                     └───────────┘           └───────────┘           └───────────┘
                           │                        │
                           ▼                        ▼
                     ┌───────────┐           ┌───────────┐
                     │  External │           │ Simulated │
                     │  Fraud    │           │   Bank    │
                     │  API      │           │  Gateway  │
                     │  (Mock)   │           │           │
                     └───────────┘           └───────────┘
```

---

## Transactional Outbox Pattern

### Problem

In the naive approach, the service commits DB changes and then publishes to Kafka **outside** the transaction. If the app crashes between the DB commit and the Kafka publish, the event is lost and the payment gets stuck forever.

```
@Transactional
submit():
  1. Save payment to DB         ← committed ✓
  2. Save audit entry to DB     ← committed ✓
  --- TRANSACTION COMMITS ---
  3. Publish to Kafka            ← APP CRASHES HERE → event lost!
```

### Solution

Events are written to an `outbox_events` table **inside** the same DB transaction. A scheduled poller reads unpublished events and relays them to Kafka.

```
@Transactional
submit():
  1. Save payment to DB         ← committed together
  2. Save audit entry to DB     ← committed together
  3. Save event to outbox_events ← committed together
  --- TRANSACTION COMMITS ---

@Scheduled (every 1s)
pollAndPublish():
  1. SELECT * FROM outbox_events WHERE published = false
  2. For each event → publish to Kafka
  3. Mark published = true
```

### Outbox Table Schema

| Column | Type | Purpose |
|---|---|---|
| `id` | UUID | Primary key |
| `aggregate_type` | VARCHAR(100) | Always "Payment" |
| `aggregate_id` | UUID | The paymentId (used as Kafka key) |
| `event_type` | VARCHAR(100) | e.g., "PaymentSubmitted" (for deserialization routing) |
| `payload` | TEXT | JSON-serialized `PaymentEvent` |
| `created_at` | TIMESTAMP | When the event was written |
| `published` | BOOLEAN | Whether the poller has relayed it to Kafka |

### Guarantees

- **Atomicity:** If the DB transaction rolls back, the outbox entry is also rolled back → no phantom events.
- **Durability:** If the DB transaction commits, the event is guaranteed to be in the outbox → no lost events.
- **At-least-once delivery:** If the app crashes after Kafka publish but before marking `published=true`, the event will be re-published on the next poll. Downstream consumers are idempotent (via `eventId`).
- **Cleanup:** Published events older than 7 days are automatically deleted by a scheduled cleanup job.

### Implementation Components

| Component | Role |
|---|---|
| `OutboxEventEntity` | JPA entity for `outbox_events` table |
| `OutboxPaymentEventPublisher` | `@Primary` `PaymentEventPublisher` — writes to outbox instead of Kafka |
| `KafkaPaymentEventPublisher` | Actual Kafka send — now called by the poller, not by services |
| `OutboxPollerService` | `@Scheduled` poller — reads outbox → publishes to Kafka → marks published |

---

## Alternatives Considered

| Alternative | Why Rejected |
|---|---|
| **RabbitMQ** | Good choice, but Kafka's log retention aligns better with the audit trail requirement. Kafka also provides stronger ordering guarantees when scaling consumers. |
| **Spring ApplicationEvents (in-process)** | No durability — if the process crashes, in-flight events are lost. A true broker provides persistence and consumer group coordination. |
| **Direct Kafka publish (no outbox)** | Simpler but creates a crash window between DB commit and Kafka publish. Unacceptable for a payment system where lost events mean stuck payments. |
| **Saga orchestrator** | Overkill for two processing steps. A simple choreography-based approach (event chain) is sufficient and simpler. |
| **Single topic with event type routing** | Reduces topic count but complicates consumer logic and makes independent scaling impossible. One-topic-per-transition is cleaner. |
| **Debezium CDC** | Production-grade alternative to polling — captures DB changes via WAL. Overkill for H2/assignment scope; would be the preferred choice at scale. |

---

## Consequences

### Positive
- Clean separation: ingestion is fast (just write to DB + outbox), processing is independent.
- **Transactional Outbox** eliminates the crash window — no more "committed but not published" events.
- Each processing phase can be deployed, scaled, and monitored independently.
- Kafka's log retention provides a natural audit/replay mechanism.
- DLT ensures no message is silently lost — failed payments are always trackable.
- Message keying guarantees per-payment ordering.

### Negative
- Kafka adds infrastructure complexity (mitigated by Testcontainers for testing, embedded broker for dev).
- Eventually consistent: there's a brief window (poller interval, default 1s) between ingestion and processing.
- The outbox poller adds a small latency (~1s) between event creation and Kafka publish.
- Requires careful consumer idempotency — consumers must handle redelivery (at-least-once semantics).

### Monitoring Considerations (Future)
- Track consumer lag per group to detect processing delays.
- Alert on DLT message count > 0.
- Track end-to-end latency (ingestion → completed) via event timestamps.

