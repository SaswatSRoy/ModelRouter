# ModelRouter — Architecture

Status: **Draft v0.2 (pre-implementation)**
Owner: Core Architecture Group
Related: [rfcs/RFC-001.md](rfcs/RFC-001.md), [ROADMAP.md](ROADMAP.md), [GLOSSARY.md](GLOSSARY.md), [diagrams/](diagrams/)

> **Terminology note:** All terms used in this document are defined in [GLOSSARY.md](GLOSSARY.md). When in doubt about a term's meaning, consult the glossary — it is the authoritative source.

## 1. Architectural Style

ModelRouter follows **Hexagonal Architecture (Ports & Adapters)** with **Domain-Driven Design** for the routing domain, and **CQRS** at the boundary between the hot path (request routing) and the management path (policy configuration, provider registration, analytics).

Rationale:

- **Hexagonal / Ports & Adapters** is the natural fit for a system whose entire purpose is to talk to N interchangeable external systems (providers). The domain core must never depend on a provider SDK. Every provider integration is an *adapter* implementing a *port* defined by the domain.
- **DDD** gives us a shared vocabulary — `RoutingPolicy`, `ProviderCandidate`, `InferenceRequest`, `RoutingDecision` — that stays stable even as providers and strategies churn.
- **CQRS** separates the read-heavy, latency-critical routing decision path from the write-heavy, consistency-critical policy/configuration management path. They have different scaling and consistency requirements and should not share a data model.
- **Event-driven design** is used where it adds value (usage metering, async analytics, cache invalidation) but is *not* used on the synchronous request path, where it would add unacceptable latency and complexity.

We deliberately avoid over-applying patterns. Not every module needs CQRS; not every interaction needs an event. See RFC-001 §"Alternatives Considered" for what we rejected and why.

## 2. System Context

```
Client Apps ──▶ ModelRouter ──▶ Inference Providers (OpenAI, Anthropic, Gemini, Ollama, Baseten, vLLM, Groq, Fireworks, Together AI, Azure OpenAI)
                    │
                    ├──▶ Redis (cache, rate limit counters, health state)
                    ├──▶ PostgreSQL (policies, tenants, provider registry, audit)
                    ├──▶ Kafka (usage events, async metering, cache invalidation)
                    └──▶ Observability stack (OTel Collector → Prometheus / Grafana / Langfuse)
```

See [diagrams/component-diagram.mmd](diagrams/component-diagram.mmd) for the full component-level view and [diagrams/deployment-diagram.mmd](diagrams/deployment-diagram.mmd) for runtime topology.

## 3. Module Breakdown

### 3.1 `router-ingress`
**Responsibility:** Terminate client connections (REST + gRPC), request validation, protocol translation into the internal `InferenceRequest` domain object, streaming session management (SSE / chunked transfer for token streams).

**Depends on:** `router-core` (via port interfaces only).
**Never depends on:** any provider adapter directly.

### 3.2 `router-core` (the domain)

**Responsibility:** The routing domain itself. Pure domain logic, framework-agnostic where possible. The former monolithic "routing engine" is decomposed into distinct subsystems with clear boundaries:

#### 3.2.0 ContextOptimizer (Future/RFC)

**Responsibility:** A pre-execution step that applies policy-driven transformations to the prompt context (e.g., summarization, compression, trimming) before it reaches the policy engine or planner. This will be an optional, generic pipeline, potentially influenced by external `FeedbackEvent`s (like user satisfaction signals), but the pipeline itself will not compute these signals.

#### 3.2.1 Policy Engine (`core.policy`)

**Responsibility:** Resolves the effective `RoutingPolicy` for a given request. Implements the hierarchical merge: **system default → tenant default → request override**, where each layer can override or further constrain (never loosen) the layer above for security-sensitive fields like `privacyTier` and `excludedProviders`.

Components:
- `PolicyResolver` — the entry point; accepts tenant ID + per-request policy overrides, returns a fully resolved `RoutingPolicy`.
- `RoutingPolicy` model — the canonical intent-expression object: cost ceiling, latency SLA, privacy tier, required capabilities, preferred/excluded providers, retry policy, strategy ID.
- `PolicyStore` port — interface to the persistence layer (PostgreSQL via `router-admin`) and the hot-reload mechanism (Kafka change events).

**Why a separate subsystem:** Policy resolution is a pure, deterministic computation with its own testing surface (merge semantics, constraint tightening, validation). It has no dependency on live provider state, scoring, or execution — mixing it with those concerns would obscure its contract and make policy bugs harder to isolate.

#### 3.2.2 Execution Planner (`core.planner`)

**Responsibility:** Given a resolved `RoutingPolicy`, produces an `ExecutionPlan` containing `ProviderCandidate`s ready for execution. This subsystem owns the "which providers, in what order" question.

