# Implementation Plan — Step-by-Step with Testable Milestones

Every step ends with a **"✅ Verify"** section: concrete commands to run, tests to execute, and cURL calls to prove the step works locally before moving on.

**Prerequisites:**
- Java 21+
- Maven 3.9+
- Docker Desktop (for Kafka via docker-compose & Testcontainers)

---

## Step 1: Project Setup & Dependencies (~20 min)

### 1.1 Update `pom.xml` — Add All Required Dependencies

Add the following to the existing `pom.xml`:

```xml
<!-- ── Data ── -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- ── Kafka ── -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<!-- ── Validation ── -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- ── JWT (JJWT) ── -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>

<!-- ── Test ── -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
</dependency>
```

### 1.2 Create `application.yml`

Replace `application.properties` with `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:paymentdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    database-platform: org.hibernate.dialect.H2Dialect
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: payment-gateway
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.fintech.gateway.*

app:
  security:
    jwt:
      secret: ThisIsA256BitSecretKeyForHMACSHA256!PaymentGateway2026
      expiration-ms: 3600000
  encryption:
    secret-key: AES256SecretKey!!
```

### 1.3 Create `docker-compose.yml` (for local Kafka)

```yaml
version: '3.8'
services:
  kafka:
    image: confluentinc/cp-kafka:7.6.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk
```

### 1.4 Create Package Structure

Create the following empty packages (with a `package-info.java` in each):

```
src/main/java/com/fintech/gateway/
├── domain/
│   ├── model/
│   ├── event/
│   ├── exception/
│   └── port/
│       ├── in/
│       └── out/
├── application/
│   ├── service/
│   └── dto/
└── infrastructure/
    ├── adapter/
    │   ├── in/
    │   │   ├── rest/
    │   │   └── kafka/
    │   └── out/
    │       ├── persistence/
    │       │   └── entity/
    │       ├── fraud/
    │       └── bank/
    ├── security/
    ├── crypto/
    ├── logging/
    └── config/
```

### ✅ Verify Step 1

```bash
# Must compile with no errors
./mvnw clean compile

# Start Kafka locally
docker-compose up -d

# Application starts (will fail on security config — expected, just verify dependencies resolve)
./mvnw spring-boot:run
# Expected: starts but may get 401 on all endpoints — that's fine
```

---

## Step 2: Domain Model — Pure Java, Zero Dependencies (~45 min)

This is the foundation. No Spring, no JPA, no Kafka — just pure Java 21.

### 2.1 `PaymentStatus.java` — Sealed Interface (State Machine)

**File:** `domain/model/PaymentStatus.java`

```java
public sealed interface PaymentStatus
    permits PaymentStatus.Submitted,
            PaymentStatus.FraudCheckInProgress,
            PaymentStatus.FraudApproved,
            PaymentStatus.FraudRejected,
            PaymentStatus.ProcessingByBank,
            PaymentStatus.Completed,
            PaymentStatus.Failed {

    record Submitted()                    implements PaymentStatus {}
    record FraudCheckInProgress()         implements PaymentStatus {}
    record FraudApproved()                implements PaymentStatus {}
    record FraudRejected(String reason)   implements PaymentStatus {}
    record ProcessingByBank()             implements PaymentStatus {}
    record Completed(String bankReference)implements PaymentStatus {}
    record Failed(String reason)          implements PaymentStatus {}

    // Convert to/from string for DB storage
    default String toDbValue() {
        return switch (this) {
            case Submitted s            -> "SUBMITTED";
            case FraudCheckInProgress f -> "FRAUD_CHECK_IN_PROGRESS";
            case FraudApproved a        -> "FRAUD_APPROVED";
            case FraudRejected r        -> "FRAUD_REJECTED";
            case ProcessingByBank p     -> "PROCESSING_BY_BANK";
            case Completed c            -> "COMPLETED";
            case Failed f               -> "FAILED";
        };
    }

    static PaymentStatus fromDbValue(String value, String detail) {
        return switch (value) {
            case "SUBMITTED"               -> new Submitted();
            case "FRAUD_CHECK_IN_PROGRESS" -> new FraudCheckInProgress();
            case "FRAUD_APPROVED"          -> new FraudApproved();
            case "FRAUD_REJECTED"          -> new FraudRejected(detail);
            case "PROCESSING_BY_BANK"      -> new ProcessingByBank();
            case "COMPLETED"               -> new Completed(detail);
            case "FAILED"                  -> new Failed(detail);
            default -> throw new IllegalArgumentException("Unknown status: " + value);
        };
    }
}
```

### 2.2 `PaymentStatusTransitionEngine.java` — State Transition Logic

**File:** `domain/model/PaymentStatusTransitionEngine.java`

```java
// Pure function: (currentStatus, triggeringEvent) → newStatus
// Uses nested pattern matching on sealed types
// Throws InvalidStateTransitionException for illegal transitions
// All terminal states (Completed, Failed, FraudRejected) reject any event
```

Events that trigger transitions:

```java
public sealed interface TransitionEvent {
    record StartFraudCheck()                                implements TransitionEvent {}
    record FraudCheckPassed(int score)                      implements TransitionEvent {}
    record FraudCheckFailed(String reason, int score)       implements TransitionEvent {}
    record SendToBank()                                     implements TransitionEvent {}
    record BankApproved(String bankReference)                implements TransitionEvent {}
    record BankRejected(String reason)                      implements TransitionEvent {}
}
```

Transition table:

| Current State | Event | → New State |
|---|---|---|
| `Submitted` | `StartFraudCheck` | `FraudCheckInProgress` |
| `FraudCheckInProgress` | `FraudCheckPassed` | `FraudApproved` |
| `FraudCheckInProgress` | `FraudCheckFailed` | `FraudRejected(reason)` |
| `FraudApproved` | `SendToBank` | `ProcessingByBank` |
| `ProcessingByBank` | `BankApproved` | `Completed(bankRef)` |
| `ProcessingByBank` | `BankRejected` | `Failed(reason)` |
| `FraudRejected` | *(any)* | `InvalidStateTransitionException` |
| `Completed` | *(any)* | `InvalidStateTransitionException` |
| `Failed` | *(any)* | `InvalidStateTransitionException` |

### 2.3 `Money.java` — Value Object

**File:** `domain/model/Money.java`

```java
// Record with compact constructor validation:
// - amount must not be null
// - amount must be > 0
// - currency must not be null (java.util.Currency)
```

### 2.4 `Payment.java` — Domain Entity (Aggregate Root)

**File:** `domain/model/Payment.java`

```java
// Fields: id (UUID), tenantId, amount (Money), creditorAccount, debtorAccount,
//         paymentMethod (String), status (PaymentStatus), statusDetail (String),
//         createdAt, updatedAt
//
// Static factory: Payment.initiate(tenantId, amount, creditorAccount, debtorAccount, paymentMethod)
//   → creates with status = new Submitted(), id = UUID.randomUUID(), createdAt = Instant.now()
//
// Method: transitionTo(TransitionEvent event)
//   → delegates to PaymentStatusTransitionEngine
//   → updates this.status, this.statusDetail, this.updatedAt
//   → returns the new status
```

