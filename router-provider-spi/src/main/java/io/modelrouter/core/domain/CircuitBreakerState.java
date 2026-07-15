package io.modelrouter.core.domain;

/**
 * Represents the state of a circuit breaker protecting a provider.
 *
 * <p>State transitions follow the standard pattern:
 * {@code CLOSED → OPEN → HALF_OPEN → CLOSED}.
 *
 * <ul>
 *   <li>{@link #CLOSED} – normal operation, requests flow through</li>
 *   <li>{@link #OPEN} – failures exceeded threshold, requests are blocked</li>
 *   <li>{@link #HALF_OPEN} – trial period allowing limited requests to probe recovery</li>
 * </ul>
 */
public enum CircuitBreakerState {

    /** Normal operation; all requests flow through to the provider. */
    CLOSED,

    /** Circuit tripped; requests are rejected immediately without calling the provider. */
    OPEN,

    /** Probing state; a limited number of requests are allowed to test provider recovery. */
    HALF_OPEN
}
