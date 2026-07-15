package io.modelrouter.provider.fake;

import io.modelrouter.core.domain.ErrorCategory;
import io.modelrouter.core.domain.HealthStatus;

/**
 * Configuration parameters for the FakeInferenceProvider.
 */
public record FakeProviderConfig(
    long responseDelayMs,
    long streamChunkIntervalMs,
    HealthStatus healthStatus,
    boolean shouldFail,
    ErrorCategory failureCategory,
    int failAfterChunks
) {
    public static final FakeProviderConfig DEFAULT = new FakeProviderConfig(
        100, 50, HealthStatus.UP, false, ErrorCategory.UNKNOWN, -1
    );
}