### 2.5 `AuditEntry.java` — Value Object

**File:** `domain/model/AuditEntry.java`

```java
// Record: id (UUID), paymentId (UUID), tenantId, previousStatus (String),
//         newStatus (String), eventType (String), metadata (String/JSON),
//         performedBy (String), createdAt (Instant)
```

### 2.6 Domain Events — `PaymentEvent.java`

**File:** `domain/event/PaymentEvent.java`

```java
// Sealed interface with records:
// - PaymentSubmitted(eventId, paymentId, tenantId, timestamp)
// - FraudAssessmentCompleted(eventId, paymentId, tenantId, approved, fraudScore, reason, timestamp)
// - BankProcessingCompleted(eventId, paymentId, tenantId, success, bankReference, reason, timestamp)
//
// Each record carries only IDs — NO sensitive data (PCI compliance)
```

### 2.7 Domain Exceptions

**Files in:** `domain/exception/`

```java
// InvalidStateTransitionException(PaymentStatus current, TransitionEvent event)
// PaymentNotFoundException(UUID paymentId)
// DuplicatePaymentException(String idempotencyKey)
```

### ✅ Verify Step 2 — Unit Tests

**File:** `src/test/java/com/fintech/gateway/domain/model/PaymentStatusTransitionEngineTest.java`

```java
// Parameterized tests covering ALL rows of the transition table:
//
// @ParameterizedTest
// @MethodSource("validTransitions")
// void shouldTransitionToExpectedState(PaymentStatus current, TransitionEvent event, PaymentStatus expected)
//
// @ParameterizedTest
// @MethodSource("invalidTransitions")
// void shouldRejectInvalidTransition(PaymentStatus current, TransitionEvent event)
//
// validTransitions() returns:
//   (Submitted, StartFraudCheck) → FraudCheckInProgress
//   (FraudCheckInProgress, FraudCheckPassed(15)) → FraudApproved
//   (FraudCheckInProgress, FraudCheckFailed("High risk", 92)) → FraudRejected("High risk")
//   (FraudApproved, SendToBank) → ProcessingByBank
//   (ProcessingByBank, BankApproved("BNK-123")) → Completed("BNK-123")
//   (ProcessingByBank, BankRejected("Insufficient funds")) → Failed("Insufficient funds")
//
// invalidTransitions() returns:
//   (Submitted, BankApproved("x"))
//   (Submitted, FraudCheckPassed(10))
//   (FraudApproved, FraudCheckPassed(10))
//   (Completed("ref"), StartFraudCheck)
//   (Failed("reason"), StartFraudCheck)
//   (FraudRejected("reason"), SendToBank)
```

**File:** `src/test/java/com/fintech/gateway/domain/model/MoneyTest.java`

```java
// Test valid construction: new Money(BigDecimal.TEN, Currency.getInstance("USD"))
// Test null amount → NullPointerException or IllegalArgumentException
// Test zero amount → IllegalArgumentException
// Test negative amount → IllegalArgumentException
// Test null currency → NullPointerException or IllegalArgumentException
// Test equals/hashCode contract (records guarantee this)
```

**File:** `src/test/java/com/fintech/gateway/domain/model/PaymentTest.java`

```java
// Test Payment.initiate() sets status to Submitted, generates UUID, sets createdAt
// Test transitionTo() delegates correctly
// Test transitionTo() from terminal state throws
```

```bash
# Run only domain unit tests (no Spring context, < 1 second)
./mvnw test -Dtest="com.fintech.gateway.domain.**"
# Expected: ALL GREEN
```

---

## Step 3: Domain Ports — Interfaces (~15 min)

### 3.1 Inbound Ports (Use Cases)

**File:** `domain/port/in/SubmitPaymentUseCase.java`

```java
public interface SubmitPaymentUseCase {
    PaymentResponse submit(SubmitPaymentCommand command);
}
```

**File:** `domain/port/in/GetPaymentUseCase.java`

```java
public interface GetPaymentUseCase {
    PaymentResponse getPayment(UUID paymentId, String tenantId);
    List<AuditEntryResponse> getAuditTrail(UUID paymentId, String tenantId);
}
```

### 3.2 Outbound Ports (SPIs)

**File:** `domain/port/out/PaymentRepository.java`

```java
public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findByIdAndTenantId(UUID id, String tenantId);
}
```

**File:** `domain/port/out/IdempotencyStore.java`

```java
public interface IdempotencyStore {
    Optional<String> findResponse(String tenantId, String idempotencyKey);
    void store(String tenantId, String idempotencyKey, UUID paymentId, int httpStatus, String responseBody);
}
```

**File:** `domain/port/out/AuditTrailStore.java`

```java
public interface AuditTrailStore {
    void append(AuditEntry entry);
    List<AuditEntry> findByPaymentIdAndTenantId(UUID paymentId, String tenantId);
}
```

**File:** `domain/port/out/PaymentEventPublisher.java`

```java
public interface PaymentEventPublisher {
    void publish(PaymentEvent event);
}
```

**File:** `domain/port/out/FraudAssessmentPort.java`

```java
public interface FraudAssessmentPort {
    FraudAssessmentResult assess(FraudAssessmentRequest request);
}
// FraudAssessmentRequest: record(UUID paymentId, BigDecimal amount, String currency, String merchantId, String paymentMethod)
// FraudAssessmentResult: record(UUID paymentId, boolean approved, int fraudScore, String reason, Instant assessedAt)
```

**File:** `domain/port/out/BankGatewayPort.java`

```java
public interface BankGatewayPort {
    BankProcessingResult process(BankProcessingRequest request);
}
// BankProcessingRequest: record(UUID paymentId, BigDecimal amount, String currency, String creditorAccount)
// BankProcessingResult: record(UUID paymentId, boolean success, String bankReference, String reason)
```

### 3.3 Application DTOs

**File:** `application/dto/SubmitPaymentCommand.java`

```java
// Record: tenantId, idempotencyKey, amount (BigDecimal), currency (String),
//         creditorAccount, debtorAccount, paymentMethod
```

**File:** `application/dto/PaymentResponse.java`

```java
// Record: paymentId (UUID), status (String), maskedCreditorAccount, maskedDebtorAccount,
//         amount (BigDecimal), currency, createdAt, updatedAt
// NOTE: never exposes raw account numbers — always masked
```

### ✅ Verify Step 3

```bash
./mvnw clean compile
# Expected: compiles. All interfaces + DTOs are in place.
```

---

## Step 4: Security Infrastructure (~1 hr)

### 4.1 `JwtTokenProvider.java`

**File:** `infrastructure/security/JwtTokenProvider.java`

```java
// Methods:
//   String generateToken(String subject, String tenantId, List<String> roles)
//     → builds JWT with claims: sub, tenantId, roles, iat, exp
//     → signs with HMAC-SHA256 using secret from config
//     → returns compact JWT string
//
//   Claims validateAndExtract(String token)
//     → parses JWT, validates signature + expiry
//     → returns Claims object
//     → throws JwtException if invalid/expired
//
//   String getTenantId(Claims claims)
//   List<String> getRoles(Claims claims)
```

