package io.modelrouter.core.domain;

import java.util.Objects;

/**
 * Captures routing metadata about which provider and model served
 * an inference request, for observability and tracing.
 *
 * @param providerId the provider that handled the request
 * @param modelId    the specific model used
 * @param attempt    the attempt number (1-based)
 * @param latencyMs  end-to-end latency in milliseconds
 */
public record RoutingInfo(ProviderId providerId, String modelId, int attempt, long latencyMs) {

    /**
     * Validates routing info fields.
     */
    public RoutingInfo {
        Objects.requireNonNull(providerId, "providerId cannot be null");
        Objects.requireNonNull(modelId, "modelId cannot be null");
    }
}
