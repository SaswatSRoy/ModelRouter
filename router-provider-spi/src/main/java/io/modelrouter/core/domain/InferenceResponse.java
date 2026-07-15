package io.modelrouter.core.domain;

import java.util.Objects;

/**
 * The complete response from a non-streaming inference call.
 *
 * @param id           unique response identifier
 * @param content      the generated text content
 * @param finishReason why generation stopped (e.g. "stop", "length")
 * @param usage        token usage and cost information
 * @param routing      metadata about which provider/model served the request
 */
public record InferenceResponse(
        String id,
        String content,
        String finishReason,
        TokenUsage usage,
        RoutingInfo routing
) {

    /**
     * Validates that required fields are non-null.
     */
    public InferenceResponse {
        Objects.requireNonNull(id, "Response id cannot be null");
        Objects.requireNonNull(content, "Response content cannot be null");
        Objects.requireNonNull(routing, "RoutingInfo cannot be null");
    }
}