### 4.2 `TenantContext.java`

**File:** `infrastructure/security/TenantContext.java`

```java
// ThreadLocal<String> holder
// static void setTenantId(String tenantId)
// static String getTenantId()
// static void clear()  ← CRITICAL: must be called after every request
```

### 4.3 `JwtAuthenticationFilter.java`

**File:** `infrastructure/security/JwtAuthenticationFilter.java`

```java
// extends OncePerRequestFilter
//
// doFilterInternal():
//   1. Extract "Authorization: Bearer <token>" header
//   2. If missing → continue filter chain (Spring Security will reject if endpoint requires auth)
//   3. Call jwtTokenProvider.validateAndExtract(token)
//   4. Extract tenantId, roles from claims
//   5. Create UsernamePasswordAuthenticationToken with authorities
//   6. Set SecurityContextHolder.getContext().setAuthentication(auth)
//   7. Set TenantContext.setTenantId(tenantId)
//   8. try { filterChain.doFilter() } finally { TenantContext.clear() }
```

### 4.4 `SecurityConfig.java`

**File:** `infrastructure/security/SecurityConfig.java`

```java
// @Configuration
// @EnableMethodSecurity  ← enables @PreAuthorize
//
// SecurityFilterChain:
//   - csrf disabled (stateless API)
//   - sessionManagement → STATELESS
//   - authorizeHttpRequests:
//       POST /api/v1/auth/token → permitAll
//       GET /h2-console/** → permitAll
//       POST /api/v1/payments → authenticated (role check via @PreAuthorize)
//       GET /api/v1/payments/** → authenticated
//       any other → authenticated
//   - addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
//   - headers.frameOptions disabled (for H2 console)
```

### 4.5 `AuthController.java` — Mock Token Issuer

**File:** `infrastructure/adapter/in/rest/AuthController.java`

```java
// POST /api/v1/auth/token
// Request body: { "subject": "merchant-abc", "tenantId": "tenant-001", "roles": ["PAYMENT_SUBMIT", "PAYMENT_VIEW"] }
// Response: { "token": "eyJhbG...", "expiresIn": 3600 }
//
// This is a DEV-ONLY endpoint. In production, tokens come from an external IdP.
```

### 4.6 `EncryptionService.java` — AES-256 Encryption

**File:** `infrastructure/crypto/EncryptionService.java`

```java
// encrypt(String plainText) → String (Base64-encoded ciphertext)
// decrypt(String cipherText) → String (original plaintext)
// Uses AES/GCM/NoPadding with a 256-bit key derived from config
// Each encryption generates a random IV (prepended to ciphertext)
```

### 4.7 `DataMaskingUtil.java`

**File:** `infrastructure/crypto/DataMaskingUtil.java`

```java
// static String maskAccountNumber(String account)
//   → "****1234" (show last 4 digits)
// static String maskCardNumber(String card)
//   → "****-****-****-1234"
```

### ✅ Verify Step 4 — Unit Tests + Local Test

**File:** `src/test/java/com/fintech/gateway/infrastructure/security/JwtTokenProviderTest.java`

```java
// Test: generate token → validate → extract tenantId matches
// Test: generate token → validate → extract roles matches
// Test: expired token → throws exception
// Test: tampered token (modified payload) → throws exception
// Test: malformed string → throws exception
```

**File:** `src/test/java/com/fintech/gateway/infrastructure/crypto/EncryptionServiceTest.java`

```java
// Test: encrypt("4111111111111111") → decrypt → equals original
// Test: two encryptions of same plaintext produce different ciphertexts (random IV)
// Test: decrypt tampered ciphertext → throws exception
```

**File:** `src/test/java/com/fintech/gateway/infrastructure/crypto/DataMaskingUtilTest.java`

```java
// Test: maskAccountNumber("1234567890") → "****7890"
// Test: maskAccountNumber(null) → null
// Test: maskAccountNumber("12") → "**12" (short input)
```

**Local run test:**

```bash
./mvnw test -Dtest="com.fintech.gateway.infrastructure.security.**,com.fintech.gateway.infrastructure.crypto.**"
# Expected: ALL GREEN

# Start the app
docker-compose up -d
./mvnw spring-boot:run

# Test mock token endpoint
curl -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"subject":"merchant-abc","tenantId":"tenant-001","roles":["PAYMENT_SUBMIT","PAYMENT_VIEW"]}'
# Expected: 200 OK with { "token": "eyJ...", "expiresIn": 3600 }

# Test that unauthenticated request is rejected
curl -X GET http://localhost:8080/api/v1/payments/some-id
# Expected: 401 Unauthorized

# Test with valid token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"subject":"merchant-abc","tenantId":"tenant-001","roles":["PAYMENT_SUBMIT","PAYMENT_VIEW"]}' | jq -r '.token')

curl -X GET http://localhost:8080/api/v1/payments/some-id \
  -H "Authorization: Bearer $TOKEN"
# Expected: 404 (payment not found) — NOT 401. This proves auth works.
```

---

## Step 5: Persistence Layer (~45 min)

### 5.1 JPA Entities

**File:** `infrastructure/adapter/out/persistence/entity/PaymentJpaEntity.java`

```java
// @Entity @Table(name = "payments")
// Fields:
//   @Id UUID id
//   String tenantId (indexed)
//   BigDecimal amount
//   String currency
//   String creditorAccount   ← encrypted via @Convert(converter = AesAttributeConverter.class)
//   String debtorAccount     ← encrypted via @Convert(converter = AesAttributeConverter.class)
//   String paymentMethod
//   String status            ← String enum value (e.g., "SUBMITTED")
//   String statusDetail      ← reason for rejection/failure, bankRef for success
//   Instant createdAt
//   Instant updatedAt
```

**File:** `infrastructure/adapter/out/persistence/entity/IdempotencyKeyEntity.java`

```java
// @Entity @Table(name = "idempotency_keys",
//   uniqueConstraints = @UniqueConstraint(columns = {"tenant_id", "idempotency_key"}))
// Fields:
//   @Id UUID id
//   String tenantId
//   String idempotencyKey
//   UUID paymentId
//   int responseStatus
//   @Column(columnDefinition = "TEXT") String responseBody
//   Instant createdAt
```

**File:** `infrastructure/adapter/out/persistence/entity/AuditTrailEntity.java`

```java
// @Entity @Table(name = "audit_trail")
// Immutable: no setters. All fields set via constructor.
// Fields:
//   @Id UUID id
//   UUID paymentId (indexed)
//   String tenantId
//   String previousStatus  (nullable — null for initial creation)
//   String newStatus
//   String eventType
//   @Column(columnDefinition = "TEXT") String metadata  (JSON string)
//   String performedBy
//   Instant createdAt
```

### 5.2 AES Attribute Converter

**File:** `infrastructure/crypto/AesAttributeConverter.java`

```java
// @Converter
// implements AttributeConverter<String, String>
//
// convertToDatabaseColumn(String attribute) → encryptionService.encrypt(attribute)
// convertToEntityAttribute(String dbData) → encryptionService.decrypt(dbData)
//
// Handles null values gracefully (null → null)
```

