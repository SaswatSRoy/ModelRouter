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

## Features

### Available in the design (see [ROADMAP.md](ROADMAP.md) for sequencing)

| Category | Capability |
|---|---|
| **Routing** | Multi-provider routing, dynamic provider selection, policy-driven routing engine |
| **Reliability** | Configurable retry strategies, automatic fallback chains, health checks, circuit breaking |
| **Performance** | Streaming-first design, prompt caching, semantic caching, latency-aware routing |
| **Cost & Privacy** | Cost-aware routing, privacy-aware routing, local-vs-cloud inference selection |
| **Extensibility** | Provider plugin architecture, pluggable routing strategies, future AI-driven routing engine |
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
                     │  │        Routing Engine (Core)       │   │
                     │  │  policy → strategy → scoring →     │   │
                     │  │  provider selection → execution    │   │
                     │  └───────────────┬───────────────────┘   │
                     │                  ▼                        │
                     │  ┌───────────────────────────────────┐   │
                     │  │      Provider Adapter Layer        │   │
                     │  │  (ports & adapters, one per        │   │
                     │  │   provider, hot-pluggable)          │   │
                     │  └───────────────┬───────────────────┘   │
                     │                  ▼                        │
                     │  ┌───────────────────────────────────┐   │
                     │  │  Cross-cutting: Cache, Retry,       │   │
                     │  │  Circuit Breaker, Tracing, Metrics  │   │
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

Full rationale for these choices is in [RFC-001.md](RFC-001.md).

## Project Status

ModelRouter is currently in **design phase**. No implementation has begun. The priority is producing a design that a team of senior engineers would sign off on before writing a single line of production code. See:

- [ARCHITECTURE.md](ARCHITECTURE.md) — system design
- [RFC-001.md](RFC-001.md) — founding design proposal, alternatives, and tradeoffs
- [ROADMAP.md](ROADMAP.md) — phased delivery plan
- [docs/](docs/) — subsystem-level design docs

## Future Roadmap (Summary)

- **Phase 1** — Core routing engine, single-provider adapters (OpenAI, Anthropic), synchronous + streaming completions, static routing policies, basic observability.
- **Phase 2** — Multi-provider fallback chains, cost/latency-aware routing, Redis-backed caching, Kafka-based event pipeline, health checks, dashboard v1.
- **Phase 3** — Kubernetes-native operator, horizontal autoscaling, semantic caching, Langfuse integration, Android/Java SDKs GA.
- **Stretch** — AI-powered adaptive routing engine, multi-region active-active deployment, policy marketplace.

Details in [ROADMAP.md](ROADMAP.md).

## Contributing

ModelRouter is pre-implementation. The most valuable contribution right now is design review — read [RFC-001.md](RFC-001.md) and open an issue or discussion if you disagree with a decision. Once Phase 1 implementation begins, `CONTRIBUTING.md` will define code standards, review process, and provider-plugin authoring guidelines.

## License

Apache 2.0 (proposed — see [RFC-001.md](RFC-001.md) for rationale).
