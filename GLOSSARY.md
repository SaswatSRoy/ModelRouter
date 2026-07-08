# Glossary

Canonical definitions for all ModelRouter project terminology. Every design document, RFC, and code comment must use these terms consistently.

| Term | Definition |
|------|------------|
| **Circuit Breaker** | Per-(provider, model) state machine governing fault isolation. States: CLOSED → OPEN → HALF_OPEN → CLOSED. Transitions are driven by error-rate thresholds and probe successes. |
| **Cold Path** | Asynchronous side-effect pipeline running outside the request-serving loop. Handles usage metering, cache population, and provider score updates via Kafka. Latency on the cold path does not affect response times. |
| **Control Plane** | ModelRouter itself — the infrastructure layer that makes routing decisions, enforces policies, and manages provider lifecycle. Contrast with *Data Plane*. |
| **CQRS** | Command Query Responsibility Segregation. The architectural boundary between the hot-path query side (serving inference requests from read-optimized stores) and the admin command side (writing configuration, tenants, and policies to PostgreSQL). |
| **Data Plane** | The actual inference traffic flowing through Provider Adapters to upstream providers. ModelRouter is the Control Plane that governs the Data Plane. |
| **Execution Planner** | The subsystem that enumerates eligible Provider Candidates, enriches them into Scored Candidates, and applies a Routing Strategy to produce a ranked candidate list. One of the three subsystems formerly unified under the monolithic "routing engine." |
| **Execution Runtime** | The subsystem that walks the ranked candidate list produced by the Execution Planner, invokes Provider Adapters, manages retries/fallback/circuit-breaking, and normalizes responses into the canonical Inference Response or Inference Chunk stream. One of the three subsystems formerly unified under the monolithic "routing engine." |
| **Fallback Chain** | The ranked candidate list produced by the Execution Planner. Retry and fallback are unified: when an invocation fails, the Execution Runtime advances to the next candidate in the list. |
| **Gateway** | The primary deployable artifact. Packages ingress, core routing subsystems, Provider Adapters, cache, and security into a single Spring Boot application. |
| **Hot Path** | The synchronous request-serving pipeline: ingress → Policy Engine → Execution Planner → Execution Runtime → response. All components on the hot path are latency-critical. |
| **Inference Chunk** | A single token or delta in a streaming Inference Response. Delivered as a Server-Sent Event (SSE) frame over a reactive stream. |
| **Inference Request** | The canonical request object accepted by the Gateway. Encapsulates the prompt/messages, model preferences, Routing Policy overrides, and tenant context. Provider-agnostic by design. |
| **Inference Response** | The canonical response object returned by the Gateway. Contains the model output, token usage, and optional Routing Trace. Provider-agnostic by design. |
| **Policy Engine** | The subsystem that resolves the effective Routing Policy for a given request by merging system defaults, tenant defaults, and per-request overrides (in that precedence order). One of the three subsystems formerly unified under the monolithic "routing engine." |
| **Privacy Tier** | A structural routing constraint with two values: `LOCAL_ONLY` (data must not leave the deployment boundary) or `CLOUD_ALLOWED` (cloud-hosted providers are eligible). Enforced at candidate enumeration time in the Execution Planner, not at strategy ranking time. |
| **Provider Adapter** | A concrete implementation of the Provider SPI (`InferenceProvider` interface) for a specific upstream provider (e.g., `OpenAiAdapter`, `AnthropicAdapter`). Responsible for request translation, API invocation, and response normalization. |
| **Provider Candidate** | A (provider, model) pair that is eligible for a given Inference Request after Privacy Tier filtering and capability matching. Not yet scored. |
| **Provider Score** | A composite metric maintained per (provider, model) pair. Components: latency EWMA, error rate, cost per token, and capacity headroom. Updated on the Cold Path; read on the Hot Path. |
| **Provider SPI** | The stable interface (`InferenceProvider`) that all Provider Adapters implement. Defines the contract for request translation, invocation (blocking and streaming), and response normalization. SPI = Service Provider Interface. |
| **Routing Policy** | The intent-expression object attached to an Inference Request. Declares constraints and preferences: cost ceiling, latency SLA, Privacy Tier, and required capabilities. Resolved by the Policy Engine from layered defaults and overrides. |
| **Routing Strategy** | A pluggable algorithm that ranks Scored Candidates to produce the Fallback Chain. Implementations include `CheapestViableStrategy`, `LowestLatencyStrategy`, and others. Selected by the Execution Planner based on the resolved Routing Policy. |
| **Routing Trace** | Opt-in response metadata (enabled via request header or Routing Policy flag) showing which Provider Candidates were considered, their scores, which candidate was chosen, and why. Intended for debugging and observability; never exposed in production by default. |
| **Scored Candidate** | A Provider Candidate enriched with live Provider Score data. Input to the Routing Strategy for ranking. |
| **Tenant** | The unit of multi-tenant isolation. Scopes authentication, rate limiting, cost budgets, and Routing Policy defaults. Identified by API key or JWT claim. |