### 5.3 Spring Data Repositories

**File:** `infrastructure/adapter/out/persistence/SpringDataPaymentRepository.java`

```java
// extends JpaRepository<PaymentJpaEntity, UUID>
// Optional<PaymentJpaEntity> findByIdAndTenantId(UUID id, String tenantId);
```

**File:** `infrastructure/adapter/out/persistence/SpringDataIdempotencyKeyRepository.java`

```java
// extends JpaRepository<IdempotencyKeyEntity, UUID>
// Optional<IdempotencyKeyEntity> findByTenantIdAndIdempotencyKey(String tenantId, String key);
// void deleteByCreatedAtBefore(Instant cutoff);  ← for key expiration cleanup
```

**File:** `infrastructure/adapter/out/persistence/SpringDataAuditTrailRepository.java`

```java
// extends JpaRepository<AuditTrailEntity, UUID>
// List<AuditTrailEntity> findByPaymentIdAndTenantIdOrderByCreatedAtAsc(UUID paymentId, String tenantId);
```

### 5.4 Port Adapter Implementations

**File:** `infrastructure/adapter/out/persistence/PaymentRepositoryAdapter.java`

```java
// @Component, implements PaymentRepository (domain port)
// Delegates to SpringDataPaymentRepository
// Maps PaymentJpaEntity ↔ Payment (domain model) using PaymentEntityMapper
```

**File:** `infrastructure/adapter/out/persistence/IdempotencyStoreAdapter.java`

```java
// @Component, implements IdempotencyStore (domain port)
// Delegates to SpringDataIdempotencyKeyRepository
```

**File:** `infrastructure/adapter/out/persistence/AuditTrailStoreAdapter.java`

```java
// @Component, implements AuditTrailStore (domain port)
// Delegates to SpringDataAuditTrailRepository
// Maps AuditTrailEntity ↔ AuditEntry (domain model)
```

### 5.5 Entity ↔ Domain Mapper

**File:** `infrastructure/adapter/out/persistence/PaymentEntityMapper.java`

```java
// static PaymentJpaEntity toEntity(Payment domain)
//   → maps all fields, converts PaymentStatus sealed type to String via toDbValue()
//
// static Payment toDomain(PaymentJpaEntity entity)
//   → maps all fields, converts String status back to sealed type via fromDbValue()
```

### ✅ Verify Step 5 — Integration Tests

**File:** `src/test/java/com/fintech/gateway/infrastructure/adapter/out/persistence/PaymentRepositoryAdapterTest.java`

```java
// @DataJpaTest (lightweight — only JPA slice, H2)
//
// Test: save a Payment → findByIdAndTenantId → fields match (including decrypted accounts)
// Test: findByIdAndTenantId with wrong tenantId → Optional.empty() (tenant isolation)
// Test: verify creditorAccount is encrypted in DB (query raw table via JDBC)
```

**File:** `src/test/java/com/fintech/gateway/infrastructure/adapter/out/persistence/IdempotencyStoreAdapterTest.java`

```java
// @DataJpaTest
//
// Test: store a key → find it → response matches
// Test: store duplicate key → DataIntegrityViolationException
// Test: find with wrong tenantId → Optional.empty()
```

**File:** `src/test/java/com/fintech/gateway/infrastructure/adapter/out/persistence/AuditTrailStoreAdapterTest.java`

```java
// @DataJpaTest
//
// Test: append 3 audit entries → findByPaymentId → returns 3, ordered by createdAt ASC
// Test: find with wrong tenantId → empty list
```

```bash
./mvnw test -Dtest="com.fintech.gateway.infrastructure.adapter.out.persistence.**"
# Expected: ALL GREEN

# Verify encryption works — start app, check H2 console
./mvnw spring-boot:run
# Open http://localhost:8080/h2-console
# Connect to jdbc:h2:mem:paymentdb
# SELECT * FROM payments → creditor_account column shows encrypted Base64 strings, not plaintext
```

---

## Step 6: Application Services — Business Logic Orchestration (~1 hr)

### 6.1 `PaymentIngestionService.java`

**File:** `application/service/PaymentIngestionService.java`

```java
// @Service, @Transactional
// Implements SubmitPaymentUseCase
//
// submit(SubmitPaymentCommand command):
//   1. Check idempotencyStore.findResponse(tenantId, idempotencyKey)
//      → if found: return deserialized original response (HTTP 200 semantics)
//
//   2. Validate command (amount > 0, currency valid, accounts not blank)
//
//   3. Create domain object: Payment.initiate(...)
//
//   4. In single @Transactional:
//      a. paymentRepository.save(payment)
//      b. auditTrailStore.append(AuditEntry for SUBMITTED)
//      c. idempotencyStore.store(tenantId, key, paymentId, 202, serializedResponse)
//
//   5. After commit: eventPublisher.publish(new PaymentSubmitted(...))
//      NOTE: publish AFTER transaction commits — if publish fails, payment is still saved
//            and can be retried. This avoids the "published but not saved" problem.
//
//   6. Return PaymentResponse
//
// Race condition handling:
//   Wrap step 4 in try/catch for DataIntegrityViolationException
//   → catch: re-query idempotencyStore → return original response
```

### 6.2 `PaymentQueryService.java`

**File:** `application/service/PaymentQueryService.java`

```java
// @Service, @Transactional(readOnly = true)
// Implements GetPaymentUseCase
//
// getPayment(UUID paymentId, String tenantId):
//   1. paymentRepository.findByIdAndTenantId(paymentId, tenantId)
//   2. If empty → throw PaymentNotFoundException
//   3. Map to PaymentResponse with MASKED account numbers
//   4. Return
//
// getAuditTrail(UUID paymentId, String tenantId):
//   1. Verify payment exists and belongs to tenant (same query)
//   2. auditTrailStore.findByPaymentIdAndTenantId(...)
//   3. Return list of AuditEntryResponse records
```

### 6.3 `FraudProcessingService.java`

**File:** `application/service/FraudProcessingService.java`

```java
// @Service
// Called by Kafka consumer — NOT a use case port (it's an internal pipeline step)
//
// processFraudCheck(UUID paymentId, String tenantId):
//   1. Load payment from DB (by id + tenantId)
//   2. Transition: payment.transitionTo(new StartFraudCheck())
//   3. Save payment + audit entry (FRAUD_CHECK_IN_PROGRESS) in @Transactional
//
//   4. Call fraudAssessmentPort.assess(request)
//      → Build FraudAssessmentRequest from payment fields
//
//   5. If approved:
//      a. Transition: payment.transitionTo(new FraudCheckPassed(score))
//      b. Save payment + audit entry (FRAUD_APPROVED) in @Transactional
//      c. Publish PaymentEvent.FraudAssessmentCompleted(approved=true)
//         → This goes to "payment.fraud-assessed" topic
//
//   6. If rejected:
//      a. Transition: payment.transitionTo(new FraudCheckFailed(reason, score))
//      b. Save payment + audit entry (FRAUD_REJECTED) in @Transactional
//      c. Publish PaymentEvent.FraudAssessmentCompleted(approved=false)
//         → This goes to "payment.completed" topic (terminal state, no bank processing)
```

