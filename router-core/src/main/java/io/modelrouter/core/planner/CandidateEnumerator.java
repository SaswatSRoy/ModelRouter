package io.modelrouter.core.planner;

import io.modelrouter.core.domain.ProviderCandidate;
import io.modelrouter.core.domain.ProviderCapabilities;
import io.modelrouter.core.domain.RoutingPolicy;
import io.modelrouter.provider.spi.InferenceProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Enumerates all registered providers and filters them to structurally eligible candidates.
 */
public class CandidateEnumerator {

    private final ProviderRegistry providerRegistry;

    public CandidateEnumerator(ProviderRegistry providerRegistry) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry cannot be null");
    }

    /**
     * Enumerates eligible candidates based on policy constraints.
     */
    public List<ProviderCandidate> enumerate(RoutingPolicy policy) {
        Objects.requireNonNull(policy, "policy cannot be null");
        List<ProviderCandidate> candidates = new ArrayList<>();

        for (InferenceProvider provider : providerRegistry.getAllProviders()) {
            // Check hard exclusions
            if (policy.excludedProviders().contains(provider.id().value())) {
                continue;
            }

            ProviderCapabilities caps = provider.capabilities();

            // Check privacy tier
            if (!caps.privacyTier().canSatisfy(policy.privacyTier())) {
                continue;
            }

            // Check required capabilities
            if (!caps.supportsAll(policy.requiredCapabilities())) {
                continue;
            }

            // Create a candidate for each available model
            for (String modelId : caps.availableModels()) {
                candidates.add(new ProviderCandidate(provider.id(), modelId, caps));
            }
        }

        return candidates;
    }
}