Components:
- `CandidateEnumerator` — filters the provider registry to structurally eligible candidates (capability match, not excluded, privacy-tier compatible). This is where `LOCAL_ONLY` enforcement happens — it is a structural exclusion, not a scoring signal.
- `RequestAnalyzer` (Future) — a pluggable abstraction (e.g. rule-based, ML, or LLM) to analyze the request's semantic intent, complexity, or safety and inform the routing strategy. We explicitly avoid hardcoding models like Random Forest here.
- `ProviderScorer` — attaches live composite scores to each eligible candidate (latency EWMA, error rate, cost per token, capacity headroom). Scores are read from Redis (shared across pods) with a short-TTL in-process L1 cache.
- `RoutingStrategy` — a pluggable interface that ranks scored candidates according to the policy. Built-in strategies: `LowestLatencyStrategy`, `CheapestViableStrategy`, `WeightedRoundRobinStrategy`, `PrivacyConstrainedStrategy`. Third-party strategies implement the same interface.

**Why a separate subsystem:** Planning is a stateless, side-effect-free pipeline (enumerate → score → rank) that can be tested and benchmarked independently of execution. It consumes policy (from the policy engine) and live scores (from Redis) but never touches adapters, never issues network calls to providers, and never mutates state. This separation is what makes `RoutingStrategy` a pure function — if planning and execution were interleaved, strategy implementations would be tempted to inspect execution state, breaking the pluggability contract.

#### 3.2.3 Execution Runtime (`core.execution`)

**Responsibility:** Takes the `ExecutionPlan` from the execution planner and executes it: invoke the top candidate via its adapter, manage retries and fallback, enforce circuit-breaker state, and normalize the response.

Components:
- `ExecutionEngine` — the state machine that walks the `ExecutionPlan`. On a retryable failure (pre-first-byte), retries the current candidate with backoff up to the retry budget, then advances to the next candidate.
- `CircuitBreakerRegistry` — per-(provider, model) circuit breakers. State: CLOSED → OPEN (on error-rate threshold) → HALF_OPEN (after cooldown) → CLOSED (on sustained success).
- `ResponseNormalizer` — translates provider-specific response shapes into the canonical `InferenceResponse` / `InferenceChunk` stream.
- `RetryPolicy` engine — configurable per `RoutingPolicy`: max attempts, backoff strategy, retryable failure classes.

**Why a separate subsystem:** Execution is inherently stateful and side-effectful (network I/O, circuit-breaker mutations, retry state). Isolating it from planning means a bug in retry logic cannot corrupt candidate ranking, and a bug in scoring cannot cause an adapter to be invoked incorrectly. It also allows execution to be tested with fake adapters against known `ExecutionPlan`s, without requiring a real planner.

### 3.3 `router-provider-spi`
**Responsibility:** The *port* — a stable interface (`InferenceProvider`) that every provider adapter must implement: `invoke()`, `invokeStreaming()`, `capabilities()`, `healthCheck()`, `estimateCost()`. This module has near-zero external dependencies and changes rarely; it is the contract that makes "add a provider without touching the core" possible. See [docs/provider-contract.md](docs/provider-contract.md) for the full contract specification and versioning rules.

### 3.4 `provider-adapters/*`
One module per provider (`provider-openai`, `provider-anthropic`, `provider-gemini`, `provider-ollama`, `provider-baseten`, `provider-vllm`, `provider-groq`, `provider-fireworks`, `provider-togetherai`, `provider-azure-openai`). Each adapter:

- Implements `InferenceProvider` from `router-provider-spi`.
- Owns its own SDK/HTTP client, auth, error-mapping, and rate-limit handling.
- Treats local execution (e.g. Ollama, vLLM) as a first-class routing target, not a separate product.
- Is independently deployable/loadable (see §6, "Should providers be loaded dynamically?").
- Is independently testable via Testcontainers/WireMock without booting the whole router.
- Must pass the shared SPI contract test suite (see [docs/provider-contract.md](docs/provider-contract.md)).

### 3.5 `router-cache`
**Responsibility:** Two distinct caching concerns, kept as separate components behind one facade:
- **Prompt/response cache** (exact or near-exact match, Redis-backed, TTL + LRU).
- **Semantic cache** (embedding-similarity match, pluggable vector backend — Phase 3).

### 3.6 `router-admin`
**Responsibility:** The CQRS "command" side — policy CRUD, provider registration/deregistration, tenant management. Backed by PostgreSQL. Publishes change events (via Kafka) so `router-core` instances can hot-reload policy without a restart.

### 3.7 `router-observability`
**Responsibility:** OpenTelemetry instrumentation (traces + metrics), Micrometer → Prometheus bridge, Langfuse exporter for LLM-specific traces (prompts, token usage, cost). Cross-cutting; wired via decorators/interceptors rather than scattered manual instrumentation.

### 3.8 `router-security`
**Responsibility:** AuthN (API keys, OAuth2/OIDC, mTLS for service-to-service), AuthZ (tenant/project scoping), request signing, secrets management integration (Vault/K8s Secrets), PII-aware logging redaction.

