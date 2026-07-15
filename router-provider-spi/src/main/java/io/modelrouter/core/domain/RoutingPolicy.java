package io.modelrouter.core.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The resolved routing policy governing provider selection, cost limits,
 * privacy, and retry behavior for an inference request.
 *
 * <p>Policies are hierarchical. The {@link #merge(RoutingPolicy, RoutingPolicy)}
 * method implements tightening-only merge semantics: a lower-priority policy
 * can only make constraints <em>tighter</em>, never looser.
 *
 * @param maxCostUsd          maximum cost budget in USD (nullable = no limit)
 * @param maxLatencyMs        maximum acceptable latency in milliseconds (nullable = no limit)
 * @param privacyTier         the required privacy tier
 * @param requiredCapabilities capabilities the selected provider must support
 * @param preferredProviders  ordered list of preferred provider IDs
 * @param excludedProviders   providers excluded from selection (additive in merges)
 * @param retryPolicy         the retry policy for pre-first-byte retries
 * @param strategyId          the routing strategy identifier (nullable = use default)
 */
public record RoutingPolicy(
        Double maxCostUsd,
        Integer maxLatencyMs,
        PrivacyTier privacyTier,
        Set<String> requiredCapabilities,
        List<String> preferredProviders,
        Set<String> excludedProviders,
        RetryPolicy retryPolicy,
        String strategyId
) {

    /** Sensible default policy: cloud allowed, default retry, no cost/latency limit. */
    public static final RoutingPolicy DEFAULT = new RoutingPolicy(
            null,
            null,
            PrivacyTier.CLOUD_ALLOWED,
            Set.of(),
            List.of(),
            Set.of(),
            RetryPolicy.DEFAULT,
            null
    );

    /**
     * Validates and creates defensive copies of collection fields.
     */
    public RoutingPolicy {
        Objects.requireNonNull(privacyTier, "privacyTier cannot be null");
        Objects.requireNonNull(retryPolicy, "retryPolicy cannot be null");
        requiredCapabilities = requiredCapabilities != null
                ? Set.copyOf(requiredCapabilities)
                : Set.of();
        preferredProviders = preferredProviders != null
                ? List.copyOf(preferredProviders)
                : List.of();
        excludedProviders = excludedProviders != null
                ? Set.copyOf(excludedProviders)
                : Set.of();
    }

    /**
     * Merges two routing policies with tightening-only semantics.
     *
     * <p>The {@code higher} policy is the baseline (e.g. system or tenant-level).
     * The {@code lower} policy is a more specific override (e.g. per-request).
     * Fields in the result can only be tighter (more restrictive) than
     * the higher-priority policy:
     *
     * <ul>
     *   <li>{@code maxCostUsd}: uses the minimum of both (lower can only tighten)</li>
     *   <li>{@code maxLatencyMs}: uses the minimum of both</li>
     *   <li>{@code privacyTier}: can only tighten (LOCAL_ONLY wins)</li>
     *   <li>{@code requiredCapabilities}: union/additive</li>
     *   <li>{@code excludedProviders}: union/additive</li>
     *   <li>{@code preferredProviders}: lower overrides if non-empty, else higher</li>
     *   <li>{@code retryPolicy}: lower overrides if maxAttempts is tighter</li>
     *   <li>{@code strategyId}: lower overrides if non-null</li>
     * </ul>
     *
     * @param higher the higher-priority (broader scope) policy
     * @param lower  the lower-priority (narrower scope) override policy
     * @return the merged policy
     */
    public static RoutingPolicy merge(RoutingPolicy higher, RoutingPolicy lower) {
        Objects.requireNonNull(higher, "higher policy cannot be null");
        Objects.requireNonNull(lower, "lower policy cannot be null");

        Double mergedCost = tightenMin(higher.maxCostUsd, lower.maxCostUsd);

        Integer mergedLatency = tightenMinInt(higher.maxLatencyMs, lower.maxLatencyMs);

        PrivacyTier mergedPrivacy = higher.privacyTier.tighten(lower.privacyTier);

        Set<String> mergedCapabilities = new LinkedHashSet<>(higher.requiredCapabilities);
        mergedCapabilities.addAll(lower.requiredCapabilities);

        Set<String> mergedExcluded = new HashSet<>(higher.excludedProviders);
        mergedExcluded.addAll(lower.excludedProviders);

        List<String> mergedPreferred = lower.preferredProviders.isEmpty()
                ? higher.preferredProviders
                : lower.preferredProviders;

        RetryPolicy mergedRetry = lower.retryPolicy.maxAttempts() <= higher.retryPolicy.maxAttempts()
                ? lower.retryPolicy
                : higher.retryPolicy;

        String mergedStrategy = lower.strategyId != null
                ? lower.strategyId
                : higher.strategyId;

        return new RoutingPolicy(
                mergedCost,
                mergedLatency,
                mergedPrivacy,
                mergedCapabilities,
                new ArrayList<>(mergedPreferred),
                mergedExcluded,
                mergedRetry,
                mergedStrategy
        );
    }

    private static Double tightenMin(Double a, Double b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.min(a, b);
    }

    private static Integer tightenMinInt(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.min(a, b);
    }
}
