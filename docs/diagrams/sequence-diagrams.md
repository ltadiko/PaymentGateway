# Sequence Diagrams

All diagrams use [Mermaid](https://mermaid.js.org/) syntax and can be rendered in GitHub, GitLab, IntelliJ, or VS Code with a Mermaid plugin.

---

## Outbox Pattern Legend

> **Outbox Pattern:**
> - Domain event is written to the `outbox_events` table in the same DB transaction as the state change.
> - `OutboxPoller` (background process) reads unpublished events and publishes to Kafka.
> - Ensures atomicity: no event is lost between DB commit and Kafka publish.

---

## 1. Payment Ingestion — Happy Path

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant F as JwtAuthFilter
    participant R as PaymentController
    participant S as PaymentIngestionService
    participant DB as Database (H2)
    participant O as OutboxPoller
    participant OB as Outbox Table
    participant K as Kafka

    C->>+F: POST /api/v1/payments<br/>Authorization: Bearer JWT<br/>Idempotency-Key: uuid-abc

    F->>F: Validate JWT signature & expiry
    F->>F: Extract tenantId, roles
    F->>F: Set SecurityContext + TenantContext

    F->>+R: Forward request (authenticated)

    R->>R: @PreAuthorize("hasRole('PAYMENT_SUBMIT')")
    R->>+S: submitPayment(command, tenantId, idempotencyKey)

    S->>+DB: SELECT FROM idempotency_keys<br/>WHERE tenant_id=? AND key=?
    DB-->>-S: NOT FOUND

    S->>S: Validate payment data
    S->>S: Create Payment (status=SUBMITTED)
    S->>S: Encrypt sensitive fields (AES-256)

    S->>+DB: BEGIN TRANSACTION
    S->>DB: INSERT INTO payments (...)
    S->>DB: INSERT INTO idempotency_keys (...)
    S->>DB: INSERT INTO audit_trail (SUBMITTED)
    S->>OB: INSERT INTO outbox_events (payment.submitted)
    DB-->>-S: COMMIT OK

    S-->>-R: PaymentStatusResponse(paymentId, SUBMITTED)

    R-->>-F: 202 Accepted
    F-->>-C: HTTP 202<br/>{ "paymentId": "pay-uuid-001", "status": "SUBMITTED" }

    %% OutboxPoller runs in background
    O->>OB: Poll for unpublished events
    OB-->>O: payment.submitted event
    O->>+K: Publish to "payment.submitted"<br/>key=paymentId
    K-->>-O: ACK
    O->>OB: Mark event as published
```

---

## 2. Payment Ingestion — Idempotent Duplicate

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant F as JwtAuthFilter
    participant R as PaymentController
    participant S as PaymentIngestionService
    participant DB as Database (H2)

    C->>+F: POST /api/v1/payments<br/>Idempotency-Key: uuid-abc (SAME KEY)

    F->>F: Validate JWT
    F->>+R: Forward request

    R->>+S: submitPayment(command, tenantId, idempotencyKey)

    S->>+DB: SELECT FROM idempotency_keys<br/>WHERE tenant_id=? AND key=?
    DB-->>-S: FOUND (existing record)

    S->>S: Deserialize stored response

    S-->>-R: Original PaymentStatusResponse

    R-->>-F: 200 OK (not 201/202)
    F-->>-C: HTTP 200<br/>{ original response }

    Note over C,DB: No new payment created.<br/>No outbox event written.<br/>No Kafka event published.<br/>No duplicate charge.
```

---

## 3. Async Processing Pipeline — Happy Path

```mermaid
sequenceDiagram
    autonumber
    participant O as OutboxPoller
    participant OB as Outbox Table
    participant K1 as Kafka: payment.submitted
    participant FC as FraudAssessment Consumer
    participant FA as Fraud API (Mock)
    participant DB as Database
    participant OB2 as Outbox Table
    participant O2 as OutboxPoller
    participant K2 as Kafka: payment.fraud-assessed
    participant BC as BankProcessing Consumer
    participant BK as Bank Simulator
    participant OB3 as Outbox Table
    participant O3 as OutboxPoller
    participant K3 as Kafka: payment.completed

    %% OutboxPoller relays payment.submitted
    O->>OB: Poll for unpublished events
    OB-->>O: payment.submitted event
    O->>+K1: Publish to payment.submitted
    K1-->>-O: ACK
    O->>OB: Mark event as published

    K1->>+FC: Consume event (paymentId, tenantId)

    FC->>+DB: Update status -> FRAUD_CHECK_IN_PROGRESS
    FC->>DB: INSERT audit_trail
    FC->>OB2: INSERT INTO outbox_events (payment.fraud-assessed)
    DB-->>-FC: COMMIT OK

    FC->>+FA: POST /api/fraud/assess
    FA-->>-FC: { approved: true, score: 15 }

    FC->>+DB: Update status -> FRAUD_APPROVED
    FC->>DB: INSERT audit_trail
    FC->>OB2: INSERT INTO outbox_events (payment.fraud-assessed)
    DB-->>-FC: COMMIT OK

    %% OutboxPoller relays payment.fraud-assessed
    O2->>OB2: Poll for unpublished events
    OB2-->>O2: payment.fraud-assessed event
    O2->>+K2: Publish to payment.fraud-assessed
    K2-->>-O2: ACK
    O2->>OB2: Mark event as published

    K2->>+BC: Consume event (paymentId, tenantId)

    BC->>+DB: Update status -> PROCESSING_BY_BANK
    BC->>DB: INSERT audit_trail
    BC->>OB3: INSERT INTO outbox_events (payment.completed)
    DB-->>-BC: COMMIT OK

    BC->>+BK: Process payment (random 100-3000ms latency)
    BK-->>-BC: { success: true, bankRef: BNK-12345 }

    BC->>+DB: Update status -> COMPLETED
    BC->>DB: INSERT audit_trail
    BC->>OB3: INSERT INTO outbox_events (payment.completed)
    DB-->>-BC: COMMIT OK

    %% OutboxPoller relays payment.completed
    O3->>OB3: Poll for unpublished events
    OB3-->>O3: payment.completed event
    O3->>+K3: Publish to payment.completed
    K3-->>-O3: ACK
    O3->>OB3: Mark event as published
```

---

## 4. Async Processing — Fraud Rejection

```mermaid
sequenceDiagram
    autonumber
    participant O as OutboxPoller
    participant OB as Outbox Table
    participant K1 as Kafka: payment.submitted
    participant FC as FraudAssessment Consumer
    participant FA as Fraud API (Mock)
    participant DB as Database
    participant OB2 as Outbox Table
    participant O2 as OutboxPoller
    participant K3 as Kafka: payment.completed

    %% OutboxPoller relays payment.submitted
    O->>OB: Poll for unpublished events
    OB-->>O: payment.submitted event
    O->>+K1: Publish to payment.submitted
    K1-->>-O: ACK
    O->>OB: Mark event as published

    K1->>+FC: Consume event (paymentId, tenantId)

    FC->>+DB: Update status -> FRAUD_CHECK_IN_PROGRESS
    FC->>OB2: INSERT INTO outbox_events (payment.completed)
    DB-->>-FC: COMMIT OK

    FC->>+FA: POST /api/fraud/assess
    FA-->>-FC: { approved: false, reason: High risk }

    FC->>+DB: Update status -> FRAUD_REJECTED
    FC->>OB2: INSERT INTO outbox_events (payment.completed)
    DB-->>-FC: COMMIT OK

    %% OutboxPoller relays payment.completed
    O2->>OB2: Poll for unpublished events
    OB2-->>O2: payment.completed event
    O2->>+K3: Publish to payment.completed (FAILED)
    K3-->>-O2: ACK
    O2->>OB2: Mark event as published

    Note over K1,K3: Pipeline stops. No bank processing.
```

---

## 5. Async Processing — Bank Failure

```mermaid
sequenceDiagram
    autonumber
    participant O2 as OutboxPoller
    participant OB2 as Outbox Table
    participant K2 as Kafka: payment.fraud-assessed
    participant BC as BankProcessing Consumer
    participant BK as Bank Simulator
    participant OB3 as Outbox Table
    participant O3 as OutboxPoller
    participant K3 as Kafka: payment.completed

    %% OutboxPoller relays payment.fraud-assessed
    O2->>OB2: Poll for unpublished events
    OB2-->>O2: payment.fraud-assessed event
    O2->>+K2: Publish to payment.fraud-assessed
    K2-->>-O2: ACK
    O2->>OB2: Mark event as published

    K2->>+BC: Consume event (paymentId)

    BC->>+DB: Update status -> PROCESSING_BY_BANK
    BC->>OB3: INSERT INTO outbox_events (payment.completed)
    DB-->>-BC: COMMIT OK

    BC->>+BK: Process payment
    BK-->>-BC: { success: false, reason: Insufficient funds }

    BC->>+DB: Update status -> FAILED
    BC->>OB3: INSERT INTO outbox_events (payment.completed)
    DB-->>-BC: COMMIT OK

    %% OutboxPoller relays payment.completed
    O3->>OB3: Poll for unpublished events
    OB3-->>O3: payment.completed event
    O3->>+K3: Publish to payment.completed (FAILED)
    K3-->>-O3: ACK
    O3->>OB3: Mark event as published
```

---

## 6. Consumer Failure — Dead Letter Topic

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka: payment.submitted
    participant FC as FraudAssessment Consumer
    participant DB as Database
    participant OB as Outbox Table
    participant O as OutboxPoller
    participant DLT as Kafka: payment.submitted.DLT

    K->>+FC: Attempt 1
    FC->>FC: Exception thrown
    FC-->>-K: NACK

    Note over K,FC: Backoff: 1s

    K->>+FC: Attempt 2
    FC->>FC: Exception thrown
    FC-->>-K: NACK

    Note over K,FC: Backoff: 2s

    K->>+FC: Attempt 3
    FC->>FC: Exception thrown
    FC-->>-K: NACK

    Note over K,FC: Backoff: 4s - FINAL attempt exhausted

    FC->>+DB: Update payment -> FAILED
    FC->>OB: INSERT INTO outbox_events (payment.submitted.DLT)
    DB-->>-FC: COMMIT OK

    %% OutboxPoller relays DLT event
    O->>OB: Poll for unpublished events
    OB-->>O: payment.submitted.DLT event
    O->>+DLT: Publish to payment.submitted.DLT
    DLT-->>-O: ACK
    O->>OB: Mark event as published

    Note over K,DLT: Message preserved in DLT for manual replay
```

---

## 9. Full End-to-End Flow (Overview)

```mermaid
flowchart LR
    subgraph Client
        A[POST /payments]
    end

    subgraph API Layer
        B[JWT Auth Filter]
        C[Payment Controller]
    end

    subgraph Application Layer
        D[Ingestion Service]
    end

    subgraph Data Layer
        E[(H2 Database)]
        OB[(Outbox Table)]
    end

    subgraph Outbox
        O[OutboxPoller]
    end

    subgraph Event Bus
        F[[Kafka: payment.submitted]]
        G[[Kafka: payment.fraud-assessed]]
        H[[Kafka: payment.completed]]
        I[[Dead Letter Topics]]
    end

    subgraph Processing
        J[Fraud Consumer]
        K[Bank Consumer]
    end

    subgraph External
        L[Fraud API Mock]
        M[Bank Simulator]
    end

    A --> B --> C --> D
    D --> E
    D --> OB
    O --> OB
    OB --> F
    F --> J
    J --> L
    J --> E
    J --> OB
    O --> OB
    OB --> G
    G --> K
    K --> M
    K --> E
    K --> OB
    O --> OB
    OB --> H
    J -->|error after retries| I
    K -->|error after retries| I
```

---

## 10. Payment State Machine

```mermaid
stateDiagram-v2
    [*] --> SUBMITTED : Payment ingested

    SUBMITTED --> FRAUD_CHECK_IN_PROGRESS : Fraud consumer picks up

    FRAUD_CHECK_IN_PROGRESS --> FRAUD_APPROVED : Fraud score below threshold
    FRAUD_CHECK_IN_PROGRESS --> FRAUD_REJECTED : Fraud score above threshold

    FRAUD_APPROVED --> PROCESSING_BY_BANK : Bank consumer picks up

    PROCESSING_BY_BANK --> COMPLETED : Bank approves
    PROCESSING_BY_BANK --> FAILED : Bank rejects / timeout

    FRAUD_REJECTED --> [*] : Terminal state
    COMPLETED --> [*] : Terminal state
    FAILED --> [*] : Terminal state

    note right of SUBMITTED : Idempotency-Key prevents duplicates
    note right of FRAUD_CHECK_IN_PROGRESS : External API call (mocked)
    note right of PROCESSING_BY_BANK : Random latency 100-3000ms
```
