package io.modelrouter.core.execution;

/**
 * Configuration for a CircuitBreaker.
 */
public record CircuitBreakerConfig(
    int failureThreshold,
    long cooldownMs,
    int halfOpenMaxAttempts
) {
    public static final CircuitBreakerConfig DEFAULT = new CircuitBreakerConfig(5, 30000, 3);
}
