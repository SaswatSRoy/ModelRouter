package io.modelrouter.core.planner;

import io.modelrouter.core.domain.RoutingPolicy;
import io.modelrouter.core.domain.ScoredCandidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Routing strategy that selects the primary candidate using weighted round robin
 * based on candidate scores, placing it first, followed by other candidates in priority order.
 */
public class WeightedRoundRobinStrategy implements RoutingStrategy {

    private final AtomicLong counter = new AtomicLong(0);

    @Override
    public String id() {
        return WEIGHTED_ROUND_ROBIN;
    }

    @Override
    public List<ScoredCandidate> rank(RoutingPolicy policy, List<ScoredCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates cannot be null");
        if (candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() == 1) {
            return List.copyOf(candidates);
        }

        // Sort candidates by score descending for fallback order
        List<ScoredCandidate> sortedByScore = new ArrayList<>(candidates);
        sortedByScore.sort((c1, c2) -> Double.compare(c2.score(), c1.score()));

        // Calculate weights (using score * 100 to handle decimals, min weight 1)
        long totalWeight = 0;
        long[] weights = new long[sortedByScore.size()];
        for (int i = 0; i < sortedByScore.size(); i++) {
            long w = Math.max(1, Math.round(sortedByScore.get(i).score() * 100));
            weights[i] = w;
            totalWeight += w;
        }

        // Select the primary candidate based on the round robin counter
        long currentCount = counter.getAndIncrement();
        long target = (currentCount & Long.MAX_VALUE) % totalWeight;

        int selectedIndex = 0;
        long cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (target < cumulative) {
                selectedIndex = i;
                break;
            }
        }

        // Reorder list: selected candidate first, then all others in their priority order
        List<ScoredCandidate> result = new ArrayList<>();
        ScoredCandidate primary = sortedByScore.get(selectedIndex);
        result.add(primary);
        for (ScoredCandidate c : sortedByScore) {
            if (c != primary) {
                result.add(c);
            }
        }

        return result;
    }
}
