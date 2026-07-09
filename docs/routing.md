# Routing Pipeline Design

See [ARCHITECTURE.md](../ARCHITECTURE.md) §3.2 for module context, [GLOSSARY.md](../GLOSSARY.md) for terminology, and [diagrams/routing-flow.mmd](../diagrams/routing-flow.mmd) for the visual flow.

## Subsystem Decomposition

The routing pipeline is decomposed into three subsystems (see [rfcs/RFC-001.md](../rfcs/RFC-001.md) §4.3 for the rationale):

```
InferenceRequest
   ┌──────────────────────────────────────────────────────────────┐
   │  CONTEXT OPTIMIZER (Future RFC)                              │
   │  (Summarization, compression, trimming, influenced by        │
   │   external FeedbackEvents)                                   │
   └──────────────────────────┬───────────────────────────────────┘
                              ▼
   ┌──────────────────────────────────────────────────────────────┐
   │  POLICY ENGINE (core.policy)                                 │
   │  PolicyResolver        (resolve effective RoutingPolicy:     │
   │                         system default + tenant + request)   │
   └──────────────────────────┬───────────────────────────────────┘
                              ▼
   ┌──────────────────────────────────────────────────────────────┐
   │  EXECUTION PLANNER (core.planner)                            │
   │  RequestAnalyzer       (pluggable abstraction for request    │
   │                         semantic intent categorization)      │
   │  CandidateEnumerator   (structurally eligible: capability    │
   │                         match, privacy tier, not excluded)   │
   │  ProviderScorer        (attach live scores: latency, error   │
   │                         rate, cost, capacity headroom)       │
   │  RoutingStrategy       (rank eligible candidates per policy) │
   └──────────────────────────┬───────────────────────────────────┘
                              ▼  ExecutionPlan
   ┌──────────────────────────────────────────────────────────────┐
   │  EXECUTION RUNTIME (core.execution)                          │
   │  ExecutionEngine       (invoke top candidate; on failure,    │
   │                         retry/fallback per policy)           │
   │  CircuitBreakerRegistry (per-provider, per-model state)      │
   │  ResponseNormalizer    (provider → canonical response)       │
   └──────────────────────────────────────────────────────────────┘
```

Each subsystem is independently testable. The boundary between them is a well-defined data contract: policy engine outputs a `RoutingPolicy`, planner outputs an `ExecutionPlan`, runtime outputs an `InferenceResponse`.

## Policy Engine (`core.policy`)

### RoutingPolicy Model

```java
record RoutingPolicy(
    Double maxCostUsd,
    Integer maxLatencyMs,
    PrivacyTier privacyTier,          // e.g. LOCAL_ONLY, CLOUD_ALLOWED
    Set<String> requiredCapabilities, // e.g. "128k-context", "function-calling"
    List<String> preferredProviders,  // soft preference, breaks ties
    Set<String> excludedProviders,    // hard exclusion
    RetryPolicy retryPolicy,
    String strategyId                 // which RoutingStrategy to use
) {}
```

### Hierarchical Policy Resolution

Policies are resolved hierarchically: **system default → tenant default → request override**, each layer able to override or further constrain (never loosen) the layer above for security-sensitive fields like `privacyTier` and `excludedProviders`.

| Field | Merge Rule |
|---|---|
| `maxCostUsd` | Request may lower (tighten), never raise above tenant ceiling |
| `maxLatencyMs` | Request may lower, never raise above tenant ceiling |
| `privacyTier` | Request may tighten (CLOUD_ALLOWED → LOCAL_ONLY), never loosen |
| `requiredCapabilities` | Request may add, never remove |
| `preferredProviders` | Request overrides tenant; tenant overrides system |
| `excludedProviders` | Union across all layers (exclusions are additive, never removable by a lower layer) |
| `retryPolicy` | Request may reduce max attempts, never increase beyond tenant limit |
| `strategyId` | Request overrides, subject to tenant-level allowlist |