### 6.4 `BankProcessingService.java`

**File:** `application/service/BankProcessingService.java`

```java
// @Service
// Called by Kafka consumer
//
// processBankPayment(UUID paymentId, String tenantId):
//   1. Load payment from DB
//   2. Transition: payment.transitionTo(new SendToBank())
//   3. Save payment + audit entry (PROCESSING_BY_BANK) in @Transactional
//
//   4. Call bankGatewayPort.process(request)
//      → Variable latency (100-3000ms), random success/failure
//
//   5. If success:
//      a. Transition: payment.transitionTo(new BankApproved(bankRef))
//      b. Save payment + audit entry (COMPLETED) in @Transactional
//      c. Publish PaymentEvent.BankProcessingCompleted(success=true)
//
//   6. If failure:
//      a. Transition: payment.transitionTo(new BankRejected(reason))
//      b. Save payment + audit entry (FAILED) in @Transactional
//      c. Publish PaymentEvent.BankProcessingCompleted(success=false)
```

### ✅ Verify Step 6 — Unit Tests (Mocked Ports)

**File:** `src/test/java/com/fintech/gateway/application/service/PaymentIngestionServiceTest.java`

```java
// All outbound ports mocked with Mockito
//
// Test: submitPayment with new idempotencyKey
//   → verify paymentRepository.save() called
//   → verify auditTrailStore.append() called with status "SUBMITTED"
//   → verify idempotencyStore.store() called
//   → verify eventPublisher.publish() called with PaymentSubmitted
//   → response has paymentId and status "SUBMITTED"
//
// Test: submitPayment with existing idempotencyKey
//   → mock idempotencyStore.findResponse() returns existing response
//   → verify paymentRepository.save() NOT called
//   → verify eventPublisher.publish() NOT called
//   → response matches original stored response
//
// Test: submitPayment race condition (DataIntegrityViolationException)
//   → mock idempotencyStore.store() throws DataIntegrityViolationException
//   → mock idempotencyStore.findResponse() returns existing (on second call)
//   → returns original response, no exception propagated
```

**File:** `src/test/java/com/fintech/gateway/application/service/PaymentQueryServiceTest.java`

```java
// Test: getPayment found → returns masked response
// Test: getPayment not found → throws PaymentNotFoundException
// Test: getPayment wrong tenant → throws PaymentNotFoundException (tenant isolation)
```

**File:** `src/test/java/com/fintech/gateway/application/service/FraudProcessingServiceTest.java`

```java
// Test: fraud approved → payment status becomes FRAUD_APPROVED → event published to fraud-assessed topic
// Test: fraud rejected → payment status becomes FRAUD_REJECTED → event published to completed topic
// Test: fraud API throws exception → exception propagates (Kafka retry will handle)
```

**File:** `src/test/java/com/fintech/gateway/application/service/BankProcessingServiceTest.java`

```java
// Test: bank approved → payment status becomes COMPLETED → event published
// Test: bank rejected → payment status becomes FAILED → event published
// Test: bank throws exception → exception propagates
```

```bash
./mvnw test -Dtest="com.fintech.gateway.application.service.**"
# Expected: ALL GREEN, runs in < 2 seconds (all mocked, no Spring context)
```

---

## Step 7: REST API Layer (~30 min)

### 7.1 `PaymentController.java`

**File:** `infrastructure/adapter/in/rest/PaymentController.java`

```java
// @RestController @RequestMapping("/api/v1/payments")
//
// @PostMapping
// @PreAuthorize("hasRole('PAYMENT_SUBMIT')")
// ResponseEntity<PaymentResponse> submitPayment(
//     @Valid @RequestBody SubmitPaymentRequest request,
//     @RequestHeader("Idempotency-Key") String idempotencyKey)
//
//   → Extract tenantId from TenantContext.getTenantId()
//   → Build SubmitPaymentCommand from request + tenantId + idempotencyKey
//   → Call submitPaymentUseCase.submit(command)
//   → If new payment: return 202 Accepted
//   → If idempotent duplicate: return 200 OK
//
// @GetMapping("/{paymentId}")
// @PreAuthorize("hasRole('PAYMENT_VIEW')")
// ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId)
//
//   → Extract tenantId from TenantContext
//   → Call getPaymentUseCase.getPayment(paymentId, tenantId)
//   → Return 200 OK
//
// @GetMapping("/{paymentId}/audit")
// @PreAuthorize("hasRole('PAYMENT_VIEW')")
// ResponseEntity<List<AuditEntryResponse>> getAuditTrail(@PathVariable UUID paymentId)
//
//   → Extract tenantId from TenantContext
//   → Call getPaymentUseCase.getAuditTrail(paymentId, tenantId)
//   → Return 200 OK
```

### 7.2 Request/Response DTOs

**File:** `infrastructure/adapter/in/rest/dto/SubmitPaymentRequest.java`

```java
// Record with validation:
//   @NotNull BigDecimal amount
//   @NotBlank @Size(min=3, max=3) String currency
//   @NotBlank String creditorAccount
//   @NotBlank String debtorAccount
//   @NotBlank String paymentMethod  (e.g., "CARD", "BANK_TRANSFER")
```

**File:** `infrastructure/adapter/in/rest/dto/TokenRequest.java`

```java
// Record: String subject, String tenantId, List<String> roles
```

**File:** `infrastructure/adapter/in/rest/dto/TokenResponse.java`

```java
// Record: String token, long expiresIn
```

### 7.3 `GlobalExceptionHandler.java`

**File:** `infrastructure/adapter/in/rest/GlobalExceptionHandler.java`

```java
// @RestControllerAdvice
//
// PaymentNotFoundException → 404 { "error": "Payment not found" }
// DuplicatePaymentException → 409 { "error": "..." }
// InvalidStateTransitionException → 409 { "error": "..." }
// MethodArgumentNotValidException → 400 { "error": "Validation failed", "details": [...] }
// MissingRequestHeaderException (Idempotency-Key) → 400 { "error": "Idempotency-Key header is required" }
// JwtException → 401 { "error": "Invalid or expired token" }
// AccessDeniedException → 403 { "error": "Insufficient permissions" }
```

### ✅ Verify Step 7 — Integration Tests + Local cURL

**File:** `src/test/java/com/fintech/gateway/infrastructure/adapter/in/rest/PaymentControllerTest.java`

```java
// @WebMvcTest(PaymentController.class) — lightweight, mocked service layer
//
// Test: POST /payments without Authorization → 401
// Test: POST /payments with valid JWT but missing PAYMENT_SUBMIT role → 403
// Test: POST /payments with valid JWT + role + valid body + Idempotency-Key → 202
// Test: POST /payments without Idempotency-Key header → 400
// Test: POST /payments with invalid body (missing amount) → 400 with validation errors
// Test: GET /payments/{id} with valid JWT + role → 200 (mock service returns payment)
// Test: GET /payments/{id} not found → 404
// Test: GET /payments/{id}/audit → 200 with audit entries
```

**Local cURL testing:**

