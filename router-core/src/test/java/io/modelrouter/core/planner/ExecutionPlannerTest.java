package io.modelrouter.core.planner;

import static org.junit.jupiter.api.Assertions.*;

import io.modelrouter.core.domain.*;
import io.modelrouter.provider.spi.InferenceProvider;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ExecutionPlannerTest {

    private InMemoryProviderRegistry registry;
    private CandidateEnumerator enumerator;
    private ProviderScorer scorer;
    private StrategyRegistry strategyRegistry;
    private ExecutionPlanner planner;

    @BeforeEach
    void setUp() {
        registry = new InMemoryProviderRegistry();
        enumerator = new CandidateEnumerator(registry);
        scorer = new ProviderScorer();
        strategyRegistry = new StrategyRegistry();
        planner = new ExecutionPlanner(enumerator, scorer, strategyRegistry);
    }

    @Test
    void testFullPipelineSuccessful() {
        TestProvider p1 = new TestProvider("openai", PrivacyTier.CLOUD_ALLOWED, Set.of("chat"), List.of("gpt-4"));
        TestProvider p2 = new TestProvider("anthropic", PrivacyTier.CLOUD_ALLOWED, Set.of("chat", "vision"), List.of("claude"));
        registry.register(p1);
        registry.register(p2);

        RoutingPolicy policy = new RoutingPolicy(
                null, null, PrivacyTier.CLOUD_ALLOWED, Set.of("chat"), List.of("anthropic", "openai"), Set.of(), RetryPolicy.DEFAULT, "priority-fallback"
        );

        InferenceRequest req = InferenceRequest.builder()
                .tenantId("tenant-1")
                .messages(List.of(new Message("user", "hi")))
                .build();

        ExecutionPlan plan = planner.plan(req, policy);

        assertNotNull(plan);
        assertEquals(2, plan.candidateCount());
        // Since anthropic is preferred first, it should be ranked first
        assertEquals(new ProviderId("anthropic"), plan.candidates().get(0).providerId());
        assertEquals("claude", plan.candidates().get(0).modelId());

        assertEquals(new ProviderId("openai"), plan.candidates().get(1).providerId());
        assertEquals("gpt-4", plan.candidates().get(1).modelId());
    }

    @Test
    void testPolicyUnsatisfiableExclusion() {
        TestProvider p1 = new TestProvider("openai", PrivacyTier.CLOUD_ALLOWED, Set.of("chat"), List.of("gpt-4"));
        registry.register(p1);

        RoutingPolicy policy = new RoutingPolicy(
                null, null, PrivacyTier.CLOUD_ALLOWED, Set.of("chat"), List.of(), Set.of("openai"), RetryPolicy.DEFAULT, "priority-fallback"
        );

        InferenceRequest req = InferenceRequest.builder()
                .tenantId("tenant-1")
                .messages(List.of(new Message("user", "hi")))
                .build();

        assertThrows(PolicyUnsatisfiableException.class, () -> planner.plan(req, policy));
    }

    @Test
    void testPolicyUnsatisfiableCapabilities() {
        TestProvider p1 = new TestProvider("openai", PrivacyTier.CLOUD_ALLOWED, Set.of("chat"), List.of("gpt-4"));
        registry.register(p1);

        RoutingPolicy policy = new RoutingPolicy(
                null, null, PrivacyTier.CLOUD_ALLOWED, Set.of("vision"), List.of(), Set.of(), RetryPolicy.DEFAULT, "priority-fallback"
        );

        InferenceRequest req = InferenceRequest.builder()
                .tenantId("tenant-1")
                .messages(List.of(new Message("user", "hi")))
                .build();

        assertThrows(PolicyUnsatisfiableException.class, () -> planner.plan(req, policy));
    }

    @Test
    void testPolicyUnsatisfiablePrivacy() {
        TestProvider p1 = new TestProvider("openai", PrivacyTier.CLOUD_ALLOWED, Set.of("chat"), List.of("gpt-4"));
        registry.register(p1);

        RoutingPolicy policy = new RoutingPolicy(
                null, null, PrivacyTier.LOCAL_ONLY, Set.of("chat"), List.of(), Set.of(), RetryPolicy.DEFAULT, "priority-fallback"
        );

        InferenceRequest req = InferenceRequest.builder()
                .tenantId("tenant-1")
                .messages(List.of(new Message("user", "hi")))
                .build();

        assertThrows(PolicyUnsatisfiableException.class, () -> planner.plan(req, policy));
    }

    private static class TestProvider implements InferenceProvider {
        private final ProviderId id;
        private final ProviderCapabilities capabilities;

        TestProvider(String id, PrivacyTier privacy, Set<String> caps, List<String> models) {
            this.id = new ProviderId(id);
            this.capabilities = new ProviderCapabilities(this.id, caps, models, privacy, 4096);
        }

        @Override
        public ProviderId id() {
            return id;
        }

        @Override
        public Mono<InferenceResponse> invoke(InferenceRequest request) {
            return Mono.empty();
        }

        @Override
        public Flux<InferenceChunk> invokeStreaming(InferenceRequest request) {
            return Flux.empty();
        }

        @Override
        public ProviderCapabilities capabilities() {
            return capabilities;
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
