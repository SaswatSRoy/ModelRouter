# API Design

## Design Principles

1. **Intent, not destination.** The request body never names a specific provider as a required field — only as an optional hint/constraint (`preferredProviders`, `excludedProviders`).
2. **Provider-agnostic response shape.** Regardless of which provider actually served the request, the response envelope is identical. Provider-specific fields, if any, live in a namespaced `providerMetadata` block, never in the top-level shape.
3. **Streaming and non-streaming share a request shape.** Only the `stream: true/false` flag and the transport (SSE vs. single JSON response) differ.
4. **Every response is explainable.** Responses include a `routingTrace` (optional, opt-in via header) showing which candidates were considered, which was chosen, and why — critical for debugging and trust.

## Primary Endpoint: Inference

```
POST /v1/inference
```

### Request

```json
{
  "tenantId": "acme-corp",
  "messages": [
    { "role": "user", "content": "Summarize this document." }
  ],
  "stream": false,
  "policy": {
    "maxCostUsd": 0.05,
    "maxLatencyMs": 3000,
    "privacyTier": "cloud-allowed",
    "requiredCapabilities": ["chat", "128k-context"],
    "preferredProviders": ["anthropic", "openai"],
    "excludedProviders": []
  }
}
```

`policy` is optional — if omitted, the tenant's default `RoutingPolicy` (configured via the admin API) applies. Per-request policy fields *override*, never fully replace, the tenant default.

### Response (non-streaming)

```json
{
  "id": "req_9f2a...",
  "content": "The document discusses...",
  "finishReason": "stop",
  "usage": { "promptTokens": 812, "completionTokens": 140, "costUsd": 0.011 },
  "routing": {
    "providerId": "anthropic",
    "modelId": "claude-sonnet",
    "attempt": 1,
    "latencyMs": 940
  }
}
```

### Response (streaming, SSE)

```
event: chunk
data: {"delta": "The document"}

event: chunk
data: {"delta": " discusses..."}

event: done
data: {"usage": {...}, "routing": {...}}
```

## Admin API (CQRS command side)

```
POST   /v1/admin/policies              create a RoutingPolicy
GET    /v1/admin/policies/{id}         read
PUT    /v1/admin/policies/{id}         update (triggers hot-reload event)
DELETE /v1/admin/policies/{id}

POST   /v1/admin/providers             register/configure a provider instance
PATCH  /v1/admin/providers/{id}        enable/disable, update weight/credentials
GET    /v1/admin/providers             list, with live health/score

GET    /v1/admin/tenants/{id}/usage    cost & usage breakdown
```

## Error Model

Errors are normalized across providers into a single taxonomy so callers never need provider-specific error handling:

| Code | Meaning |
|---|---|
| `POLICY_UNSATISFIABLE` | No provider candidate satisfies the request's policy constraints |
| `ALL_CANDIDATES_EXHAUSTED` | Every candidate in the ranked list failed (with per-candidate detail) |
| `RATE_LIMITED` | Tenant-level rate limit exceeded (not a provider rate limit — those trigger fallback, not a client-facing error) |
| `UNAUTHORIZED` / `FORBIDDEN` | AuthN/AuthZ failure |
| `INVALID_REQUEST` | Malformed request body |

## Versioning

The API is versioned via URL path (`/v1/...`). Breaking changes ship as `/v2/...` with `/v1/...` maintained for a documented deprecation window (minimum two Phase-cycles per ROADMAP.md). This is simpler and more explicit for infra consumers than header-based versioning, at the cost of some URL churn — an accepted tradeoff for a control-plane API.

## gRPC

A parallel gRPC service (`ModelRouterService`) mirrors this API for the Android SDK and other latency-sensitive internal callers, using `.proto` definitions kept in lockstep with the REST schema via a shared IDL-first generation step (Phase 2).