```bash
# Start Kafka + app
docker-compose up -d
./mvnw spring-boot:run

# 1. Get a token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"subject":"merchant-abc","tenantId":"tenant-001","roles":["PAYMENT_SUBMIT","PAYMENT_VIEW"]}' \
  | jq -r '.token')

# 2. Submit a payment
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "amount": 150.00,
    "currency": "USD",
    "creditorAccount": "NL91ABNA0417164300",
    "debtorAccount": "DE89370400440532013000",
    "paymentMethod": "BANK_TRANSFER"
  }'
# Expected: 202 Accepted with { "paymentId": "uuid", "status": "SUBMITTED" }

# 3. Query payment status
PAYMENT_ID="<paymentId from step 2>"
curl -X GET "http://localhost:8080/api/v1/payments/$PAYMENT_ID" \
  -H "Authorization: Bearer $TOKEN"
# Expected: 200 OK with masked account numbers

# 4. Test idempotency — resubmit with same key
IDEM_KEY=$(uuidgen)
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -d '{"amount":150,"currency":"USD","creditorAccount":"NL91ABNA0417164300","debtorAccount":"DE89370400440532013000","paymentMethod":"BANK_TRANSFER"}'
# First call: 202 Accepted

curl -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -d '{"amount":150,"currency":"USD","creditorAccount":"NL91ABNA0417164300","debtorAccount":"DE89370400440532013000","paymentMethod":"BANK_TRANSFER"}'
# Second call: 200 OK (same paymentId, no new payment)

# 5. Test tenant isolation
TOKEN_B=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"subject":"merchant-xyz","tenantId":"tenant-002","roles":["PAYMENT_SUBMIT","PAYMENT_VIEW"]}' \
  | jq -r '.token')

curl -X GET "http://localhost:8080/api/v1/payments/$PAYMENT_ID" \
  -H "Authorization: Bearer $TOKEN_B"
# Expected: 404 Not Found (tenant-002 cannot see tenant-001's payment)

# 6. Test missing auth
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{}'
# Expected: 401 Unauthorized
```

---

## Step 8: Kafka Infrastructure — Event Publishing & Consuming (~45 min)

### 8.1 Kafka Configuration

**File:** `infrastructure/config/KafkaTopicConfig.java`

```java
// @Configuration
// Creates topics on startup:
//   NewTopic("payment.submitted", 3 partitions, 1 replica)
//   NewTopic("payment.fraud-assessed", 3 partitions, 1 replica)
//   NewTopic("payment.completed", 3 partitions, 1 replica)
```

**File:** `infrastructure/config/KafkaConsumerConfig.java`

```java
// @Configuration
// Configures:
//   - DefaultErrorHandler with FixedBackOff(1000ms, 3 attempts)
//   - DeadLetterPublishingRecoverer → sends to <topic>.DLT after retries exhausted
//   - JsonDeserializer with trusted packages
//   - Manual ACK mode (AckMode.RECORD)
```

### 8.2 Event Publisher Adapter

**File:** `infrastructure/adapter/out/kafka/KafkaPaymentEventPublisher.java`

```java
// @Component, implements PaymentEventPublisher (domain port)
//
// publish(PaymentEvent event):
//   Route based on sealed type using pattern matching:
//     case PaymentSubmitted s         → send to "payment.submitted" topic
//     case FraudAssessmentCompleted f → if approved: "payment.fraud-assessed"
//                                       if rejected: "payment.completed"
//     case BankProcessingCompleted b  → send to "payment.completed" topic
//
//   Message key: event.paymentId().toString() (partition ordering)
//
//   Uses kafkaTemplate.send(topic, key, event)
```

### 8.3 Kafka Consumers

**File:** `infrastructure/adapter/in/kafka/FraudAssessmentConsumer.java`

```java
// @Component
// @KafkaListener(
//   topics = "payment.submitted",
//   groupId = "fraud-assessment-group",
//   containerFactory = "kafkaListenerContainerFactory"
// )
//
// consume(PaymentSubmitted event):
//   → log event received
//   → call fraudProcessingService.processFraudCheck(event.paymentId(), event.tenantId())
//   → if exception: let it propagate (Kafka retry + DLT will handle)
```

**File:** `infrastructure/adapter/in/kafka/BankProcessingConsumer.java`

```java
// @Component
// @KafkaListener(
//   topics = "payment.fraud-assessed",
//   groupId = "bank-processing-group",
//   containerFactory = "kafkaListenerContainerFactory"
// )
//
// consume(FraudAssessmentCompleted event):
//   → call bankProcessingService.processBankPayment(event.paymentId(), event.tenantId())
```

### ✅ Verify Step 8 — Integration Test with Testcontainers

**File:** `src/test/java/com/fintech/gateway/infrastructure/adapter/in/kafka/KafkaEventPublishingTest.java`

```java
// @SpringBootTest
// @Testcontainers
// @EmbeddedKafka or KafkaContainer via Testcontainers
//
// Test: publish PaymentSubmitted event → consumer receives it and calls FraudProcessingService
//   → Use @SpyBean on FraudProcessingService
//   → Awaitility.await().atMost(10, SECONDS).untilAsserted(() ->
//       verify(fraudProcessingService).processFraudCheck(paymentId, tenantId))
//
// Test: publish FraudAssessmentCompleted → BankProcessingConsumer receives it
```

**Local cURL testing (full async pipeline):**

```bash
docker-compose up -d
./mvnw spring-boot:run

# Submit payment
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"subject":"m","tenantId":"t1","roles":["PAYMENT_SUBMIT","PAYMENT_VIEW"]}' | jq -r '.token')

PAYMENT_ID=$(curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount":100,"currency":"USD","creditorAccount":"NL91ABNA0417164300","debtorAccount":"DE89370400440532013000","paymentMethod":"BANK_TRANSFER"}' \
  | jq -r '.paymentId')

# Wait 5 seconds for async pipeline to complete
sleep 5

# Check status — should be COMPLETED or FAILED (bank is random)
curl -s "http://localhost:8080/api/v1/payments/$PAYMENT_ID" \
  -H "Authorization: Bearer $TOKEN" | jq
# Expected: status is one of COMPLETED, FAILED, FRAUD_REJECTED

# Check audit trail — should show full lifecycle
curl -s "http://localhost:8080/api/v1/payments/$PAYMENT_ID/audit" \
  -H "Authorization: Bearer $TOKEN" | jq
# Expected: array of audit entries showing each state transition in order
```

---

## Step 9: External Service Adapters — Fraud API & Bank Simulator (~30 min)

### 9.1 OpenAPI Spec

**File:** `src/main/resources/openapi/fraud-assessment-api.yaml`

```yaml
# Full OpenAPI 3.1 spec as defined in ADR-007
# Serves as living documentation of the fraud service contract
```

### 9.2 Fraud API Client (OpenAPI-based)

**File:** `infrastructure/adapter/out/fraud/OpenApiFraudClient.java`

