package io.modelrouter.core.execution;

import io.modelrouter.core.domain.ErrorCategory;
import io.modelrouter.core.domain.ProviderId;

/**
 * Captures failure details for a candidate model of a provider.
 */
public record CandidateFailure(
    ProviderId providerId,
    String modelId,
    ErrorCategory errorCategory,
    String message,
    int attempts
) {}
