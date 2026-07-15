package io.modelrouter.core.planner;

import io.modelrouter.core.domain.RoutingPolicy;

/**
 * Thrown when no registered provider candidates can satisfy the criteria in the routing policy.
 */
public class PolicyUnsatisfiableException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final transient RoutingPolicy policy;

    public PolicyUnsatisfiableException(RoutingPolicy policy, String reason) {
        super(reason);
        this.policy = policy;
    }

    public RoutingPolicy getPolicy() {
        return policy;
    }
}
