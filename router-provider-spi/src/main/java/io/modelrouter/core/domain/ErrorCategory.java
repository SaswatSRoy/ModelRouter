package io.modelrouter.core.domain;

/**
 * Classifies inference errors into categories that determine
 * whether the execution runtime should retry or fail fast.
 */
public enum ErrorCategory {

    /** Server-side transient error (5xx); safe to retry. */
    RETRYABLE_SERVER_ERROR(true),

    /** Rate-limited by the provider (429); safe to retry after backoff. */
    RETRYABLE_RATE_LIMITED(true),

    /** Client-side error (4xx except 429); do not retry. */
    NON_RETRYABLE_CLIENT_ERROR(false),

    /** Provider-level permanent error; do not retry. */
    NON_RETRYABLE_PROVIDER_ERROR(false),

    /** Unknown or unclassified error. */
    UNKNOWN(false);

    private final boolean retryable;

    ErrorCategory(boolean retryable) {
        this.retryable = retryable;
    }

    /**
     * Returns whether errors in this category are safe to retry.
     *
     * @return {@code true} if the error is retryable
     */
    public boolean isRetryable() {
        return retryable;
    }
}
