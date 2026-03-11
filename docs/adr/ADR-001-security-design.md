# ADR-001: Security Architecture — Authentication, Authorization & Data Protection

**Status:** Accepted  
**Date:** 2026-03-10  
**Deciders:** Senior Development Team

---

## Context

We are building a payment gateway that handles PII/PCI-sensitive data (card numbers, account numbers, personal details). The system is multi-tenant: multiple merchant clients will submit and query payments through the same API. We must:

1. Authenticate every API request.
2. Authorize actions based on roles and tenant ownership.
3. Protect sensitive data at rest, in transit, and in logs.
4. Prevent cross-tenant data leakage.

The assignment scope explicitly states: **do not integrate with a real IAM provider**; mock the security context/token validation within the Spring Security filter chain.

---

## Decision

### 1. Authentication — JWT Bearer Tokens (Mocked Issuance)

- All API endpoints (except a dedicated `/api/auth/token` helper endpoint) require a `Bearer` JWT in the `Authorization` header.
- A custom `JwtAuthenticationFilter` (extending `OncePerRequestFilter`) intercepts every request, extracts and validates the JWT, and populates the `SecurityContext`.
- Token validation is performed locally using HMAC-SHA256 with a server-side secret key — no external IdP call.
- A helper endpoint `POST /api/auth/token` is provided **for development/testing only** to issue tokens with configurable `tenantId` and `roles` claims. In production, this would be replaced by an external identity provider (Keycloak, Auth0, etc.).

**JWT Claims Structure:**
```json
{
  "sub": "merchant-abc",
  "tenantId": "tenant-001",
  "roles": ["ROLE_PAYMENT_SUBMIT", "ROLE_PAYMENT_VIEW"],
  "iat": 1710000000,
  "exp": 1710003600
}
```

### 2. Authorization — Role-Based + Tenant-Scoped

- **Role-Based Access Control (RBAC):**
  - `ROLE_PAYMENT_SUBMIT` — required to call `POST /api/v1/payments`
  - `ROLE_PAYMENT_VIEW` — required to call `GET /api/v1/payments/{id}`
  - Enforced via `@PreAuthorize` annotations on controller methods.

- **Tenant Isolation:**
  - The `tenantId` is extracted from the JWT and stored in a `TenantContext` (ThreadLocal-based holder).
  - Every repository query includes a `tenantId` filter clause: `WHERE tenant_id = :tenantId`.
  - A payment belonging to `tenant-001` is **invisible** to `tenant-002`, even if the payment ID is guessed.
  - This is enforced at the **service layer**, not just the controller, to prevent leakage through internal pathways.

### 3. Data Protection

| Concern | Approach |
|---|---|
| **Encryption at rest** | Sensitive fields (`cardNumber`, `accountNumber`) are encrypted using AES-256-GCM via a JPA `AttributeConverter` (`AesEncryptionConverter`). The encryption key is loaded from application properties (in production, from a vault/KMS). |
| **Masking in logs** | A custom Logback `MaskingPatternLayout` replaces patterns matching card numbers, account numbers, and other PII with masked representations (e.g., `****-****-****-1234`). |
| **Masking in API responses** | API response DTOs only carry masked/truncated representations of sensitive fields. The full card number is **never** returned to the client. |
| **Event payloads** | Kafka event payloads carry only `paymentId` and `tenantId` — no PII or card data is placed on the message bus. |
| **Transport security** | Spring Security config enforces HTTPS headers. In the embedded/dev profile, this is relaxed for local testing. |

### 4. Spring Security Filter Chain

```
Request
  │
  ▼
┌─────────────────────────────┐
│  JwtAuthenticationFilter    │  ← Extract & validate JWT
│  Set SecurityContext        │  ← Populate Authentication with tenantId + roles
│  Set TenantContext          │  ← ThreadLocal for tenant-scoped queries
└─────────────────────────────┘
  │
  ▼
┌─────────────────────────────┐
│  Spring Security Authorizer │  ← @PreAuthorize role checks
└─────────────────────────────┘
  │
  ▼
┌─────────────────────────────┐
│  Controller / Service       │  ← tenantId always scoped
└─────────────────────────────┘
```

---

## Alternatives Considered

| Alternative | Why Rejected |
|---|---|
| **OAuth2 Resource Server with external IdP** | Out of scope for this assignment. The filter chain is designed to be swappable — replacing `JwtAuthenticationFilter` with `spring-boot-starter-oauth2-resource-server` config is a drop-in change. |
| **API Key authentication** | Simpler but less expressive — no claims, no expiry, no standard tooling. JWT is industry standard for FinTech APIs. |
| **Database-level Row-Level Security (RLS)** | Powerful but ties tenant logic to a specific DB engine. Application-level filtering is more portable and testable. |
| **Transparent Data Encryption (TDE)** | Protects against disk theft but not application-level access. Column-level encryption via `AttributeConverter` provides finer-grained control. |

---

## Consequences

### Positive
- Security posture is production-shaped despite being a mock — easily upgradable to a real IdP.
- Tenant isolation is enforced at multiple layers (filter → service → repository).
- Sensitive data never leaks into logs, events, or API responses.
- Standard JWT approach makes the API consumable by any HTTP client.

### Negative
- Mock token endpoint is a security risk if accidentally deployed to production — must be profile-gated.
- HMAC-based JWT validation means the same key signs and verifies; in production, RSA/EC key pairs with JWKS endpoint are preferred.
- Application-level encryption adds slight complexity to queries (cannot search encrypted fields without additional indexing strategies).

### Risks
- Encryption key rotation requires a migration strategy (re-encrypt existing data).
- ThreadLocal-based `TenantContext` must be cleared after each request to prevent leakage in thread-pooled environments.