```java
// @Component @Profile("!mock-fraud")
// Implements FraudAssessmentPort
//
// Uses Spring RestClient to call the external fraud service per OpenAPI spec
// Base URL from config: fraud.api.base-url
//
// assess(request):
//   POST {base-url}/api/v1/fraud/assess
//   Content-Type: application/json
//   Body: FraudAssessmentRequest
//   Returns: FraudAssessmentResult
```

### 9.3 Mock Fraud Controller (In-App Mock)

**File:** `infrastructure/adapter/out/fraud/MockFraudController.java`

```java
// @RestController @Profile("mock-fraud")
// @RequestMapping("/api/v1/fraud")
//
// This runs INSIDE the same app, simulating the external fraud microservice.
// The OpenApiFraudClient calls this endpoint via HTTP — exercising the full OpenAPI contract.
//
// POST /api/v1/fraud/assess:
//   → Simulate 50-200ms processing delay
//   → Score rules (deterministic for testability):
//       amount > 10,000 → score 85 (rejected, reason: "High risk transaction amount")
//       amount > 5,000  → score 55 (approved, borderline)
//       otherwise       → score 15 (approved, low risk)
//   → Return FraudAssessmentResponse per OpenAPI schema
```

Configuration: `fraud.api.base-url=http://localhost:${server.port}` (calls itself in mock mode)

### 9.4 Bank Gateway Simulator

**File:** `infrastructure/adapter/out/bank/SimulatedBankGateway.java`

```java
// @Component, implements BankGatewayPort
//
// process(BankProcessingRequest request):
//   1. Simulate variable latency: Thread.sleep(random 100-3000ms)
//   2. Random outcome (70% success, 30% failure):
//      → Success: BankProcessingResult(paymentId, true, "BNK-" + randomAlphanumeric(8), null)
//      → Failure: BankProcessingResult(paymentId, false, null, randomReason())
//         Reasons: "Insufficient funds", "Account closed", "Bank system timeout", "Currency not supported"
//   3. For testability: if amount == 99.99 → always fail (deterministic test hook)
//      if amount == 11.11 → always succeed
```

### ✅ Verify Step 9 — Unit Tests + E2E Local Test

**File:** `src/test/java/com/fintech/gateway/infrastructure/adapter/out/fraud/MockFraudControllerTest.java`

```java
// @WebMvcTest(MockFraudController.class)
//
// Test: amount 100 → approved, score 15
// Test: amount 6000 → approved, score 55
// Test: amount 15000 → rejected, score 85
```

**File:** `src/test/java/com/fintech/gateway/infrastructure/adapter/out/bank/SimulatedBankGatewayTest.java`

```java
// Unit test (no Spring context):
// Test: amount 99.99 → always failure
// Test: amount 11.11 → always success
// Test: random amount → success or failure (run 100 times, verify both outcomes occur)
// Test: latency is between 100-3000ms
```

**Full E2E local test:**

```bash
docker-compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=mock-fraud

TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"subject":"m","tenantId":"t1","roles":["PAYMENT_SUBMIT","PAYMENT_VIEW"]}' | jq -r '.token')

# Test 1: Low amount → fraud approved → bank processing
PAYMENT_ID=$(curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount":11.11,"currency":"USD","creditorAccount":"NL91ABNA0417164300","debtorAccount":"DE89370400440532013000","paymentMethod":"CARD"}' \
  | jq -r '.paymentId')
sleep 5
curl -s "http://localhost:8080/api/v1/payments/$PAYMENT_ID" -H "Authorization: Bearer $TOKEN" | jq '.status'
# Expected: "COMPLETED" (deterministic: amount 11.11 → bank always succeeds)

# Test 2: High amount → fraud rejected → no bank call
PAYMENT_ID=$(curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount":15000,"currency":"USD","creditorAccount":"NL91ABNA0417164300","debtorAccount":"DE89370400440532013000","paymentMethod":"CARD"}' \
  | jq -r '.paymentId')
sleep 5
curl -s "http://localhost:8080/api/v1/payments/$PAYMENT_ID" -H "Authorization: Bearer $TOKEN" | jq '.status'
# Expected: "FRAUD_REJECTED"

# Test 3: Bank failure amount
PAYMENT_ID=$(curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount":99.99,"currency":"USD","creditorAccount":"NL91ABNA0417164300","debtorAccount":"DE89370400440532013000","paymentMethod":"CARD"}' \
  | jq -r '.paymentId')
sleep 5
curl -s "http://localhost:8080/api/v1/payments/$PAYMENT_ID" -H "Authorization: Bearer $TOKEN" | jq '.status'
# Expected: "FAILED"

# Test 4: Full audit trail
curl -s "http://localhost:8080/api/v1/payments/$PAYMENT_ID/audit" \
  -H "Authorization: Bearer $TOKEN" | jq '.[].newStatus'
# Expected (for test 1): ["SUBMITTED", "FRAUD_CHECK_IN_PROGRESS", "FRAUD_APPROVED", "PROCESSING_BY_BANK", "COMPLETED"]
```

---

## Step 10: Log Masking (~15 min)

### 10.1 `MaskingPatternLayout.java`

**File:** `infrastructure/logging/MaskingPatternLayout.java`

```java
// extends ch.qos.logback.classic.PatternLayout
//
// Overrides doLayout(ILoggingEvent event):
//   → Apply regex patterns to mask PII in log output:
//     - Card numbers: \b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b → "****-****-****-XXXX"
//     - Account numbers (IBAN): \b[A-Z]{2}\d{2}[A-Z0-9]{4,30}\b → "****XXXX" (last 4)
//     - JSON "accountNumber":"..." → masked
```

### 10.2 `logback-spring.xml`

**File:** `src/main/resources/logback-spring.xml`

```xml
<!-- Configure console appender to use MaskingPatternLayout -->
<!-- Ensures that even if a developer accidentally logs a card number, it gets masked -->
```

### ✅ Verify Step 10

```java
// Unit test:
// MaskingPatternLayout layout = new MaskingPatternLayout();
// String masked = layout.mask("Card: 4111-1111-1111-1111 Account: NL91ABNA0417164300");
// assertThat(masked).contains("****-****-****-1111");
// assertThat(masked).contains("****4300");
// assertThat(masked).doesNotContain("4111-1111-1111-1111");
// assertThat(masked).doesNotContain("NL91ABNA0417164300");
```

---

## Step 11: Full Pipeline Integration Tests — Testcontainers (~45 min)

### 11.1 Test Infrastructure Base Class

**File:** `src/test/java/com/fintech/gateway/IntegrationTestBase.java`

```java
// @SpringBootTest(webEnvironment = RANDOM_PORT)
// @Testcontainers
// @ActiveProfiles("test,mock-fraud")
//
// @Container
// static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
//
// @DynamicPropertySource
// static void overrideProperties(DynamicPropertyRegistry registry) {
//     registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
// }
//
// @Autowired TestRestTemplate restTemplate;
//
// Helper: String getToken(String tenantId, List<String> roles) → calls /auth/token
// Helper: HttpHeaders authHeaders(String token) → sets Authorization + Content-Type
```

### 11.2 Full Pipeline Test

**File:** `src/test/java/com/fintech/gateway/FullPipelineIntegrationTest.java`

