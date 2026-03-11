# Architecture Documentation

This directory contains the architectural documentation for the Payment Gateway backend engine.

> **📖 New here?** Start with the **[Reading Guide](READING-GUIDE.md)** — it walks you through all documents in the right order (~80 min total).
>
> **🔨 Ready to code?** Follow the **[Implementation Plan](IMPLEMENTATION-PLAN.md)** — 12 steps, each with testable milestones, unit tests, integration tests, and cURL commands.

## Architectural Decision Records (ADRs)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-001](adr/ADR-001-security-design.md) | Security Architecture — AuthN, AuthZ & Data Protection | ✅ Accepted |
| [ADR-002](adr/ADR-002-idempotency-strategy.md) | Idempotency Strategy | ✅ Accepted |
| [ADR-003](adr/ADR-003-event-driven-design.md) | Event-Driven Architecture Design | ✅ Accepted |
| [ADR-004](adr/ADR-004-domain-modeling-patterns.md) | Domain Modeling & Design Patterns | ✅ Accepted |
| [ADR-005](adr/ADR-005-immutable-audit-trail.md) | Immutable Audit Trail | ✅ Accepted |
| [ADR-006](adr/ADR-006-testing-strategy.md) | Testing Strategy | ✅ Accepted |
| [ADR-007](adr/ADR-007-fraud-openapi-integration.md) | Fraud Assessment — OpenAPI Integration | ✅ Accepted |
| [ADR-008](adr/ADR-008-design-tradeoffs.md) | Design Trade-offs & Scope Management | ✅ Accepted |

### ADR Coverage → Requirements Traceability

| Requirement | Covered By |
|---|---|
| Payment Ingestion (high-throughput, async, tracking ID) | ADR-003, ADR-004 |
| Strict Idempotency | ADR-002 |
| Fraud Assessment (pluggable, OpenAPI) | ADR-007, ADR-004 (Strategy pattern) |
| Acquiring Bank Simulation (variable latency, random fail) | ADR-003, ADR-008 |
| Immutable Audit Trail | ADR-005 |
| Status Inquiry (secure, tenant-scoped) | ADR-001 |
| API Security (AuthN/AuthZ) | ADR-001 |
| Data Protection (PII/PCI) | ADR-001 |
| Tenant Isolation | ADR-001 |
| Event-Driven Architecture (Kafka, DLT, retries) | ADR-003 |
| Modern Java (Records, Sealed, Pattern Matching) | ADR-004 |
| Design Patterns (Hexagonal, Strategy, State Machine) | ADR-004 |
| Testing Strategy (Unit + Integration + Testcontainers) | ADR-006 |
| Design Trade-offs (timebox, conscious shortcuts) | ADR-008 |

## Diagrams

| Diagram | Description |
|---------|-------------|
| [Sequence Diagrams](diagrams/sequence-diagrams.md) | All sequence diagrams including ingestion, async pipeline, failure handling, and tenant isolation |

### Diagrams Included

1. **Payment Ingestion — Happy Path** — Full flow from client through JWT auth to Kafka event
2. **Idempotent Duplicate** — Client retry returns original response without creating new payment
3. **Async Processing Pipeline — Happy Path** — Fraud check → bank simulation → completion
4. **Fraud Rejection** — Pipeline stops after fraud rejection, no bank call
5. **Bank Failure** — Bank simulation rejects, payment marked as failed
6. **Dead Letter Topic** — Consumer retries exhausted, message sent to DLT
7. **Status Inquiry — Tenant Isolation** — Successful tenant-scoped query
8. **Cross-Tenant Rejection** — 404 response prevents information leakage
9. **End-to-End Flow Overview** — High-level architecture flowchart
10. **Payment State Machine** — Visual representation of all valid state transitions

## Rendering Mermaid Diagrams

The sequence diagrams use [Mermaid](https://mermaid.js.org/) syntax. They render automatically in:

- **GitHub** — native Mermaid support in `.md` files
- **GitLab** — native Mermaid support
- **IntelliJ IDEA** — install the "Mermaid" plugin
- **VS Code** — install "Markdown Preview Mermaid Support" extension
- **CLI** — use `@mermaid-js/mermaid-cli` (`mmdc -i input.md -o output.png`)

