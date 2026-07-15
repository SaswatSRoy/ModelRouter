package io.modelrouter.core.execution;

import io.modelrouter.core.domain.InferenceChunk;
import io.modelrouter.core.domain.InferenceResponse;
import io.modelrouter.core.domain.RoutingInfo;
import reactor.core.publisher.Flux;

/**
 * Normalizes inference responses by stamping them with the final routing execution metadata.
 */
public class ResponseNormalizer {

    /**
     * Normalizes a non-streaming response.
     */
    public InferenceResponse normalize(InferenceResponse raw, RoutingInfo routingInfo) {
        return new InferenceResponse(
                raw.id(),
                raw.content(),
                raw.finishReason(),
                raw.usage(),
                routingInfo
        );
    }

    /**
     * Normalizes a streaming response by appending routing metadata to the final chunk.
     */
    public Flux<InferenceChunk> normalizeStream(Flux<InferenceChunk> rawStream, RoutingInfo routingInfo) {
        return rawStream.map(chunk -> {
            if (chunk.done()) {
                return new InferenceChunk(
                        chunk.delta(),
                        true,
                        chunk.usage(),
                        routingInfo
                );
            }
            return chunk;
        });
    }
}
