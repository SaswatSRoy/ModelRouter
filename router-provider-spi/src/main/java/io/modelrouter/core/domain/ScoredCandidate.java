package io.modelrouter.core.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A candidate provider/model pair scored by the execution planner,
 * with a detailed breakdown of how the score was computed.
 *
 * @param providerId     the candidate provider
 * @param modelId        the candidate model
 * @param capabilities   the provider's full capabilities
 * @param score          the composite score (higher is better)
 * @param scoreBreakdown per-dimension score breakdown (e.g. "cost" → 0.8, "latency" → 0.9)
 */
public record ScoredCandidate(
        ProviderId providerId,
        String modelId,
        ProviderCapabilities capabilities,
        double score,
        Map<String, Double> scoreBreakdown
) {

    /**
     * Validates required fields and creates a defensive copy of the breakdown map.
     */
    public ScoredCandidate {
        Objects.requireNonNull(providerId, "providerId cannot be null");
        Objects.requireNonNull(modelId, "modelId cannot be null");
        Objects.requireNonNull(capabilities, "capabilities cannot be null");
        scoreBreakdown = scoreBreakdown != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(scoreBreakdown))
                : Map.of();
    }
}
