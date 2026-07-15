package io.modelrouter.core.domain;

import java.util.Objects;

/**
 * Represents a specific model candidate offered by a provider.
 */
public record ProviderCandidate(
    ProviderId providerId,
    String modelId,
    ProviderCapabilities capabilities
) {
    public ProviderCandidate {
        Objects.requireNonNull(providerId, "providerId cannot be null");
        Objects.requireNonNull(modelId, "modelId cannot be null");
        Objects.requireNonNull(capabilities, "capabilities cannot be null");
        if (modelId.isBlank()) {
            throw new IllegalArgumentException("modelId cannot be blank");
        }
    }
}
