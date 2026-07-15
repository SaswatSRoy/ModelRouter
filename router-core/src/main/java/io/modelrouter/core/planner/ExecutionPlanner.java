package io.modelrouter.core.planner;

import io.modelrouter.core.domain.ExecutionPlan;
import io.modelrouter.core.domain.InferenceRequest;
import io.modelrouter.core.domain.ProviderCandidate;
import io.modelrouter.core.domain.RoutingPolicy;
import io.modelrouter.core.domain.ScoredCandidate;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates the execution planning pipeline:
 * Filters structurally eligible candidates, scores them, ranks them, and returns an ExecutionPlan.
 */
public class ExecutionPlanner {

    private final CandidateEnumerator enumerator;
    private final ProviderScorer scorer;
    private final StrategyRegistry strategyRegistry;

    public ExecutionPlanner(CandidateEnumerator enumerator, ProviderScorer scorer, StrategyRegistry strategyRegistry) {
        this.enumerator = Objects.requireNonNull(enumerator, "enumerator cannot be null");
        this.scorer = Objects.requireNonNull(scorer, "scorer cannot be null");
        this.strategyRegistry = Objects.requireNonNull(strategyRegistry, "strategyRegistry cannot be null");
    }

    /**
     * Produces an ExecutionPlan for the given request and resolved policy.
     */
    public ExecutionPlan plan(InferenceRequest request, RoutingPolicy policy) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(policy, "policy cannot be null");

        List<ProviderCandidate> candidates = enumerator.enumerate(policy);
        if (candidates.isEmpty()) {
            throw new PolicyUnsatisfiableException(policy, "No registered provider candidates satisfy the routing policy constraints.");
        }

        List<ScoredCandidate> scored = scorer.score(candidates, policy);

        RoutingStrategy strategy = strategyRegistry.getStrategy(policy.strategyId());
        List<ScoredCandidate> ranked = strategy.rank(policy, scored);

        return new ExecutionPlan(ranked, policy, Instant.now());
    }
}