This tightening-only semantics for security-sensitive fields ensures that a per-request policy override can never relax a constraint set by a tenant administrator.

## Execution Planner (`core.planner`)

### Provider Scoring

Composite score per `(provider, model)`:

```
score = w_latency * normalize(latencyEwma)
      + w_cost    * normalize(costPerToken)
      + w_error   * normalize(errorRate)
      + w_capacity* normalize(capacityHeadroom)
```

Weights (`w_*`) are supplied by the active `RoutingStrategy`, not hardcoded in the scorer — `CheapestViableStrategy` sets `w_cost` high and enforces a latency *floor* (not weight) from the policy; `LowestLatencyStrategy` inverts that. This keeps the scorer strategy-agnostic and the strategies declarative.

Signals are gathered:
- **Passively**, from real request outcomes (primary signal, near-real-time).
- **Actively**, from a low-frequency background health prober per provider (recovery detection).

State lives in Redis, shared across all gateway pods, with a short-TTL in-process cache to avoid a network round-trip on every routing decision (see [rfcs/RFC-001.md](../rfcs/RFC-001.md) §4.6).

### Extensibility Contract

A third-party `RoutingStrategy`:

```java
public interface RoutingStrategy {
    List<ProviderCandidate> rank(
        RoutingPolicy policy,
        List<ScoredCandidate> eligibleCandidates,
        RequestFeatures features
    );
}
```

No access to adapter internals, no side effects expected or permitted — a strategy is a pure function from (policy, scored candidates, request features) to a ranked list. This is what allows an AI-powered strategy ([rfcs/RFC-001.md](../rfcs/RFC-001.md) §7) to be a drop-in implementation with zero changes to the execution runtime, adapters, or the reliability layer.

## Execution Runtime (`core.execution`)

### Retry & Fallback

Retry and fallback are unified: the execution planner output is an **`ExecutionPlan`**, not a single choice. The `ExecutionEngine` walks the plan:

1. Invoke candidate N.
2. On a retryable failure (timeout, 429, 5xx) *before the first response byte*, and while candidate N's own retry budget (from `RetryPolicy`) isn't exhausted, retry candidate N with backoff.
3. On budget exhaustion or a non-retryable-but-fallback-eligible failure, advance to candidate N+1.
4. If the list is exhausted, return `ALL_CANDIDATES_EXHAUSTED` with per-candidate failure detail.

**Streaming limitation (v1):** once the first chunk has been sent to the client, retry/fallback can no longer transparently occur — the client has already begun consuming a response from a specific provider. This is documented, not silently swallowed. See [failure-modes.md](failure-modes.md) for the full failure analysis.

### Circuit Breaking

Per `(provider, model)` circuit breaker (not global, not even global-per-provider) so that one degraded model on a provider doesn't take healthy models on the same provider out of rotation. States: `CLOSED → OPEN` (on error-rate threshold breach) `→ HALF_OPEN` (after cooldown, admits a trickle of traffic) `→ CLOSED` (on sustained success) or back to `OPEN`.

## Caching Interaction

Cache lookup happens *before* the execution planner — a cache hit short-circuits the entire pipeline below the policy engine. Cache keys incorporate the resolved policy's `requiredCapabilities` and a normalized hash of the request content, so a cache hit is never served across incompatible policy constraints (e.g., a response generated under `CLOUD_ALLOWED` is never served to a `LOCAL_ONLY` request, even if content matches).

## Performance Budget

| Subsystem | Target | What it covers |
|---|---|---|
| Policy Engine | <2ms | Policy resolution and merge |
| Execution Planner | <15ms | Candidate enumeration + Redis score fetch + strategy ranking |
| Execution Runtime (orchestration) | <13ms | Candidate selection + cache lookup + response normalization |
| **Total routing overhead** | **<30ms** | Excludes actual provider call time |

Enforced via micro-benchmarks in CI, not aspiration. See [ARCHITECTURE.md](../ARCHITECTURE.md) §6.
