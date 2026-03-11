# API Collections

Pre-built API collections for testing the Payment Gateway.

## Postman

**File:** [`postman/Payment-Gateway-API.postman_collection.json`](postman/Payment-Gateway-API.postman_collection.json)

### Import
1. Open Postman
2. Click **Import** → **Upload Files**
3. Select `Payment-Gateway-API.postman_collection.json`
4. The collection includes a `baseUrl` variable set to `http://localhost:8080`

### Usage
Run the requests in order:
1. **Auth → Get JWT Token** — saves `token` variable automatically
2. **Payments → Submit Payment** — saves `paymentId` automatically
3. **Payments → Get Payment Status** — wait 3-5s after submit
4. **Payments → Get Audit Trail** — see all state transitions

### Test Scripts
Every request includes Postman test scripts that:
- Verify HTTP status codes
- Auto-populate collection variables (`token`, `paymentId`)
- Validate response structure (masked accounts, audit trail format)

---

## Bruno

**Folder:** [`bruno/Payment-Gateway/`](bruno/Payment-Gateway/)

### Import
1. Open Bruno
2. Click **Open Collection**
3. Select the `bruno/Payment-Gateway` folder
4. Set environment to **Local** (`http://localhost:8080`)

### Collection Structure
```
Payment-Gateway/
├── environments/
│   └── Local.bru              → baseUrl = http://localhost:8080
├── Auth/
│   ├── Get JWT Token.bru      → Saves token to variable
│   ├── Get Token (View Only).bru → For 403 testing
│   └── Get Token (Tenant B).bru  → For tenant isolation testing
├── Payments/
│   ├── Submit Payment (Success).bru  → Amount 11.11 (bank success)
│   ├── Submit Payment (Random).bru   → Amount 250 (70% success)
│   ├── Get Payment Status.bru        → Query status (masked accounts)
│   └── Get Audit Trail.bru           → Immutable audit log
├── Fraud-Scenarios/
│   ├── Fraud Rejection (High Amount).bru     → Amount 15,000 (rejected)
│   ├── Bank Failure (Amount 99.99).bru       → Deterministic failure
│   └── Borderline Fraud (Medium Amount).bru  → Amount 7,500 (score 55)
└── Security-Tests/
    ├── No Token (401).bru          → Unauthenticated → 401
    ├── Wrong Role (403).bru        → View-only → 403 on POST
    └── Tenant Isolation (404).bru  → Cross-tenant → 404
```

---

## Test Scenarios Covered

| Scenario | Postman Request | Bruno Request | Expected |
|---|---|---|---|
| Happy path (bank success) | Submit Payment (Success) | Submit Payment (Success) | 202 → COMPLETED |
| Random outcome | Submit Payment (Random) | Submit Payment (Random) | 202 → COMPLETED or FAILED |
| Fraud rejection | Fraud Rejection (≥10,000) | Fraud Rejection (High Amount) | 202 → FRAUD_REJECTED |
| Bank failure | Bank Failure (99.99) | Bank Failure (Amount 99.99) | 202 → FAILED |
| Idempotency (first call) | Submit (First Call) | — | 202 |
| Idempotency (duplicate) | Submit (Duplicate) | — | 200, same paymentId |
| No auth token | No Token → 401 | No Token (401) | 401 |
| Wrong role | Wrong Role → 403 | Wrong Role (403) | 403 |
| Invalid token | Invalid Token → 401 | — | 401 |
| Tenant isolation | Tenant Isolation → 404 | Tenant Isolation (404) | 404 |

## Deterministic Test Hooks

| Amount | Fraud Score | Bank Result | Final Status |
|---|---|---|---|
| `11.11` | 15 (approved) | Always succeeds | `COMPLETED` |
| `99.99` | 15 (approved) | Always fails | `FAILED` |
| `≥ 10,000` | 85 (rejected) | Not called | `FRAUD_REJECTED` |
| `5,000 – 9,999` | 55 (approved) | 70% success | `COMPLETED` or `FAILED` |
| Other | 15 (approved) | 70% success | `COMPLETED` or `FAILED` |

