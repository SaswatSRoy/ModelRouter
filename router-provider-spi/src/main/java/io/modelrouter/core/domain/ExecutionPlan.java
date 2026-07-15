package io.modelrouter.core.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable execution plan produced by the execution planner,
 * containing a ranked list of scored provider/model candidates
 * and the routing policy that was used.
 *
 * <p>The candidate list is an unmodifiable defensive copy. This record
 * is fully immutable and safe to share across threads.
 *
 * @param candidates the ranked list of scored candidates (best first)
 * @param policy     the routing policy used to produce this plan
 * @param createdAt  the instant this plan was created
 */
public record ExecutionPlan(
        List<ScoredCandidate> candidates,
        RoutingPolicy policy,
        Instant createdAt
) {

    /**
     * Validates and creates an unmodifiable defensive copy of the candidate list.
     */
    public ExecutionPlan {
        Objects.requireNonNull(policy, "policy cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        candidates = candidates != null
                ? List.copyOf(candidates)
                : List.of();
    }

    /**
     * Returns the top-ranked candidate, if any.
     *
     * @return an {@link Optional} containing the highest-scored candidate,
     *         or empty if there are no candidates
     */
    public Optional<ScoredCandidate> topCandidate() {
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.getFirst());
    }

    /**
     * Returns the number of candidates in this plan.
     *
     * @return the candidate count
     */
    public int candidateCount() {
        return candidates.size();
    }

    /**
     * Returns the candidate at the given index.
     *
     * @param index zero-based index
     * @return the scored candidate at the specified position
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public ScoredCandidate candidateAt(int index) {
        return candidates.get(index);
    }
}
