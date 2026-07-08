# ModelRouter — Roadmap

Status tracking for feature delivery. See [RFC-001.md](RFC-001.md) for the reasoning behind sequencing decisions.

## Phase 0 — Design (current)

- [x] README, ARCHITECTURE, RFC-001 drafted
- [x] Module boundaries and ports/adapters defined
- [ ] Design review sign-off from maintainers
- [ ] `router-provider-spi` interface frozen for v1 (`InferenceProvider` contract)

## Phase 1 — Core Routing (MVP)

Goal: a single team can replace hardcoded OpenAI/Anthropic calls with ModelRouter and get retries, fallback, and basic observability for free.

- [ ] `router-core`: policy model, static/rule-based `RoutingStrategy` (weighted round robin, priority-order fallback)
- [ ] `router-ingress`: REST API, synchronous + SSE streaming
- [ ] `provider-openai`, `provider-anthropic` adapters
- [ ] Retry + fallback state machine (pre-first-byte only)
- [ ] Per-provider circuit breaker
- [ ] Basic API-key authentication
- [ ] Structured logging + OpenTelemetry traces (no dashboard yet)
- [ ] Docker Compose local dev environment
- [ ] CI: unit tests + Testcontainers-based adapter integration tests
- [ ] `sdk-java` v0 (blocking + reactive clients)

**Exit criteria:** routing overhead <30ms measured in CI micro-benchmarks; a demo app running entirely through ModelRouter with induced provider failures demonstrating automatic fallback.

## Phase 2 — Reliability, Cost & Multi-Provider

Goal: production-viable for teams running real traffic across 3+ providers with cost/latency constraints.

- [ ] `provider-gemini`, `provider-groq`, `provider-fireworks`, `provider-togetherai`, `provider-azure-openai` adapters
- [ ] Live provider scoring (latency EWMA, error rate, capacity headroom)
- [ ] Cost-aware and latency-aware routing strategies
- [ ] Privacy-aware routing (local-vs-cloud tiering)
- [ ] Redis-backed prompt/response cache
- [ ] Kafka event pipeline: usage metering, async score updates
- [ ] Active + passive health checks
- [ ] Rate limiting (tenant-scoped, token-bucket)
- [ ] OAuth2/OIDC authentication, tenant/project AuthZ
- [ ] `router-admin` CQRS command API + policy hot-reload
- [ ] Dashboard v1: live routing decisions, provider health, cost breakdown
- [ ] Prometheus metrics + Grafana dashboards (reference implementation)
- [ ] `sdk-android` v0

**Exit criteria:** a design partner running >10k requests/day across at least 3 providers in production.

## Phase 3 — Scale & Platform Maturity

Goal: cloud-native, horizontally scalable, and observable enough to be trusted as core infrastructure.

- [ ] `provider-ollama`, `provider-vllm`, `provider-baseten` adapters (local/self-hosted inference)
- [ ] Kubernetes Helm chart, HPA on custom metrics
- [ ] Semantic caching (embedding similarity, pluggable vector backend)
- [ ] Langfuse integration (LLM-native tracing: prompts, completions, token/cost breakdown)
- [ ] Multi-region provider awareness (route to nearest healthy region)
- [ ] `sdk-java` / `sdk-android` GA (1.0, semver-stable)
- [ ] Kubernetes Operator: `RoutingPolicy` and `ProviderConfig` CRDs (built on the by-then-stable REST admin API)
- [ ] Load-tested at 1000+ concurrent requests with published benchmark results
- [ ] Security audit / threat model review published

**Exit criteria:** ModelRouter is running as core infrastructure for at least one team's production LLM traffic at meaningful scale, with a published performance benchmark.

## Stretch Goals

Not committed, order not implied — explored as the ecosystem and adopter needs clarify:

- AI-powered adaptive routing strategy (a `RoutingStrategy` implementation backed by a learned model — no core changes required, see RFC-001 §7)
- Dynamic/hot-pluggable provider loading (plugin marketplace)
- Hosted multi-tenant SaaS control plane
- Multi-modal request routing (image/audio capability-aware provider selection)
- Additional SDKs: Python, Go, TypeScript
- Policy marketplace / shareable routing policy templates
- Go-based lightweight edge/sidecar variant for in-cluster local-only routing

## Non-Goals

Explicitly out of scope, tracked so they don't silently creep back in:

- Model fine-tuning or training orchestration
- Prompt templating/authoring frameworks
- Zero-external-dependency deployment mode (Redis/Postgres/Kafka assumed) — may be revisited post-1.0