### 3.9 `router-dashboard` (Phase 2+)
**Responsibility:** Operator-facing UI — live routing decisions, provider health, cost breakdown, policy editor. Separate deployable, talks to `router-admin` and the observability stack, never to `router-core` directly.

### 3.10 SDKs (`sdk-java`, `sdk-android`)
Thin, idiomatic clients that speak the ModelRouter API. No routing logic lives in the SDK — that would reintroduce the exact coupling ModelRouter exists to remove. SDKs handle connection management, streaming ergonomics, and typed request/response models only.

## 4. Interaction Model

The canonical request flow (see [diagrams/sequence-diagram.mmd](diagrams/sequence-diagram.mmd) and [diagrams/routing-flow.mmd](diagrams/routing-flow.mmd)):

1. Client sends an `InferenceRequest` (intent, not a provider) to `router-ingress`.
2. `router-security` authenticates/authorizes, rate-limiter checks quota.
3. **Policy Engine:** `core.policy.PolicyResolver` resolves the effective `RoutingPolicy` for the tenant/request.
4. `router-cache` checks for a cache hit (short-circuits the pipeline below if hit).
5. **Execution Planner:** `core.planner.CandidateEnumerator` filters to eligible candidates; `core.planner.ProviderScorer` retrieves live scores from Redis; `core.planner.RoutingStrategy` produces an `ExecutionPlan`.
6. **Execution Runtime:** `core.execution.ExecutionEngine` invokes the top candidate via its adapter (from `provider-adapters/*`).
7. On failure (timeout, 5xx, rate-limit), `core.execution` applies the retry/fallback policy and advances to the next candidate.
8. Response (or stream) is normalized into a provider-agnostic shape and returned to the client.
9. Asynchronously, usage/cost/latency events are emitted to Kafka for metering, analytics, and cache/score updates — off the hot path.

## 5. Deployment Model

- **Container-first.** Every module ships as an OCI image; `router-core`+`router-ingress`+adapters are typically packaged together as the `modelrouter-gateway` deployable, with `router-admin` and `router-dashboard` as separate deployables so their release cadence and scaling profile can differ.
- **Kubernetes-native.** Stateless gateway pods behind a Service/Ingress, horizontally autoscaled on CPU + custom Prometheus metrics (in-flight requests, p99 latency). Redis and PostgreSQL run as managed services or operators (not modeled by ModelRouter itself). Kafka as a shared cluster or managed offering (MSK/Confluent).
- **Config as data, not redeploy.** Routing policies live in PostgreSQL and are hot-reloaded via Kafka change events, so policy changes never require a gateway redeploy.

Full topology in [diagrams/deployment-diagram.mmd](diagrams/deployment-diagram.mmd) and [docs/deployment.md](docs/deployment.md).

## 6. Scalability Decisions

| Decision | Reasoning |
|---|---|
| WebFlux (reactive, non-blocking) on the ingress + core path | Inference calls are I/O-bound and long-lived (streaming); blocking threads-per-request does not scale to 1000+ concurrent requests without an enormous thread pool. See RFC-001 for the WebFlux vs. MVC debate. |
| Stateless gateway pods | All routing state (provider scores, health, policy) lives in Redis/PostgreSQL, not in pod memory, so any pod can serve any request — horizontal scaling is trivial and rolling deploys are safe. |
| Score computation is read-heavy and cache-local | Provider scores are read on every request but updated asynchronously (from response telemetry), so we optimize for fast local reads (Redis, with a short-TTL in-memory L1 cache) over strong consistency. |
| Kafka off the hot path | Usage metering and cache-invalidation events are fire-and-forget from the request path; a Kafka outage degrades analytics, never request serving. See [docs/failure-modes.md](docs/failure-modes.md). |
| Per-provider circuit breakers, not global | A single failing provider must not degrade routing to healthy providers; blast radius is contained per-provider (and per-model where relevant). |
| Target: <30ms routing overhead | This excludes the actual provider call. It bounds policy resolution + planning + candidate selection + cache lookup. Budget is enforced via micro-benchmarks in CI, not aspiration. |

## 7. Failure Modes

See [docs/failure-modes.md](docs/failure-modes.md) for the complete failure mode analysis, including:

- Degraded behavior for every infrastructure dependency (Redis, PostgreSQL, Kafka, OTel)
- Invariants that hold even under degraded conditions (privacy enforcement, auth, tenant isolation)
- Cascading failure prevention mechanisms

## 8. Non-Goals (v1)

To keep the design honest, explicitly out of scope for the initial architecture:

- Fine-tuning or model training orchestration.
- Prompt-engineering/templating frameworks (ModelRouter routes requests; it does not author them).
- A hosted, multi-tenant SaaS control plane (v1 is self-hostable OSS infrastructure).
- Cross-cloud data residency guarantees beyond routing-time privacy tiering.
- Browser-frontend SDK.
- Multi-modal routing optimization (structurally supported, not performance-optimized).

## 9. Open Questions

Tracked and answered in [rfcs/RFC-001.md](rfcs/RFC-001.md) §"Critical Design Questions". This document will be updated as those answers evolve.
