package io.modelrouter.core.domain;

/**
 * Represents the health status of an inference provider.
 *
 * <ul>
 *   <li>{@link #UP} – provider is fully operational</li>
 *   <li>{@link #DEGRADED} – provider is operational but experiencing issues</li>
 *   <li>{@link #DOWN} – provider is unreachable or non-functional</li>
 * </ul>
 */
public enum HealthStatus {
    UP,
    DOWN,
    DEGRADED
}
