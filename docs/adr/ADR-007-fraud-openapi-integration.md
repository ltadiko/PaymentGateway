# ADR-007: Fraud Assessment — OpenAPI Specification Integration

**Status:** Accepted  
**Date:** 2026-03-10  
**Deciders:** Senior Development Team

---

## Context

The requirement specifies that fraud assessment is performed by a **separate external microservice**, and the integration must use **OpenAPI Specification** to call it. The response can be mocked for this assignment.

This means:
1. We must define a formal API contract (OpenAPI 3.x spec) for the fraud assessment service.
2. We must generate or write an HTTP client based on that spec.
3. The actual fraud service is not implemented — we mock the response.

---

## Decision

### 1. OpenAPI Specification (Contract-First)

We define the fraud assessment API contract as an OpenAPI 3.1 YAML file:

**File:** `src/main/resources/openapi/fraud-assessment-api.yaml`

```yaml
openapi: 3.1.0
info:
  title: Fraud Assessment Service API
  version: 1.0.0
  description: External microservice for payment fraud detection

paths:
  /api/v1/fraud/assess:
    post:
      operationId: assessPayment
      summary: Assess a payment for fraud risk
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/FraudAssessmentRequest'
      responses:
        '200':
          description: Assessment completed
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/FraudAssessmentResponse'
        '500':
          description: Assessment service unavailable

components:
  schemas:
    FraudAssessmentRequest:
      type: object
      required: [paymentId, amount, currency, merchantId]
      properties:
        paymentId:
          type: string
          format: uuid
        amount:
          type: number
          format: decimal
        currency:
          type: string
          minLength: 3
          maxLength: 3
        merchantId:
          type: string
        paymentMethod:
          type: string
          enum: [CARD, BANK_TRANSFER, WALLET]

    FraudAssessmentResponse:
      type: object
      required: [paymentId, approved, fraudScore]
      properties:
        paymentId:
          type: string
          format: uuid
        approved:
          type: boolean
        fraudScore:
          type: integer
          minimum: 0
          maximum: 100
          description: 0 = no risk, 100 = definite fraud
        reason:
          type: string
          description: Human-readable reason (present when rejected)
        assessedAt:
          type: string
          format: date-time
```

### 2. Client Implementation — Spring `RestClient`

Rather than using a full OpenAPI code generator (which adds build complexity), we implement a **hand-written client** that conforms to the OpenAPI contract:

```java
@Component
public class OpenApiFraudClient implements FraudAssessmentPort {

    private final RestClient restClient;

    public OpenApiFraudClient(@Value("${fraud.api.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public FraudAssessmentResult assess(FraudAssessmentRequest request) {
        return restClient.post()
            .uri("/api/v1/fraud/assess")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(FraudAssessmentResult.class);
    }
}
```

### 3. Mock Implementation — For Development & Testing

Since the external fraud service does not exist, we provide a **mock implementation** that simulates realistic behavior:

```java
@Component
@Profile("mock-fraud")
public class MockFraudAssessmentAdapter implements FraudAssessmentPort {

    @Override
    public FraudAssessmentResult assess(FraudAssessmentRequest request) {
        // Simulate processing delay
        Thread.sleep(Duration.ofMillis(random(50, 200)));

        int fraudScore = calculateMockScore(request);
        boolean approved = fraudScore < 70;

        return new FraudAssessmentResult(
            request.paymentId(),
            approved,
            fraudScore,
            approved ? null : "High risk score: " + fraudScore,
            Instant.now()
        );
    }

    private int calculateMockScore(FraudAssessmentRequest request) {
        // Deterministic rules for testability:
        // - Amounts > 10,000 → high risk (score 85)
        // - Amounts > 5,000  → medium risk (score 55)
        // - Otherwise        → low risk (score 15)
        if (request.amount().compareTo(new BigDecimal("10000")) > 0) return 85;
        if (request.amount().compareTo(new BigDecimal("5000")) > 0) return 55;
        return 15;
    }
}
```

### 4. Strategy Pattern — Pluggable Fraud Assessment

The domain layer defines a port (interface):

```java
public interface FraudAssessmentPort {
    FraudAssessmentResult assess(FraudAssessmentRequest request);
}
```

Two implementations exist:
- `OpenApiFraudClient` — calls the real external service (active in `prod` profile).
- `MockFraudAssessmentAdapter` — simulates responses (active in `mock-fraud` / `dev` profile).

Spring profiles control which implementation is active. The domain layer is **completely unaware** of which adapter is being used.

### 5. Integration in Tests

- **Unit tests:** The `FraudAssessmentPort` is mocked directly — no HTTP calls.
- **Integration tests:** WireMock stubs the fraud API endpoint based on the OpenAPI contract, verifying that the client sends correctly-shaped requests and handles responses.

```java
// WireMock stub in integration test
wireMock.stubFor(post(urlEqualTo("/api/v1/fraud/assess"))
    .willReturn(okJson("""
        {
            "paymentId": "%s",
            "approved": true,
            "fraudScore": 15,
            "assessedAt": "2026-03-10T14:30:00Z"
        }
        """.formatted(paymentId))));
```

---

## Alternatives Considered

| Alternative | Why Rejected |
|---|---|
| **OpenAPI Generator (codegen)** | Generates client code from the YAML spec automatically. Powerful in production but adds Maven plugin complexity and generated code maintenance. A hand-written `RestClient` conforming to the spec is simpler for this scope. |
| **Feign Client** | Declarative HTTP client, good for simple cases. `RestClient` (Spring 6.1+) is the modern Spring-native alternative and doesn't require an additional dependency. |
| **WebClient (reactive)** | The application uses Spring MVC (servlet-based), not WebFlux. `RestClient` is the blocking counterpart and is more appropriate. |
| **Hardcoded mock (no OpenAPI spec)** | Doesn't demonstrate the "contract-first" integration approach required by the assignment. The YAML spec serves as documentation and a test contract. |

---

## Consequences

### Positive
- The OpenAPI spec serves as a **living contract** — both the mock and the real client conform to the same schema.
- Strategy pattern makes fraud assessment **swappable at deploy time** via profiles.
- WireMock integration tests verify **contract compliance** without a running fraud service.
- Mock implementation has **deterministic rules** — amount-based scoring makes tests predictable.

### Negative
- Hand-written client requires manual updates if the OpenAPI spec changes (codegen would automate this).
- Mock fraud rules are simplistic — production would use ML-based scoring.
- The YAML spec file must be kept in sync with the client DTOs manually.

