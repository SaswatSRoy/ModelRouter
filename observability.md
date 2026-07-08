# Observability Design

## Philosophy

Every routing decision must be explainable after the fact without reproducing it — "which providers were considered, which was chosen, why, what it cost, and how long it took" is baseline information, not a debug-mode luxury. Observability is wired at module boundaries via interceptors/decorators (ingress, routing engine stages, adapter invocation), not scattered as manual instrumentation calls through business logic — this keeps `router-core` readable and ensures instrumentation can't silently drift out of sync with the code it's observing.

## Traces (OpenTelemetry)

A single distributed trace per inference request, spanning:

```
router-ingress (root span)
 └─ PolicyResolver
 └─ CandidateEnumerator
 └─ ProviderScorer
 └─ RoutingStrategy.rank()
 └─ ExecutionEngine
     └─ attempt #1: provider-anthropic.invoke()   [failed: 503]
     └─ attempt #2: provider-openai.invoke()      [succeeded]
 └─ ResponseNormalizer
```

Each attempt span carries: provider ID, model ID, outcome, latency, and (if failed) the normalized error code — enough to answer "why did this request fail over" without needing prompt content in the trace. Traces export via OTLP to any OTel-compatible backend (Jaeger, Tempo, vendor APM).

## Metrics (Prometheus via Micrometer)

| Metric | Type | Labels |
|---|---|---|
| `modelrouter_routing_overhead_ms` | Histogram | `strategy` |
| `modelrouter_requests_total` | Counter | `tenant`, `provider`, `model`, `outcome` |
| `modelrouter_provider_latency_ms` | Histogram | `provider`, `model` |
| `modelrouter_provider_error_rate` | Gauge | `provider`, `model` |
| `modelrouter_circuit_breaker_state` | Gauge (0/1/2) | `provider`, `model` |
| `modelrouter_cache_hit_ratio` | Gauge | `cache_tier` (L1/L2/semantic) |
| `modelrouter_cost_usd_total` | Counter | `tenant`, `provider`, `model` |
| `modelrouter_rate_limit_rejections_total` | Counter | `tenant` |

`modelrouter_routing_overhead_ms` is the metric CI enforces the <30ms budget against (ARCHITECTURE.md §6) — it excludes provider call time by construction (measured from request-received to candidate-selected).

Reference Grafana dashboards (per-tenant cost, provider health overview, routing overhead SLO) ship as part of the Phase 2 deliverable.

## LLM-Native Tracing (Langfuse)

Where a tenant opts in, a parallel export path sends prompt/completion content, token usage, and cost to Langfuse — kept deliberately separate from the OTel operational trace (which never carries prompt content by default, per [docs/security.md](security.md)). This lets teams get prompt-level debugging and eval tooling without operational traces becoming a compliance liability by default.

## Logging

Structured (JSON) logs, correlation-ID-linked to the OTel trace, at INFO for routing decisions (provider chosen, fallback occurred) and WARN/ERROR for failures. Request/response bodies are redacted by default (see [docs/security.md](security.md)); log volume is intentionally kept low-cardinality and cheap since it's not the primary debugging tool — traces and metrics are.

## Alerting (reference, Phase 2+)

Suggested SLO-based alerts, shipped as example Prometheus alerting rules rather than hardcoded behavior:

- Routing overhead p99 > 30ms sustained over 5m
- Any provider circuit breaker `OPEN` for > 2m
- Tenant cost burn rate exceeding configured budget pace
- Cache hit ratio dropping sharply (possible cache backend issue)

## Dashboard vs. Grafana

The operator-facing `router-dashboard` (ARCHITECTURE.md §3.9) is a product surface for day-to-day operation (live decisions, policy editing, cost breakdown) and talks to `router-admin`, not to Prometheus directly — it is not a Grafana replacement, and teams are expected to use both: the dashboard for operational/policy workflows, Grafana/Langfuse for deep metrics and prompt-level analysis.
