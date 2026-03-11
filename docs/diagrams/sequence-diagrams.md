# Sequence Diagrams

All diagrams use [Mermaid](https://mermaid.js.org/) syntax and can be rendered in GitHub, GitLab, IntelliJ, or VS Code with a Mermaid plugin.

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
    DB-->>-S: COMMIT OK

    S->>+K: Publish to "payment.submitted"<br/>key=paymentId
    K-->>-S: ACK

    S-->>-R: PaymentStatusResponse(paymentId, SUBMITTED)

    R-->>-F: 202 Accepted
    F-->>-C: HTTP 202<br/>{ "paymentId": "pay-uuid-001", "status": "SUBMITTED" }
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

    Note over C,DB: No new payment created.<br/>No Kafka event published.<br/>No duplicate charge.
```

---

## 3. Async Processing Pipeline — Happy Path

```mermaid
sequenceDiagram
    autonumber
    participant K1 as Kafka: payment.submitted
    participant FC as FraudAssessment Consumer
    participant FA as Fraud API (Mock)
    participant DB as Database
    participant K2 as Kafka: payment.fraud-assessed
    participant BC as BankProcessing Consumer
    participant BK as Bank Simulator
    participant K3 as Kafka: payment.completed

    K1->>+FC: Consume event (paymentId, tenantId)

    FC->>+DB: Update status -> FRAUD_CHECK_IN_PROGRESS
    FC->>DB: INSERT audit_trail
    DB-->>-FC: OK

    FC->>+FA: POST /api/fraud/assess
    FA-->>-FC: { approved: true, score: 15 }

    FC->>+DB: Update status -> FRAUD_APPROVED
    FC->>DB: INSERT audit_trail
    DB-->>-FC: OK

    FC->>+K2: Publish to payment.fraud-assessed
    K2-->>-FC: ACK
    deactivate FC

    K2->>+BC: Consume event (paymentId, tenantId)

    BC->>+DB: Update status -> PROCESSING_BY_BANK
    BC->>DB: INSERT audit_trail
    DB-->>-BC: OK

    BC->>+BK: Process payment (random 100-3000ms latency)
    BK-->>-BC: { success: true, bankRef: BNK-12345 }

    BC->>+DB: Update status -> COMPLETED
    BC->>DB: INSERT audit_trail
    DB-->>-BC: OK

    BC->>+K3: Publish to payment.completed
    K3-->>-BC: ACK
    deactivate BC
```

---

## 4. Async Processing — Fraud Rejection

```mermaid
sequenceDiagram
    autonumber
    participant K1 as Kafka: payment.submitted
    participant FC as FraudAssessment Consumer
    participant FA as Fraud API (Mock)
    participant DB as Database
    participant K3 as Kafka: payment.completed

    K1->>+FC: Consume event (paymentId, tenantId)

    FC->>+DB: Update status -> FRAUD_CHECK_IN_PROGRESS
    DB-->>-FC: OK

    FC->>+FA: POST /api/fraud/assess
    FA-->>-FC: { approved: false, reason: High risk }

    FC->>+DB: Update status -> FRAUD_REJECTED
    FC->>DB: INSERT audit_trail (FRAUD_REJECTED)
    DB-->>-FC: OK

    FC->>+K3: Publish to payment.completed (FAILED)
    K3-->>-FC: ACK
    deactivate FC

    Note over K1,K3: Pipeline stops. No bank processing.
```

---

## 5. Async Processing — Bank Failure

```mermaid
sequenceDiagram
    autonumber
    participant K2 as Kafka: payment.fraud-assessed
    participant BC as BankProcessing Consumer
    participant BK as Bank Simulator
    participant DB as Database
    participant K3 as Kafka: payment.completed

    K2->>+BC: Consume event (paymentId)

    BC->>+DB: Update status -> PROCESSING_BY_BANK
    DB-->>-BC: OK

    BC->>+BK: Process payment
    BK-->>-BC: { success: false, reason: Insufficient funds }

    BC->>+DB: Update status -> FAILED
    BC->>DB: INSERT audit_trail (FAILED)
    DB-->>-BC: OK

    BC->>+K3: Publish to payment.completed (FAILED)
    K3-->>-BC: ACK
    deactivate BC
```

---

## 6. Consumer Failure — Dead Letter Topic

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka: payment.submitted
    participant FC as FraudAssessment Consumer
    participant DB as Database
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
    FC->>DB: INSERT audit_trail
    DB-->>-FC: OK

    FC->>+DLT: Publish to payment.submitted.DLT
    DLT-->>-FC: ACK

    Note over K,DLT: Message preserved in DLT for manual replay
```

---

## 7. Status Inquiry — With Tenant Isolation

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (Tenant A)
    participant F as JwtAuthFilter
    participant R as PaymentController
    participant S as PaymentService
    participant DB as Database

    C->>+F: GET /api/v1/payments/pay-uuid-001<br/>Authorization: Bearer JWT (tenantId=tenant-A)

    F->>F: Validate JWT, tenantId = tenant-A
    F->>+R: Forward (authenticated)

    R->>R: @PreAuthorize("hasRole('PAYMENT_VIEW')")
    R->>+S: getPayment(paymentId, tenantId=tenant-A)

    S->>+DB: SELECT FROM payments<br/>WHERE id=? AND tenant_id=tenant-A
    DB-->>-S: Payment found

    S->>S: Mask sensitive fields
    S-->>-R: PaymentStatusResponse (masked)

    R-->>-F: 200 OK
    F-->>-C: { paymentId, status: COMPLETED, cardNumber: ****1234 }
```

---

## 8. Status Inquiry — Cross-Tenant Rejection

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (Tenant B)
    participant F as JwtAuthFilter
    participant R as PaymentController
    participant S as PaymentService
    participant DB as Database

    C->>+F: GET /api/v1/payments/pay-uuid-001<br/>Authorization: Bearer JWT (tenantId=tenant-B)

    F->>F: Validate JWT, tenantId = tenant-B
    F->>+R: Forward (authenticated)

    R->>+S: getPayment(paymentId, tenantId=tenant-B)

    S->>+DB: SELECT WHERE id=? AND tenant_id=tenant-B
    DB-->>-S: NOT FOUND (belongs to tenant-A)

    S-->>-R: throw PaymentNotFoundException

    R-->>-F: 404 Not Found
    F-->>-C: HTTP 404 { error: Payment not found }

    Note over C,DB: Returns 404 not 403 to prevent<br/>information leakage about valid IDs
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
    D --> F
    F --> J
    J --> L
    J --> E
    J -->|approved| G
    J -->|rejected| H
    J -->|error after retries| I
    G --> K
    K --> M
    K --> E
    K --> H
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
