package io.modelrouter.core.policy;

import io.modelrouter.core.domain.PrivacyTier;
import io.modelrouter.core.domain.RequestPolicyOverride;
import io.modelrouter.core.domain.RetryPolicy;
import io.modelrouter.core.domain.RoutingPolicy;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

/**
 * Entry point for policy resolution in ModelRouter.
 * Resolves the effective RoutingPolicy for a given request using a hierarchical merge:
 * system default -> tenant default -> request override.
 */
public class PolicyResolver {

    private final PolicyStore policyStore;

    public PolicyResolver(PolicyStore policyStore) {
        this.policyStore = Objects.requireNonNull(policyStore, "policyStore cannot be null");
    }

    /**
     * Resolves the effective RoutingPolicy.
     *
     * @param tenantId        the tenant ID
     * @param requestOverride the per-request policy override
     * @return the resolved RoutingPolicy
     * @throws PolicyValidationException if the override attempts to relax a constraint
     */
    public RoutingPolicy resolve(String tenantId, RequestPolicyOverride requestOverride) {
        RoutingPolicy systemDefault = policyStore.getSystemDefault().orElse(RoutingPolicy.DEFAULT);

        RoutingPolicy current = policyStore.getTenantDefault(tenantId)
                .map(tenantPolicy -> RoutingPolicy.merge(systemDefault, tenantPolicy))
                .orElse(systemDefault);

        if (requestOverride != null) {
            // Check validation rules before merging (request cannot relax constraints)
            validateOverride(current, requestOverride);

            Double cost = requestOverride.maxCostUsd() != null ? requestOverride.maxCostUsd() : current.maxCostUsd();
            Integer latency = requestOverride.maxLatencyMs() != null ? requestOverride.maxLatencyMs() : current.maxLatencyMs();
            PrivacyTier privacy = requestOverride.privacyTier() != null ? requestOverride.privacyTier() : current.privacyTier();
            Set<String> capabilities = requestOverride.requiredCapabilities() != null ? requestOverride.requiredCapabilities() : current.requiredCapabilities();
            Set<String> excluded = requestOverride.excludedProviders() != null ? requestOverride.excludedProviders() : current.excludedProviders();

            RetryPolicy retry;
            if (requestOverride.maxRetryAttempts() != null) {
                retry = new RetryPolicy(
                        requestOverride.maxRetryAttempts(),
                        current.retryPolicy().initialBackoffMs(),
                        current.retryPolicy().backoffMultiplier(),
                        current.retryPolicy().maxBackoffMs()
                );
            } else {
                retry = current.retryPolicy();
            }

            String strategy = requestOverride.strategyId() != null ? requestOverride.strategyId() : current.strategyId();

            RoutingPolicy overridePolicy = new RoutingPolicy(
                    cost,
                    latency,
                    privacy,
                    capabilities,
                    requestOverride.preferredProviders() != null ? requestOverride.preferredProviders() : new ArrayList<>(),
                    excluded,
                    retry,
                    strategy
            );

            current = RoutingPolicy.merge(current, overridePolicy);
        }

        return current;
    }

    private void validateOverride(RoutingPolicy current, RequestPolicyOverride override) {
        if (current.maxCostUsd() != null && override.maxCostUsd() != null && override.maxCostUsd() > current.maxCostUsd()) {
            throw new PolicyValidationException("Request override cannot increase maxCostUsd limit (" 
                + override.maxCostUsd() + " > " + current.maxCostUsd() + ")");
        }

        if (current.maxLatencyMs() != null && override.maxLatencyMs() != null && override.maxLatencyMs() > current.maxLatencyMs()) {
            throw new PolicyValidationException("Request override cannot increase maxLatencyMs limit (" 
                + override.maxLatencyMs() + " > " + current.maxLatencyMs() + ")");
        }

        if (current.privacyTier() == PrivacyTier.LOCAL_ONLY && override.privacyTier() == PrivacyTier.CLOUD_ALLOWED) {
            throw new PolicyValidationException("Request override cannot relax privacyTier from LOCAL_ONLY to CLOUD_ALLOWED");
        }

        if (override.maxRetryAttempts() != null && override.maxRetryAttempts() > current.retryPolicy().maxAttempts()) {
            throw new PolicyValidationException("Request override cannot increase maxRetryAttempts beyond policy limit (" 
                + override.maxRetryAttempts() + " > " + current.retryPolicy().maxAttempts() + ")");
        }
    }
}
