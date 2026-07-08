# Routing Engine Design

See [ARCHITECTURE.md](../ARCHITECTURE.md) §3.2 for module context and [diagrams/routing-flow.mmd](../diagrams/routing-flow.mmd) for the visual flow.

## Pipeline

```
InferenceRequest
   → PolicyResolver         (resolve effective RoutingPolicy: tenant default + request overrides)
   → CandidateEnumerator     (which providers/models are structurally eligible: capability match, not excluded)
   → ProviderScorer          (attach live scores: latency, error rate, cost, capacity headroom)
   → RoutingStrategy         (rank eligible candidates per policy: e.g. cheapest-viable, lowest-latency)
   → ExecutionEngine         (invoke top candidate; on failure, retry/fallback per policy, advance list)
   → ResponseNormalizer      (provider-specific response → canonical InferenceResponse)
```

Each stage is a separate, independently testable component behind a narrow interface. `RoutingStrategy` is the only stage intended for third-party extension in v1 (see RFC-001 §7, "AI-based routing").

## RoutingPolicy Model

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

Policies are resolved hierarchically: **system default → tenant default → request override**, each layer able to override or further constrain (never loosen) the layer above for security-sensitive fields like `privacyTier` and `excludedProviders`.

## Provider Scoring

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

State lives in Redis, shared across all gateway pods, with a short-TTL in-process cache to avoid a network round-trip on every routing decision (see RFC-001 §4.5).

## Retry & Fallback

Retry and fallback are unified: the `RoutingStrategy` output is an **ordered candidate list**, not a single choice. The `ExecutionEngine` walks the list:

1. Invoke candidate N.
2. On a retryable failure (timeout, 429, 5xx) *before the first response byte*, and while candidate N's own retry budget (from `RetryPolicy`) isn't exhausted, retry candidate N with backoff.
3. On budget exhaustion or a non-retryable-but-fallback-eligible failure, advance to candidate N+1.
4. If the list is exhausted, return `ALL_CANDIDATES_EXHAUSTED` with per-candidate failure detail.

**Streaming limitation (v1):** once the first chunk has been sent to the client, retry/fallback can no longer transparently occur — the client has already begun consuming a response from a specific provider. This is documented, not silently swallowed; a Phase 2+ investigation may add client-side replay semantics for streaming fallback.

## Circuit Breaking

Per `(provider, model)` circuit breaker (not global, not even global-per-provider) so that one degraded model on a provider doesn't take healthy models on the same provider out of rotation. States: `CLOSED → OPEN` (on error-rate threshold breach) `→ HALF_OPEN` (after cooldown, admits a trickle of traffic) `→ CLOSED` (on sustained success) or back to `OPEN`.

## Caching Interaction

Cache lookup happens *before* candidate scoring — a cache hit short-circuits the entire pipeline below `PolicyResolver`. Cache keys incorporate the resolved policy's `requiredCapabilities` and a normalized hash of the request content, so a cache hit is never served across incompatible policy constraints (e.g., a response generated under `CLOUD_ALLOWED` is never served to a `LOCAL_ONLY` request, even if content matches).

## Extensibility Contract

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

No access to adapter internals, no side effects expected or permitted — a strategy is a pure function from (policy, scored candidates, request features) to a ranked list. This is what allows an AI-powered strategy (RFC-001 §7) to be a drop-in implementation with zero changes to `ExecutionEngine`, adapters, or the reliability layer.
