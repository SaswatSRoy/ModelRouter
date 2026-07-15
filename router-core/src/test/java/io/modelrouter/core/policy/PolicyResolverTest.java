package io.modelrouter.core.policy;

import static org.junit.jupiter.api.Assertions.*;

import io.modelrouter.core.domain.PrivacyTier;
import io.modelrouter.core.domain.RequestPolicyOverride;
import io.modelrouter.core.domain.RetryPolicy;
import io.modelrouter.core.domain.RoutingPolicy;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolicyResolverTest {

    private InMemoryPolicyStore policyStore;
    private PolicyResolver resolver;

    @BeforeEach
    void setUp() {
        policyStore = new InMemoryPolicyStore();
        resolver = new PolicyResolver(policyStore);
    }

    @Test
    void testSystemDefaultFallback() {
        RoutingPolicy systemPolicy = new RoutingPolicy(
                0.5, 1000, PrivacyTier.CLOUD_ALLOWED, Set.of("chat"), List.of("openai"), Set.of("fake"), RetryPolicy.DEFAULT, "strategy-1"
        );
        policyStore.setSystemDefault(systemPolicy);

        RoutingPolicy resolved = resolver.resolve("tenant-1", null);
        assertEquals(systemPolicy, resolved);
    }

    @Test
    void testTenantDefaultOverridesSystemDefault() {
        RoutingPolicy systemPolicy = new RoutingPolicy(
                0.5, 1000, PrivacyTier.CLOUD_ALLOWED, Set.of("chat"), List.of("openai"), Set.of("fake"), RetryPolicy.DEFAULT, "strategy-1"
        );
        policyStore.setSystemDefault(systemPolicy);

        RoutingPolicy tenantPolicy = new RoutingPolicy(
                0.3, 800, PrivacyTier.CLOUD_ALLOWED, Set.of("vision"), List.of("anthropic"), Set.of("bad-provider"), RetryPolicy.DEFAULT, "strategy-2"
        );
        policyStore.setTenantDefault("tenant-1", tenantPolicy);

        RoutingPolicy resolved = resolver.resolve("tenant-1", null);

        assertEquals(0.3, resolved.maxCostUsd());
        assertEquals(800, resolved.maxLatencyMs());
        assertTrue(resolved.requiredCapabilities().containsAll(Set.of("chat", "vision")));
        assertEquals(List.of("anthropic"), resolved.preferredProviders()); // Tenant preferred overrides system
        assertTrue(resolved.excludedProviders().containsAll(Set.of("fake", "bad-provider"))); // Excluded is union
        assertEquals("strategy-2", resolved.strategyId());
    }

    @Test
    void testRequestOverrideTightens() {
        RoutingPolicy tenantPolicy = new RoutingPolicy(
                0.3, 800, PrivacyTier.CLOUD_ALLOWED, Set.of("chat"), List.of("openai"), Set.of("fake"), RetryPolicy.DEFAULT, "strategy-1"
        );
        policyStore.setTenantDefault("tenant-1", tenantPolicy);

        RequestPolicyOverride override = new RequestPolicyOverride(
                0.2, 500, PrivacyTier.LOCAL_ONLY, Set.of("vision"), List.of("anthropic"), Set.of("bad-provider"), 1, "strategy-2"
        );

        RoutingPolicy resolved = resolver.resolve("tenant-1", override);

        assertEquals(0.2, resolved.maxCostUsd());
        assertEquals(500, resolved.maxLatencyMs());
        assertEquals(PrivacyTier.LOCAL_ONLY, resolved.privacyTier());
        assertTrue(resolved.requiredCapabilities().containsAll(Set.of("chat", "vision")));
        assertEquals(List.of("anthropic"), resolved.preferredProviders());
        assertTrue(resolved.excludedProviders().containsAll(Set.of("fake", "bad-provider")));
        assertEquals(1, resolved.retryPolicy().maxAttempts());
        assertEquals("strategy-2", resolved.strategyId());
    }

    @Test
    void testCannotRelaxPrivacyTier() {
        RoutingPolicy tenantPolicy = new RoutingPolicy(
                0.3, 800, PrivacyTier.LOCAL_ONLY, Set.of(), List.of(), Set.of(), RetryPolicy.DEFAULT, null
        );
        policyStore.setTenantDefault("tenant-1", tenantPolicy);

        RequestPolicyOverride override = new RequestPolicyOverride(
                null, null, PrivacyTier.CLOUD_ALLOWED, null, null, null, null, null
        );

        assertThrows(PolicyValidationException.class, () -> resolver.resolve("tenant-1", override));
    }

    @Test
    void testCannotRelaxCostOrLatency() {
        RoutingPolicy tenantPolicy = new RoutingPolicy(
                0.3, 800, PrivacyTier.CLOUD_ALLOWED, Set.of(), List.of(), Set.of(), RetryPolicy.DEFAULT, null
        );
        policyStore.setTenantDefault("tenant-1", tenantPolicy);

        // Cost relax
        RequestPolicyOverride overrideCost = new RequestPolicyOverride(
                0.4, null, null, null, null, null, null, null
        );
        assertThrows(PolicyValidationException.class, () -> resolver.resolve("tenant-1", overrideCost));

        // Latency relax
        RequestPolicyOverride overrideLatency = new RequestPolicyOverride(
                null, 900, null, null, null, null, null, null
        );
        assertThrows(PolicyValidationException.class, () -> resolver.resolve("tenant-1", overrideLatency));
    }

    @Test
    void testCannotRelaxRetries() {
        RoutingPolicy tenantPolicy = new RoutingPolicy(
                null, null, PrivacyTier.CLOUD_ALLOWED, Set.of(), List.of(), Set.of(), new RetryPolicy(2, 200, 2.0, 5000), null
        );
        policyStore.setTenantDefault("tenant-1", tenantPolicy);

        RequestPolicyOverride override = new RequestPolicyOverride(
                null, null, null, null, null, null, 3, null
        );
        assertThrows(PolicyValidationException.class, () -> resolver.resolve("tenant-1", override));
    }

    @Test
    void testNullOverrideHandledGracefully() {
        RoutingPolicy tenantPolicy = new RoutingPolicy(
                0.3, 800, PrivacyTier.CLOUD_ALLOWED, Set.of(), List.of(), Set.of(), RetryPolicy.DEFAULT, null
        );
        policyStore.setTenantDefault("tenant-1", tenantPolicy);

        RoutingPolicy resolved = resolver.resolve("tenant-1", null);
        assertEquals(tenantPolicy, resolved);
    }
}
