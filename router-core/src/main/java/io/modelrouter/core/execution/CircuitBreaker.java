package io.modelrouter.core.execution;

import io.modelrouter.core.domain.CircuitBreakerState;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe CircuitBreaker protecting a specific provider/model target.
 */
public class CircuitBreaker {

    private final CircuitBreakerConfig config;
    private final AtomicReference<CircuitBreakerState> state = new AtomicReference<>(CircuitBreakerState.CLOSED);
    
    // Closed state counters
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    
    // Open state timestamps
    private final AtomicLong openSince = new AtomicLong(0);

    // Half-open state counters
    private final AtomicInteger activeHalfOpenRequests = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccesses = new AtomicInteger(0);

    public CircuitBreaker(CircuitBreakerConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public CircuitBreakerState getState() {
        checkCooldown();
        return state.get();
    }

    /**
     * Determines whether a request is allowed to proceed.
     */
    public synchronized boolean allowRequest() {
        checkCooldown();
        CircuitBreakerState current = state.get();
        if (current == CircuitBreakerState.CLOSED) {
            return true;
        }
        if (current == CircuitBreakerState.HALF_OPEN) {
            if (activeHalfOpenRequests.get() < config.halfOpenMaxAttempts()) {
                activeHalfOpenRequests.incrementAndGet();
                return true;
            }
            return false;
        }
        // OPEN
        return false;
    }

    /**
     * Records a successful execution.
     */
    public synchronized void recordSuccess() {
        CircuitBreakerState current = state.get();
        if (current == CircuitBreakerState.CLOSED) {
            consecutiveFailures.set(0);
        } else if (current == CircuitBreakerState.HALF_OPEN) {
            activeHalfOpenRequests.decrementAndGet();
            if (halfOpenSuccesses.incrementAndGet() >= config.halfOpenMaxAttempts()) {
                state.set(CircuitBreakerState.CLOSED);
                consecutiveFailures.set(0);
                resetHalfOpenCounters();
            }
        }
    }

    /**
     * Records a failed execution.
     */
    public synchronized void recordFailure() {
        CircuitBreakerState current = state.get();
        if (current == CircuitBreakerState.CLOSED) {
            if (consecutiveFailures.incrementAndGet() >= config.failureThreshold()) {
                transitionToOpen();
            }
        } else if (current == CircuitBreakerState.HALF_OPEN) {
            transitionToOpen();
        }
    }

    private void transitionToOpen() {
        state.set(CircuitBreakerState.OPEN);
        openSince.set(System.currentTimeMillis());
        resetHalfOpenCounters();
    }

    private void resetHalfOpenCounters() {
        activeHalfOpenRequests.set(0);
        halfOpenSuccesses.set(0);
    }

    private void checkCooldown() {
        if (state.get() == CircuitBreakerState.OPEN) {
            long elapsed = System.currentTimeMillis() - openSince.get();
            if (elapsed >= config.cooldownMs()) {
                synchronized (this) {
                    if (state.get() == CircuitBreakerState.OPEN) {
                        state.set(CircuitBreakerState.HALF_OPEN);
                        resetHalfOpenCounters();
                    }
                }
            }
        }
    }
}
