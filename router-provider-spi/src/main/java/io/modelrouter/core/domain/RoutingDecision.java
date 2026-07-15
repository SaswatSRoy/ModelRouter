package io.modelrouter.core.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Records the final routing decision for observability and tracing.
 *
 * <p>Captures which provider/model was selected, the full execution plan
 * that was evaluated, and the strategy that made the selection.
 *
 * @param selectedProvider the provider chosen for execution
 * @param modelId          the model chosen for execution
 * @param plan             the execution plan that was evaluated
 * @param strategyUsed     the identifier of the routing strategy that made the decision
 * @param decidedAt        the instant the decision was made
 */
public record RoutingDecision(
        ProviderId selectedProvider,
        String modelId,
        ExecutionPlan plan,
        String strategyUsed,
        Instant decidedAt
) {

    /**
     * Validates that required fields are non-null.
     */
    public RoutingDecision {
        Objects.requireNonNull(selectedProvider, "selectedProvider cannot be null");
        Objects.requireNonNull(modelId, "modelId cannot be null");
        Objects.requireNonNull(plan, "plan cannot be null");
        Objects.requireNonNull(strategyUsed, "strategyUsed cannot be null");
        Objects.requireNonNull(decidedAt, "decidedAt cannot be null");
    }
}
