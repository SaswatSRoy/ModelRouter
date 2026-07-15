# ModelRouter

**The Intelligent Control Plane for AI Inference.**

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Status](https://img.shields.io/badge/status-design--phase-orange.svg)](ROADMAP.md)
[![Java](https://img.shields.io/badge/java-21-red.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/spring--boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

---

## Vision

Applications that call AI models should not need to know *who is serving them*.

They should express **intent** — "generate this completion, under this latency budget, at this cost ceiling, with this privacy requirement" — and let a dedicated control plane decide the rest: which provider, which model, which deployment, whether to retry, whether to fall back, whether to stream, whether to cache, whether to stay on-prem or go to the cloud.

That control plane is **ModelRouter**.

ModelRouter is not a chatbot. It is not a prompt-engineering framework. It is not "yet another OpenAI-compatible proxy" with a thin veneer of YAML. It is infrastructure — a policy-driven routing and reliability layer that sits between your applications and the fragmented, fast-moving landscape of inference providers, in the same spirit that a service mesh sits between your microservices, or an API gateway sits between the internet and your backend.

## The Problem

Every team building on LLMs eventually re-invents the same brittle logic:

```
if primary_provider_down:
    try secondary_provider
if request_is_sensitive:
    force_local_model
if cost_budget_exceeded:
    downgrade_to_cheaper_model
if latency_sla_at_risk:
    switch_region_or_provider
```

This logic gets duplicated across services, hardcoded into application code, and untested until the day a provider has an outage. Meanwhile:

- **Providers differ wildly** — latency, pricing, context windows, rate limits, capabilities, regional availability, and data-handling guarantees are all different, and all change without notice.
- **Vendor lock-in accumulates silently.** Provider-specific SDKs, error formats, and streaming protocols leak into business logic.
- **Reliability is an afterthought.** Retries, fallbacks, and circuit breaking get bolted on per-service, inconsistently, if at all.
- **Observability is fragmented.** Nobody has a single place to answer "what did this request cost, which provider served it, and why."

ModelRouter exists to solve this once, correctly, as shared infrastructure — not as a library every team reimplements.

## Why This Project Exists

The API gateway pattern solved this class of problem for HTTP microservices two decades ago: centralize cross-cutting concerns (auth, rate limiting, retries, observability, routing) instead of scattering them across every caller. Inference traffic has the same shape, with additional dimensions unique to AI workloads — token-based cost, streaming responses, model-capability negotiation, and privacy-sensitive routing (local vs. cloud). No existing OSS project treats this as a first-class **control plane** problem rather than a thin reverse proxy. ModelRouter is built to fill that gap, with the engineering rigor of production infrastructure from day one.

## Non-Goals

Explicitly out of scope — tracked so they don't silently creep back in:

| Non-Goal | Rationale |
|---|---|
| **Model fine-tuning or training orchestration** | ModelRouter routes inference requests; it does not manage the model lifecycle. Training pipelines are a different class of infrastructure with different resource, scheduling, and data requirements. |
| **Prompt templating / authoring frameworks** | ModelRouter routes requests; it does not author them. Prompt engineering belongs in application code or dedicated prompt-management tools. Coupling prompt logic to the routing layer would violate the separation of concerns that makes ModelRouter useful. |
| **Hosted multi-tenant SaaS control plane (v1)** | v1 is self-hostable OSS infrastructure. A hosted offering is a business-model decision that should follow, not precede, proving the self-hosted core is excellent. |
| **Zero-external-dependency deployment mode** | Redis, PostgreSQL, and Kafka are assumed. An embedded/lite mode is a plausible future variant, not a v1 goal. Attempting to support both modes from day one would dilute engineering effort and introduce conditional complexity throughout the codebase. |
| **Cross-cloud data residency guarantees** | ModelRouter enforces privacy-tier routing (LOCAL_ONLY vs. CLOUD_ALLOWED) at request time, but does not guarantee data residency compliance across jurisdictions. That is an operational/legal concern for the adopter's deployment topology, not for the routing layer. |
| **Browser-frontend SDK** | v1 targets backend services and mobile apps (Java + Android SDKs). Direct browser-to-ModelRouter traffic introduces CORS, credential-exposure, and abuse-surface concerns that are better solved by having a backend intermediary. |
| **Multi-modal routing optimization** | Multi-modal requests (image, audio) are structurally supported via the capability model but not performance-optimized in v1. Routing decisions for multi-modal payloads have different latency/cost profiles that require dedicated scoring work. |
| **Direct end-user clients / Chatbot UIs** | Telegram-bot style remote access or full conversational UIs are client features, not control plane features. ModelRouter handles infrastructure, not end-user interfaces. |
| **Knowledge-graph-heavy optimization** | Deep semantic processing is out of scope unless it directly supports routing (e.g., Request Analysis) or policy-driven context manipulation (e.g., ContextOptimizer). |

## Features

### Available in the design (see [ROADMAP.md](ROADMAP.md) for sequencing)

| Category | Capability |
|---|---|
| **Routing** | Multi-provider routing, dynamic provider selection, policy-driven routing with separate policy engine, execution planner, and execution runtime |
| **Reliability** | Configurable retry strategies, automatic fallback via `ExecutionPlan`, health checks, circuit breaking |
| **Performance** | Streaming-first design, prompt caching, semantic caching, latency-aware routing |
| **Cost & Privacy** | Cost-aware routing, privacy-aware routing, local-vs-cloud inference selection (local execution treated as a first-class routing target) |
| **Extensibility** | Provider adapter contract with SPI versioning, pluggable routing strategies, future `AdaptiveRoutingEngine` with `FeedbackEvent`s |
| **Advanced Pre-processing** | Policy-driven `ContextOptimizer` (Future), pluggable `RequestAnalyzer` (Future) (rules, ML, or LLMs) |
| **Platform** | AuthN/AuthZ, rate limiting, multi-tenant support, admin dashboard |
| **Observability** | Metrics (Prometheus), distributed tracing (OpenTelemetry), Langfuse integration |
| **Operability** | Kubernetes-native deployment, horizontal scaling, Docker-first packaging |
| **SDKs** | Android SDK, Java SDK (additional language SDKs on the roadmap) |

## High-Level Architecture

```
                     ┌────────────────────────────────────────┐
                     │              Client Applications         │
                     │   (Java SDK, Android SDK, REST, gRPC)    │
                     └───────────────────┬──────────────────────┘
                                          │
                                          ▼
                     ┌────────────────────────────────────────┐
                     │               ModelRouter                │
                     │  ┌────────────┐  ┌────────────────────┐ │
                     │  │   Ingress   │  │   Auth / RateLimit  │ │
                     │  │  (WebFlux)  │  │                     │ │
                     │  └─────┬──────┘  └──────────┬──────────┘ │
                     │        ▼                    ▼            │
                     │  ┌───────────────────────────────────┐   │
                     │  │      ContextOptimizer (Future)      │   │
                     │  │   Summarize / compress context      │   │
                     │  └───────────────┬───────────────────┘   │
                     │                  ▼                        │
                     │  ┌───────────────────────────────────┐   │
                     │  │          Policy Engine              │   │
                     │  │  resolve effective RoutingPolicy    │   │
                     │  └───────────────┬───────────────────┘   │
                     │                  ▼                        │
                     │  ┌───────────────────────────────────┐   │
                     │  │       Execution Planner             │   │
                     │  │  RequestAnalyzer (Future)           │   │
                     │  │  enumerate → score → rank           │   │
                     │  │  candidates via RoutingStrategy     │   │
                     │  └───────────────┬───────────────────┘   │
                     │                  ▼                        │
                     │  ┌───────────────────────────────────┐   │
                     │  │       Execution Runtime             │   │
                     │  │  invoke → retry/fallback →          │   │
                     │  │  circuit break → normalize          │   │
                     │  └───────────────┬───────────────────┘   │
                     │                  ▼                        │
                     │  ┌───────────────────────────────────┐   │
                     │  │      Provider Adapter Layer        │   │
                     │  │  (ports & adapters, one per        │   │
                     │  │   provider, SPI-versioned)          │   │
                     │  └───────────────┬───────────────────┘   │
                     │                  ▼                        │
                     │  ┌───────────────────────────────────┐   │
                     │  │  Cross-cutting: Cache, Tracing,     │   │
                     │  │  Metrics, FeedbackEvents,           │   │
                     │  │  Usage Events (Kafka)               │   │
                     │  └───────────────────────────────────┘   │
                     └───────────────────┬──────────────────────┘
                                          ▼
        ┌───────────┬───────────┬───────────┬───────────┬───────────┐
        │  OpenAI    │ Anthropic │  Gemini   │  Ollama   │  Baseten  │  ...
        └───────────┴───────────┴───────────┴───────────┴───────────┘
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full component breakdown, deployment topology, and module responsibilities, and [diagrams/](diagrams/) for detailed Mermaid diagrams.

## Tech Stack

| Layer | Choice |
|---|---|
| Language / Runtime | Java 21 (virtual threads, pattern matching) |
| Web Framework | Spring Boot 3, Spring WebFlux |
| Security | Spring Security (OAuth2 / API keys / mTLS) |
| Caching | Redis |
| Messaging / Events | Kafka |
| Persistence | PostgreSQL |
| Build | Gradle |
| Testing | JUnit 5, Testcontainers |
| Packaging | Docker |
| Orchestration | Kubernetes |
| Observability | OpenTelemetry, Prometheus, Grafana, Langfuse |

Full rationale for these choices is in [rfcs/RFC-001.md](rfcs/RFC-001.md).

## Repository Structure

```
ModelRouter/
├── README.md               ← you are here
├── ARCHITECTURE.md          ← system design and module breakdown
├── ROADMAP.md               ← phased delivery plan
├── DOMAIN_MODEL.md          ← canonical domain language and structural rules
├── GLOSSARY.md              ← locked terminology definitions
├── CONTRIBUTING.md          ← contribution guidelines
├── CODE_OF_CONDUCT.md       ← community standards
├── SECURITY.md              ← vulnerability reporting policy
├── LICENSE                  ← Apache 2.0
├── rfcs/
│   └── RFC-001.md           ← founding design proposal
├── docs/
│   ├── api.md               ← API design and error model
│   ├── routing.md           ← routing pipeline design
│   ├── providers.md         ← provider architecture
│   ├── provider-contract.md ← adapter SPI contract and versioning
│   ├── deployment.md        ← deployment model and topology
│   ├── observability.md     ← tracing, metrics, logging
│   ├── security.md          ← security design (authn/authz/privacy)
│   └── failure-modes.md     ← failure modes and degraded behavior
├── diagrams/
│   ├── component-diagram.mmd
│   ├── deployment-diagram.mmd
│   ├── package-structure.mmd
│   ├── provider-architecture.mmd
│   ├── routing-flow.mmd
│   └── sequence-diagram.mmd
└── .github/
    ├── ISSUE_TEMPLATE/
    │   ├── bug_report.md
    │   ├── feature_request.md
    │   └── design_review.md
    └── PULL_REQUEST_TEMPLATE.md
```

## Project Status

ModelRouter is currently in **design phase**. No implementation has begun. The priority is producing a design that a team of senior engineers would sign off on before writing a single line of production code. See:

- [ARCHITECTURE.md](ARCHITECTURE.md) — system design
- [rfcs/RFC-001.md](rfcs/RFC-001.md) — founding design proposal, alternatives, and tradeoffs
- [ROADMAP.md](ROADMAP.md) — phased delivery plan
- [GLOSSARY.md](GLOSSARY.md) — locked terminology
- [docs/](docs/) — subsystem-level design docs

## Future Roadmap (Summary)

- **Phase 1** — Core routing (policy engine, execution planner, execution runtime), provider adapters (OpenAI, Anthropic), synchronous + streaming completions, static routing policies, multi-provider fallback (`ExecutionPlan`), basic observability.
- **Phase 2** — Cost/latency-aware routing, Redis-backed caching, Kafka-based event pipeline, health checks, dashboard v1.
- **Phase 3** — Kubernetes-native operator, horizontal autoscaling, semantic caching, Langfuse integration, Android/Java SDKs GA.
- **Stretch** — `AdaptiveRoutingEngine` (Future) based on `FeedbackEvent`s, `ContextOptimizer` (Future), pluggable `RequestAnalyzer` (Future), multi-region active-active deployment.

Details in [ROADMAP.md](ROADMAP.md).

## Contributing

ModelRouter is pre-implementation. The most valuable contribution right now is design review — read [rfcs/RFC-001.md](rfcs/RFC-001.md) and open an issue or discussion if you disagree with a decision. See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License

[Apache 2.0](LICENSE).
