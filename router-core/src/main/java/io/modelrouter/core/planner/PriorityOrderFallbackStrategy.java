package io.modelrouter.core.planner;

import io.modelrouter.core.domain.RoutingPolicy;
import io.modelrouter.core.domain.ScoredCandidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Default routing strategy that ranks candidates by score descending.
 */
public class PriorityOrderFallbackStrategy implements RoutingStrategy {

    @Override
    public String id() {
        return PRIORITY_FALLBACK;
    }

    @Override
    public List<ScoredCandidate> rank(RoutingPolicy policy, List<ScoredCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates cannot be null");
        List<ScoredCandidate> ranked = new ArrayList<>(candidates);
        // Sort by score descending
        ranked.sort((c1, c2) -> Double.compare(c2.score(), c1.score()));
        return ranked;
    }
}
