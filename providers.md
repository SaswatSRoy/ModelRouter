# Provider Architecture

See [diagrams/provider-architecture.mmd](../diagrams/provider-architecture.mmd).

## The Port: `InferenceProvider`

Every provider adapter implements a single, stable interface defined in `router-provider-spi`:

```java
public interface InferenceProvider {

    ProviderId id();

    Mono<InferenceResponse> invoke(InferenceRequest request);

    Flux<InferenceChunk> invokeStreaming(InferenceRequest request);

    ProviderCapabilities capabilities();     // context window, modalities, function calling, etc.

    Mono<HealthStatus> healthCheck();

    CostEstimate estimateCost(InferenceRequest request);
}
```

`router-core` depends only on this interface. It never imports a provider SDK, never branches on provider identity in business logic (`if provider == "openai"` is a code smell that should not appear outside an adapter module), and is fully testable with fake `InferenceProvider` implementations.

## Anatomy of an Adapter Module

Each `provider-*` module owns, and is solely responsible for:

- **SDK/HTTP client management** — connection pooling, provider-specific auth headers, base URL configuration.
- **Error mapping** — translating provider-specific error codes/formats into ModelRouter's normalized error taxonomy (see [docs/api.md](api.md)), including correctly classifying errors as retryable or not.
- **Rate-limit awareness** — parsing provider rate-limit headers (e.g., `x-ratelimit-remaining`) to feed `capacityHeadroom` into scoring, rather than discovering limits only via 429s.
- **Request/response shape translation** — mapping the canonical `InferenceRequest`/`InferenceResponse` to and from the provider's wire format.
- **Streaming protocol translation** — normalizing provider-specific streaming formats (OpenAI's SSE delta format, Anthropic's event types, etc.) into the canonical `InferenceChunk` stream.
- **Its own tests** — adapter tests run in isolation via Testcontainers/WireMock against recorded or mocked provider responses; they do not require booting the full router.

## Adding a New Provider

The explicit goal is that this is a same-day task for a competent contributor:

1. Create `provider-<name>` Gradle module, depend on `router-provider-spi`.
2. Implement `InferenceProvider`.
3. Register via Spring component scanning (v1 — static compilation, see RFC-001 §4.4).
4. Add a capability descriptor (context window, supported modalities, pricing) — either hardcoded initially or sourced from the provider's own model-listing API where available.
5. Add adapter-level tests.
6. No changes required to `router-core`, `router-ingress`, or any other adapter.

## Provider Capability Model

```java
record ProviderCapabilities(
    Set<Modality> modalities,          // TEXT, IMAGE, AUDIO
    int maxContextTokens,
    boolean supportsStreaming,
    boolean supportsFunctionCalling,
    boolean supportsSystemPrompt,
    Set<String> availableModels
) {}
```

Used by `CandidateEnumerator` (see [docs/routing.md](routing.md)) to filter out structurally ineligible providers before scoring ever runs — e.g., a request requiring function calling never reaches scoring for a provider/model that doesn't support it.

## Local vs. Cloud Providers

Local/self-hosted providers (Ollama, vLLM, Baseten self-hosted deployments) implement the exact same `InferenceProvider` port as cloud providers (OpenAI, Anthropic, Gemini, Groq, Fireworks, Together AI, Azure OpenAI). The only distinction the routing engine is aware of is a `privacyTier` capability flag (`LOCAL` vs. `CLOUD`), used by `PrivacyConstrainedStrategy`. This means "route sensitive data to on-prem inference" requires no special-casing anywhere in `router-core` — it's an ordinary policy constraint evaluated against an ordinary capability field.

## Provider-Specific Configuration vs. Code

Credentials, endpoint URLs, enabled/disabled state, and routing weight are **configuration**, stored in PostgreSQL, managed via the admin API, and hot-reloadable without a redeploy. Request/response translation logic and error mapping are **code**, shipped with each release. This split is deliberate (RFC-001 §4.4): it's what lets operators reconfigure providers instantly while keeping the actual integration logic under normal code review and release discipline.
