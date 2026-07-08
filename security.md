# Security Design

## Authentication

ModelRouter supports three auth mechanisms, selectable per-deployment or even per-tenant, all implemented as pluggable `Spring Security` filter chains:

| Mechanism | Use case |
|---|---|
| API keys (hashed at rest, prefix-identifiable for rotation/audit) | Simple self-hosted adopters, service-to-service where OIDC isn't already in place |
| OAuth2 / OIDC | Enterprise adopters with existing identity providers (Okta, Azure AD, etc.) |
| mTLS | Service-to-service traffic within a cluster/mesh, where the mesh already terminates identity |

Every authenticated request resolves to a `tenantId` and, optionally, a `projectId` — these are the units AuthZ, rate limiting, cost budgets, and routing policy are all scoped to.

## Authorization

Tenant/project-scoped RBAC:

- **Caller role** — can submit inference requests within the tenant's policy constraints; cannot exceed tenant-level cost/rate ceilings even if a request-level policy claims otherwise (policy layering is override-only-tightens for security-relevant fields, per [docs/routing.md](routing.md)).
- **Operator role** — can manage `RoutingPolicy` and provider configuration for their tenant via the admin API.
- **Admin role** — cross-tenant visibility, provider registry management, global defaults.

## Secrets Management

Provider API keys and other credentials are never stored in plaintext in PostgreSQL or in application config. They are stored via a pluggable secrets backend (Kubernetes Secrets for simple deployments, HashiCorp Vault or a cloud KMS for production), referenced by ID from the provider configuration record, and fetched at adapter initialization / rotation time — never logged, never included in trace/span attributes.

## Data Handling & Privacy

- **`privacyTier` as a routing input, not just a label.** Requests marked `LOCAL_ONLY` are structurally ineligible for any cloud provider candidate — enforced in `CandidateEnumerator`, not left to strategy discretion, so a misconfigured or malicious strategy implementation cannot leak sensitive requests to a cloud provider.
- **PII-aware logging redaction.** Request/response bodies are never logged verbatim by default; observability captures token counts, latency, cost, and provider/model identity, not prompt content, unless a tenant explicitly opts in (e.g., for Langfuse-based prompt debugging) with that content flagged and access-controlled separately from operational logs.
- **No cross-tenant data mixing.** Cache keys, score aggregates used for routing decisions, and rate-limit counters are namespaced per tenant; a tenant's traffic pattern never influences another tenant's routing outcomes (see [docs/routing.md](routing.md) cache-key design).

## Transport Security

TLS termination at the ingress (or mTLS end-to-end within a service mesh). Outbound calls to providers are always TLS, with certificate validation enforced (no adapter is permitted to disable verification, even for local/self-hosted providers like Ollama/vLLM — self-signed CAs are supported via explicit trust-store configuration instead).

## Rate Limiting & Abuse Prevention

Token-bucket rate limiting, enforced per-tenant at `router-ingress` before a request reaches the routing engine, backed by Redis counters shared across gateway pods (consistent with the stateless-pod scalability decision in [ARCHITECTURE.md](../ARCHITECTURE.md) §6). This protects both ModelRouter itself and downstream provider rate limits/cost exposure from a single misbehaving tenant.

## Audit

All admin-API mutations (policy changes, provider registration/deregistration, credential rotation) are written to an append-only audit log in PostgreSQL, including actor identity, timestamp, and diff — required for any adopter running ModelRouter as regulated infrastructure.

## Threat Model (initial, to be expanded pre-Phase-3 security review)

| Threat | Mitigation |
|---|---|
| Leaked provider API key | Secrets backend, never logged, rotatable without redeploy via admin API |
| Cross-tenant data leakage via cache | Tenant-namespaced cache keys |
| Sensitive request routed to cloud despite `LOCAL_ONLY` | Structural exclusion at `CandidateEnumerator`, not strategy-level discretion |
| Tenant exhausting shared capacity | Per-tenant rate limiting + cost ceilings enforced independent of request-level policy claims |
| Malicious/misconfigured third-party `RoutingStrategy` | Strategy interface is a pure function with no side-effect capability and no access to adapter internals or credentials (see [docs/routing.md](routing.md)) |
