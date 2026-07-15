package io.modelrouter.core.planner;

import io.modelrouter.core.domain.RoutingPolicy;
import io.modelrouter.core.domain.ScoredCandidate;
import java.util.List;

/**
 * Strategy interface for ranking scored candidates.
 */
public interface RoutingStrategy {

    String PRIORITY_FALLBACK = "priority-fallback";
    String WEIGHTED_ROUND_ROBIN = "weighted-round-robin";

    /**
     * Unique identifier for this strategy.
     */
    String id();

    /**
     * Ranks scored candidates according to policy requirements.
     * Must return a newly ordered list. Must be a pure function.
     */
    List<ScoredCandidate> rank(RoutingPolicy policy, List<ScoredCandidate> candidates);
}
