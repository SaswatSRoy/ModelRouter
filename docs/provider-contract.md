# Provider Adapter Contract

Status: **Draft v0.1 (pre-implementation)**
Related: [GLOSSARY.md](../GLOSSARY.md), [providers.md](providers.md), [ARCHITECTURE.md](../ARCHITECTURE.md), [ROADMAP.md](../ROADMAP.md)

---

## Purpose

This document defines the formal contract that every provider adapter must satisfy to integrate with ModelRouter. It specifies interface requirements, behavioral contracts, versioning rules, and testing obligations.

A provider adapter is a module (`provider-<name>`) that implements the `InferenceProvider` port defined in `router-provider-spi`. The port is the only coupling point between `router-core` and any external inference provider. Conformance to this contract is what makes "add a provider without touching the core" possible (see [ARCHITECTURE.md §3.3](../ARCHITECTURE.md)).

---

## The Port: `InferenceProvider` SPI

### Interface Definition

```java
public interface InferenceProvider {

    ProviderId id();

    Mono<InferenceResponse> invoke(InferenceRequest request);

    Flux<InferenceChunk> invokeStreaming(InferenceRequest request);

    ProviderCapabilities capabilities();

    Mono<HealthStatus> healthCheck();

    CostEstimate estimateCost(InferenceRequest request);
}
```

### Method-Level Contracts

#### `id()`

Must return a stable, unique `ProviderId`. Must be idempotent, deterministic, and never null. Used as the key for circuit breakers, scoring, and metrics.

**Format:** lowercase-alphanumeric-with-hyphens (e.g., `openai`, `azure-openai`, `ollama`).

#### `invoke(InferenceRequest)`

Synchronous (non-streaming) inference.

- Must return a `Mono<InferenceResponse>` that completes with exactly one response or errors.
- Must map all provider-specific errors to ModelRouter's normalized error taxonomy (see [Error Classification](#error-classification) below).
- Must respect cancellation (`Mono` disposal).
- Must not block the calling thread (reactive contract).
- Timeout handling is the adapter's responsibility; the execution runtime applies its own deadline, but the adapter should implement a provider-appropriate connect/read timeout.

#### `invokeStreaming(InferenceRequest)`

Streaming inference.

- Must return a `Flux<InferenceChunk>` that emits zero or more chunks followed by completion or error.
- Must handle provider-specific streaming protocols (SSE, chunked transfer, WebSocket) and normalize to `InferenceChunk`.
- Must propagate backpressure.
- Must support cancellation.

#### `capabilities()`

Returns a `ProviderCapabilities` describing the provider's supported modalities, context window, function calling support, available models, and privacy tier (`LOCAL` vs `CLOUD`).

- Called at boot and periodically; it should be fast and may be cached by the adapter.
- **Must never throw.** Return a conservative/minimal capability set if the provider's capability API is unavailable.

#### `healthCheck()`

Returns a `Mono<HealthStatus>` representing the provider's current health.

- Used by the active health prober.
- Must complete within **5 seconds** (hard timeout enforced by the runtime).
- Must not trigger billing on the provider side — use a lightweight endpoint, not a real inference call.

#### `estimateCost(InferenceRequest)`

Returns a `CostEstimate` for the given request based on the provider's pricing model and the request's token count estimate.

- Used during scoring to inform cost-aware routing.
- Must be a **pure computation** (no network call).
- May return `CostEstimate.UNKNOWN` if pricing data is unavailable; the scorer will treat this as a neutral signal, not a disqualifier.

---

## Behavioral Contracts

### Error Classification

Every adapter **MUST** classify provider errors into ModelRouter's error taxonomy:

