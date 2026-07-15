package io.modelrouter.core.execution;

import static org.junit.jupiter.api.Assertions.*;

import io.modelrouter.core.domain.*;
import io.modelrouter.core.planner.InMemoryProviderRegistry;
import io.modelrouter.provider.spi.InferenceProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ExecutionEngineTest {

    private InMemoryProviderRegistry providerRegistry;
    private CircuitBreakerRegistry cbRegistry;
    private ExecutionEngine engine;

    @BeforeEach
    void setUp() {
        providerRegistry = new InMemoryProviderRegistry();
        cbRegistry = new CircuitBreakerRegistry();
        engine = new ExecutionEngine(providerRegistry, cbRegistry);
    }

    @Test
    void testSuccessfulExecution() {
        TestProvider p1 = new TestProvider("openai", "gpt-4", false, ErrorCategory.UNKNOWN, 0);
        providerRegistry.register(p1);

        ExecutionPlan plan = createPlan("openai", "gpt-4");
        InferenceRequest req = createRequest();

        StepVerifier.create(engine.execute(req, plan))
                .assertNext(res -> {
                    assertEquals("openai", res.routing().providerId().value());
                    assertEquals("gpt-4", res.routing().modelId());
                    assertEquals(1, res.routing().attempt());
                    assertEquals("hello", res.content());
                })
                .verifyComplete();
    }

    @Test
    void testFallbackOnFailure() {
        TestProvider p1 = new TestProvider("openai", "gpt-4", true, ErrorCategory.RETRYABLE_SERVER_ERROR, 5); // Fails
        TestProvider p2 = new TestProvider("anthropic", "claude", false, ErrorCategory.UNKNOWN, 0); // Succeeds
        providerRegistry.register(p1);
        providerRegistry.register(p2);

        ExecutionPlan plan = createPlan("openai", "gpt-4", "anthropic", "claude");
        InferenceRequest req = createRequest();

        StepVerifier.create(engine.execute(req, plan))
                .assertNext(res -> {
                    assertEquals("anthropic", res.routing().providerId().value());
                    assertEquals("claude", res.routing().modelId());
                    assertEquals("hello", res.content());
                })
                .verifyComplete();
    }

    @Test
    void testRetryBeforeFallback() {
        // Fails once with retryable server error, then succeeds
        TestProvider p1 = new TestProvider("openai", "gpt-4", true, ErrorCategory.RETRYABLE_SERVER_ERROR, 1);
        providerRegistry.register(p1);

        ExecutionPlan plan = createPlan("openai", "gpt-4");
        InferenceRequest req = createRequest();

        StepVerifier.create(engine.execute(req, plan))
                .assertNext(res -> {
                    assertEquals("openai", res.routing().providerId().value());
                    assertEquals(2, res.routing().attempt());
                    assertEquals("hello", res.content());
                })
                .verifyComplete();
    }

    @Test
    void testNonRetryableSkipsImmediately() {
        // Fails with non-retryable error, should fallback immediately without retry
        TestProvider p1 = new TestProvider("openai", "gpt-4", true, ErrorCategory.NON_RETRYABLE_CLIENT_ERROR, 5); // retryable logic would wait but it's non-retryable
        TestProvider p2 = new TestProvider("anthropic", "claude", false, ErrorCategory.UNKNOWN, 0);
        providerRegistry.register(p1);
        providerRegistry.register(p2);

        ExecutionPlan plan = createPlan("openai", "gpt-4", "anthropic", "claude");
        InferenceRequest req = createRequest();

        StepVerifier.create(engine.execute(req, plan))
                .assertNext(res -> {
                    assertEquals("anthropic", res.routing().providerId().value());
                    assertEquals("claude", res.routing().modelId());
                })
                .verifyComplete();
        
        assertEquals(1, p1.invocations.get()); // Only 1 attempt
    }

    @Test
    void testAllCandidatesExhausted() {
        TestProvider p1 = new TestProvider("openai", "gpt-4", true, ErrorCategory.RETRYABLE_SERVER_ERROR, 5);
        TestProvider p2 = new TestProvider("anthropic", "claude", true, ErrorCategory.NON_RETRYABLE_CLIENT_ERROR, 5);
        providerRegistry.register(p1);
        providerRegistry.register(p2);

        ExecutionPlan plan = createPlan("openai", "gpt-4", "anthropic", "claude");
        InferenceRequest req = createRequest();

        StepVerifier.create(engine.execute(req, plan))
                .expectError(CandidateExhaustedException.class)
                .verify();
    }

    @Test
    void testCircuitBreakerOpenSkips() {
        TestProvider p1 = new TestProvider("openai", "gpt-4", false, ErrorCategory.UNKNOWN, 0);
        TestProvider p2 = new TestProvider("anthropic", "claude", false, ErrorCategory.UNKNOWN, 0);
        providerRegistry.register(p1);
        providerRegistry.register(p2);

        // Manually trip the circuit breaker for openai
        CircuitBreaker cb = cbRegistry.getOrCreate(new ProviderId("openai"), "gpt-4");
        for (int i = 0; i < 6; i++) {
            cb.recordFailure();
        }
        assertEquals(CircuitBreakerState.OPEN, cb.getState());

        ExecutionPlan plan = createPlan("openai", "gpt-4", "anthropic", "claude");
        InferenceRequest req = createRequest();

        StepVerifier.create(engine.execute(req, plan))
                .assertNext(res -> {
                    assertEquals("anthropic", res.routing().providerId().value()); // Skip openai
                })
                .verifyComplete();
    }

    private InferenceRequest createRequest() {
        return InferenceRequest.builder()
                .tenantId("tenant-1")
                .messages(List.of(new Message("user", "test")))
                .build();
    }

    private ExecutionPlan createPlan(String... providerModelPairs) {
        List<ScoredCandidate> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < providerModelPairs.length; i += 2) {
            ProviderId providerId = new ProviderId(providerModelPairs[i]);
            String modelId = providerModelPairs[i + 1];
            ProviderCapabilities caps = new ProviderCapabilities(providerId, Set.of("chat"), List.of(modelId), PrivacyTier.CLOUD_ALLOWED, 4096);
            candidates.add(new ScoredCandidate(providerId, modelId, caps, 1.0, Map.of()));
        }
        return new ExecutionPlan(candidates, RoutingPolicy.DEFAULT, Instant.now());
    }

    private static class TestProvider implements InferenceProvider {
        private final ProviderId id;
        private final String modelId;
        private final boolean fail;
        private final ErrorCategory failCategory;
        private final int failAttempts;
        private final AtomicInteger invocations = new AtomicInteger(0);

        TestProvider(String id, String modelId, boolean fail, ErrorCategory failCategory, int failAttempts) {
            this.id = new ProviderId(id);
            this.modelId = modelId;
            this.fail = fail;
            this.failCategory = failCategory;
            this.failAttempts = failAttempts;
        }

        @Override
        public ProviderId id() {
            return id;
        }

        @Override
        public Mono<InferenceResponse> invoke(InferenceRequest request) {
            int current = invocations.incrementAndGet();
            if (fail && current <= failAttempts) {
                return Mono.error(new ProviderException(failCategory, id, 500, "Simulated error"));
            }
            return Mono.just(new InferenceResponse("res-1", "hello", "stop", new TokenUsage(10, 10, 0.0), new RoutingInfo(id, modelId, current, 10)));
        }

        @Override
        public Flux<InferenceChunk> invokeStreaming(InferenceRequest request) {
            return Flux.empty();
        }

        @Override
        public ProviderCapabilities capabilities() {
            return new ProviderCapabilities(id, Set.of("chat"), List.of(modelId), PrivacyTier.CLOUD_ALLOWED, 4096);
        }

        @Override
        public Mono<HealthStatus> healthCheck() {
            return Mono.just(HealthStatus.UP);
        }

        @Override
        public CostEstimate estimateCost(InferenceRequest request) {
            return CostEstimate.UNKNOWN;
        }
    }
}
