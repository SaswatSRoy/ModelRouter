package io.modelrouter.core.domain;

/**
 * Defines the retry policy for pre-first-byte retry logic.
 *
 * <p>Retries use exponential backoff: the delay for attempt {@code n}
 * (0-indexed) is {@code min(initialBackoffMs * backoffMultiplier^n, maxBackoffMs)}.
 *
 * @param maxAttempts       maximum number of attempts (including the initial call)
 * @param initialBackoffMs  initial backoff delay in milliseconds
 * @param backoffMultiplier multiplier applied to the backoff on each retry
 * @param maxBackoffMs      upper bound on the backoff delay in milliseconds
 */
public record RetryPolicy(int maxAttempts, long initialBackoffMs,
                           double backoffMultiplier, long maxBackoffMs) {

    /** Default retry policy: 3 attempts, 200ms initial backoff, 2x multiplier, 5s max. */
    public static final RetryPolicy DEFAULT = new RetryPolicy(3, 200L, 2.0, 5000L);

    /**
     * Validates retry policy parameters.
     */
    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, got " + maxAttempts);
        }
        if (initialBackoffMs < 0) {
            throw new IllegalArgumentException("initialBackoffMs must be >= 0, got " + initialBackoffMs);
        }
        if (backoffMultiplier < 1.0) {
            throw new IllegalArgumentException("backoffMultiplier must be >= 1.0, got " + backoffMultiplier);
        }
        if (maxBackoffMs < 0) {
            throw new IllegalArgumentException("maxBackoffMs must be >= 0, got " + maxBackoffMs);
        }
    }

    /**
     * Computes the backoff delay for the given attempt number (0-indexed).
     *
     * <p>The formula is:
     * {@code min(initialBackoffMs * backoffMultiplier^attempt, maxBackoffMs)}
     *
     * @param attempt the zero-based attempt index
     * @return the backoff delay in milliseconds
     */
    public long computeBackoff(int attempt) {
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must be >= 0, got " + attempt);
        }
        double backoff = initialBackoffMs * Math.pow(backoffMultiplier, attempt);
        return Math.min((long) backoff, maxBackoffMs);
    }
}
