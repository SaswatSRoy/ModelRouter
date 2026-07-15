package io.modelrouter.core.domain;

import java.util.Set;
import java.util.List;

/**
 * Per-request policy overrides that allow callers to customize routing
 * behavior on individual inference requests.
 *
 * <p>All fields are nullable. A {@code null} field means "no override;
 * use the default from the resolved {@link RoutingPolicy}."
 *
 * @param maxCostUsd          maximum cost budget in USD for this request
 * @param maxLatencyMs        maximum acceptable latency in milliseconds
 * @param privacyTier         privacy tier override (can only tighten)
 * @param requiredCapabilities capabilities the provider must support
 * @param preferredProviders  ordered list of preferred provider IDs
 * @param excludedProviders   providers to exclude (additive with policy)
 * @param maxRetryAttempts    override for the maximum retry attempts
 * @param strategyId          override for the routing strategy identifier
 */
public record RequestPolicyOverride(
        Double maxCostUsd,
        Integer maxLatencyMs,
        PrivacyTier privacyTier,
        Set<String> requiredCapabilities,
        List<String> preferredProviders,
        Set<String> excludedProviders,
        Integer maxRetryAttempts,
        String strategyId
) {
}
