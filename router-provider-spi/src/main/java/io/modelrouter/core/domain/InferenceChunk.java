package io.modelrouter.core.domain;

/**
 * A single chunk in a streaming inference response.
 *
 * <p>Intermediate chunks carry only a text {@code delta} with
 * {@code done = false}. The final chunk has {@code done = true}
 * and may include {@link TokenUsage} and {@link RoutingInfo}.
 *
 * @param delta   the incremental text content for this chunk
 * @param done    {@code true} if this is the final chunk in the stream
 * @param usage   token usage and cost; non-null only on the final chunk
 * @param routing routing metadata; non-null only on the final chunk
 */
public record InferenceChunk(
        String delta,
        boolean done,
        TokenUsage usage,
        RoutingInfo routing
) {
}
