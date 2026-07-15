package io.modelrouter.core.planner;

import io.modelrouter.core.domain.ProviderCandidate;
import io.modelrouter.core.domain.RoutingPolicy;
import io.modelrouter.core.domain.ScoredCandidate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Attaches scores to eligible candidates.
 * In Phase 1 MVP, this implements a static scoring heuristic based on policy preferences.
 */
public class ProviderScorer {

    /**
     * Attaches scores to eligible candidates based on the policy preference list.
     */
    public List<ScoredCandidate> score(List<ProviderCandidate> candidates, RoutingPolicy policy) {
        Objects.requireNonNull(candidates, "candidates cannot be null");
        Objects.requireNonNull(policy, "policy cannot be null");
        List<ScoredCandidate> scored = new ArrayList<>();

        for (ProviderCandidate candidate : candidates) {
            Map<String, Double> breakdown = new LinkedHashMap<>();
            double baseScore = 1.0;
            breakdown.put("baseScore", baseScore);

            double preferredBonus = 0.0;
            List<String> preferred = policy.preferredProviders();
            if (preferred != null) {
                int index = preferred.indexOf(candidate.providerId().value());
                if (index != -1) {
                    // Lower index in preferred list means higher preference, so higher bonus
                    preferredBonus = 100.0 - index;
                }
            }
            breakdown.put("preferredBonus", preferredBonus);

            double totalScore = baseScore + preferredBonus;

            scored.add(new ScoredCandidate(
                    candidate.providerId(),
                    candidate.modelId(),
                    candidate.capabilities(),
                    totalScore,
                    breakdown
            ));
        }

        return scored;
    }
}
