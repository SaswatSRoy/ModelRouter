package io.modelrouter.provider.spi;

import java.time.Instant;

/**
 * Rate limit information returned by a provider, parsed from upstream headers.
 */
public record RateLimitInfo(
    int remaining,
    int limit,
    Instant resetAt
) {
    public RateLimitInfo {
        if (remaining < 0) {
            throw new IllegalArgumentException("remaining cannot be negative");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit cannot be negative");
        }
    }
}