| Error Category | Trigger Conditions | Retry Behavior |
|---|---|---|
| `RETRYABLE_SERVER_ERROR` | 5xx, timeout, connection reset | Retryable; fallback eligible |
| `RETRYABLE_RATE_LIMITED` | 429 or equivalent | Retryable with backoff; capacity signal |
| `NON_RETRYABLE_CLIENT_ERROR` | 400, 401, 403, 404 | Not retried; not eligible for fallback |
| `NON_RETRYABLE_PROVIDER_ERROR` | Provider-specific permanent failure | Not retried; not eligible for fallback |
| `UNKNOWN` | Unclassified | Treated as non-retryable by default |

> [!WARNING]
> Misclassification of errors (e.g., treating a 401 as retryable) will cause incorrect retry/fallback behavior and is considered a bug.

See also: [api.md](api.md) for the client-facing error model.

### Thread Safety

All methods must be thread-safe. Multiple gateway pods (and multiple threads within a pod) will call the same adapter instance concurrently.

### Resource Management

Adapters own their HTTP clients and connection pools. They must:

- Implement lifecycle hooks (Spring's `@PreDestroy` or `DisposableBean`) for clean shutdown.
- Release connections on `Mono`/`Flux` cancellation.
- Not leak file descriptors or threads.

### Rate-Limit Header Parsing

Adapters **SHOULD** parse provider-specific rate-limit headers (e.g., `x-ratelimit-remaining`, `x-ratelimit-reset`) and expose them via a supplementary `RateLimitInfo` accessor. This feeds the `capacityHeadroom` component of provider scoring (see [routing.md](routing.md)).

---

## Versioning Rules

### SPI Versioning

The `router-provider-spi` module follows [Semantic Versioning 2.0.0](https://semver.org/):

| Change Type | Scope | Examples |
|---|---|---|
| **MAJOR** (breaking) | Removes or changes existing API surface | Removing a method from `InferenceProvider`; changing a method signature; changing the semantics of an existing method |
| **MINOR** (backward-compatible additions) | Adds new surface with defaults | Adding a new `default` method to `InferenceProvider` (with a sensible default so existing adapters don't break); adding new fields to `ProviderCapabilities` with defaults; adding new error taxonomy entries |
| **PATCH** (bug fixes) | No API surface change | Fixing Javadoc; correcting default implementations |

### Compatibility Guarantee

- The SPI will **not** have a MAJOR version bump within a Phase (per [ROADMAP.md](../ROADMAP.md)).
- Adapters compiled against SPI version `X.Y` are guaranteed to work with any SPI version `X.Z` where `Z >= Y`.
- Before any MAJOR bump, a deprecation cycle of **at least one Phase** is required.
- New `default` methods will always provide a backward-compatible default (typically returning an empty/unknown value).

### Adapter Versioning

Each provider adapter has its own version, independent of the SPI version. Adapter versions track the provider SDK / API version they target.

An adapter's README (or module doc) **must** declare which SPI version it was built against.

---

## Testing Requirements

Every adapter **MUST** include:

1. **Unit tests** — for request/response mapping, error classification, cost estimation.
2. **Integration tests** — using [WireMock](https://wiremock.org/) or [Testcontainers](https://www.testcontainers.org/) to simulate the provider's API.
3. **Contract tests** — a shared test suite (provided by `router-provider-spi`) that validates any `InferenceProvider` implementation against the behavioral contracts defined in this document.

Contract test examples:

- `invoke()` must not return null.
- `invokeStreaming()` must propagate errors.
- `capabilities()` must not throw.
- `healthCheck()` must complete within 5 seconds.
- Error classification must be consistent with the taxonomy above.

> [!IMPORTANT]
> The contract test suite is the formal verification that an adapter conforms to the SPI. **Passing it is a prerequisite for merging.**

---

## Adapter Registration

**v1:** Static registration via Spring component scanning (see RFC-001 §4.4). Each adapter is a `@Component` discovered at boot. No runtime class loading.

**Configuration** (credentials, endpoints, enabled/disabled, routing weight) is dynamic via the admin API + Kafka hot-reload. See [providers.md §Provider-Specific Configuration vs. Code](providers.md) for the rationale behind this split.