```java
// extends IntegrationTestBase
//
// @Test: happyPath_paymentCompletesSuccessfully
//   1. Get token for tenant-A
//   2. POST /payments (amount=11.11 → deterministic bank success)
//   3. Assert 202 Accepted
//   4. Awaitility.await().atMost(30, SECONDS).untilAsserted(() -> {
//        GET /payments/{id} → status == "COMPLETED"
//      })
//   5. GET /payments/{id}/audit → verify 5 entries in order:
//      SUBMITTED → FRAUD_CHECK_IN_PROGRESS → FRAUD_APPROVED → PROCESSING_BY_BANK → COMPLETED
//
// @Test: fraudRejection_pipelineStopsAfterFraud
//   1. POST /payments (amount=15000 → fraud rejection)
//   2. Await status == "FRAUD_REJECTED"
//   3. Audit trail: SUBMITTED → FRAUD_CHECK_IN_PROGRESS → FRAUD_REJECTED
//
// @Test: bankFailure_paymentFails
//   1. POST /payments (amount=99.99 → deterministic bank failure)
//   2. Await status == "FAILED"
//   3. Audit trail: SUBMITTED → ... → PROCESSING_BY_BANK → FAILED
```

### 11.3 Idempotency Integration Test

**File:** `src/test/java/com/fintech/gateway/IdempotencyIntegrationTest.java`

```java
// extends IntegrationTestBase
//
// @Test: duplicateRequest_returnsSamePaymentId
//   1. Generate idempotencyKey
//   2. POST /payments → 202, get paymentId-1
//   3. POST /payments with SAME idempotencyKey → 200, get paymentId-2
//   4. Assert paymentId-1 == paymentId-2
//   5. Query DB: only ONE payment record exists
//
// @Test: differentKey_createsDifferentPayment
//   1. POST /payments with key-1 → paymentId-1
//   2. POST /payments with key-2 → paymentId-2
//   3. Assert paymentId-1 != paymentId-2
```

### 11.4 Tenant Isolation Integration Test

**File:** `src/test/java/com/fintech/gateway/TenantIsolationIntegrationTest.java`

```java
// extends IntegrationTestBase
//
// @Test: tenantA_cannotSeeTenantB_payment
//   1. Token for tenant-A → POST /payments → get paymentId
//   2. Token for tenant-B → GET /payments/{paymentId} → 404
//
// @Test: tenantA_canSeeOwnPayment
//   1. Token for tenant-A → POST /payments → get paymentId
//   2. Token for tenant-A → GET /payments/{paymentId} → 200
```

### 11.5 Security Integration Test

**File:** `src/test/java/com/fintech/gateway/SecurityIntegrationTest.java`

```java
// extends IntegrationTestBase
//
// @Test: noToken_returns401
// @Test: expiredToken_returns401
// @Test: validToken_wrongRole_returns403
//   Token with only PAYMENT_VIEW → POST /payments → 403
// @Test: validToken_correctRole_returns2xx
```

### ✅ Verify Step 11

```bash
# Run all integration tests (requires Docker for Testcontainers)
./mvnw verify -Dtest="com.fintech.gateway.*IntegrationTest"
# Expected: ALL GREEN

# Or run everything
./mvnw verify
# Expected: ALL GREEN (unit + integration)
```

---

## Step 12: README & Final Polish (~30 min)

### 12.1 Create `README.md` at project root

Include:
1. **Architecture overview** — link to `docs/READING-GUIDE.md`
2. **Tech stack** — Java 21, Spring Boot 4.0.3, Kafka, H2, JJWT
3. **Prerequisites** — Java 21+, Maven 3.9+, Docker Desktop
4. **Build & Test**:
   ```bash
   ./mvnw clean verify
   ```
5. **Run locally**:
   ```bash
   docker-compose up -d
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=mock-fraud
   ```
6. **API Usage** — all cURL commands from Step 7 and Step 9
7. **H2 Console** — http://localhost:8080/h2-console (jdbc:h2:mem:paymentdb)
8. **ADR Summary** — link to `docs/README.md`

### 12.2 Final Verification Checklist

```bash
# 1. Clean build + all tests pass
./mvnw clean verify
# Expected: BUILD SUCCESS

# 2. Start locally
docker-compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=mock-fraud

# 3. Full E2E smoke test
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"subject":"m","tenantId":"t1","roles":["PAYMENT_SUBMIT","PAYMENT_VIEW"]}' | jq -r '.token')

# Submit → wait → check → audit
PAYMENT_ID=$(curl -s -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount":11.11,"currency":"USD","creditorAccount":"NL91ABNA0417164300","debtorAccount":"DE89370400440532013000","paymentMethod":"CARD"}' \
  | jq -r '.paymentId')

sleep 5

echo "=== Payment Status ==="
curl -s "http://localhost:8080/api/v1/payments/$PAYMENT_ID" -H "Authorization: Bearer $TOKEN" | jq

echo "=== Audit Trail ==="
curl -s "http://localhost:8080/api/v1/payments/$PAYMENT_ID/audit" -H "Authorization: Bearer $TOKEN" | jq

echo "=== Idempotency ==="
# Resubmit with same key should return 200 not 202

echo "=== Tenant Isolation ==="
TOKEN_B=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"subject":"other","tenantId":"t2","roles":["PAYMENT_VIEW"]}' | jq -r '.token')
curl -s "http://localhost:8080/api/v1/payments/$PAYMENT_ID" -H "Authorization: Bearer $TOKEN_B" | jq
# Expected: 404

# 4. Stop
docker-compose down
```

---

## Implementation Dependency Graph

```
Step 1: Project Setup
  │
  ▼
Step 2: Domain Model ← pure Java, no dependencies
  │
  ▼
Step 3: Ports/Interfaces ← depends on domain
  │
  ├──────────────────┬────────────────────┐
  ▼                  ▼                    ▼
Step 4: Security   Step 5: Persistence  Step 9: External Adapters
  │                  │                    │  (fraud mock, bank sim)
  │                  │                    │
  ├──────────────────┼────────────────────┘
  ▼                  ▼
Step 6: Application Services ← ties ports to adapters
  │
  ▼
Step 7: REST API ← exposes use cases
  │
  ▼
Step 8: Kafka ← connects async pipeline
  │
  ▼
Step 10: Log Masking
  │
  ▼
Step 11: Integration Tests ← proves everything works together
  │
  ▼
Step 12: README & Polish
```

## Test Count Summary

| Step | Test Type | # Tests | Runtime |
|------|-----------|---------|---------|
| 2 | Unit | ~15 | < 1s |
| 4 | Unit | ~8 | < 1s |
| 5 | Integration (@DataJpaTest) | ~8 | ~3s |
| 6 | Unit (mocked) | ~12 | < 1s |
| 7 | Integration (@WebMvcTest) | ~8 | ~3s |
| 8 | Integration (Testcontainers) | ~3 | ~15s |
| 9 | Unit + @WebMvcTest | ~6 | ~3s |
| 10 | Unit | ~3 | < 1s |
| 11 | Full Integration (Testcontainers) | ~8 | ~30s |
| **Total** | | **~71** | **~60s** |

