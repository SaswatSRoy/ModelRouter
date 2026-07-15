package io.modelrouter.provider.spi;

import io.modelrouter.core.domain.CostEstimate;
import io.modelrouter.core.domain.HealthStatus;
import io.modelrouter.core.domain.InferenceChunk;
import io.modelrouter.core.domain.InferenceRequest;
import io.modelrouter.core.domain.InferenceResponse;
import io.modelrouter.core.domain.ProviderCapabilities;
import io.modelrouter.core.domain.ProviderId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Provider Interface (SPI) for inference providers.
 * Implementing this interface allows a provider adapter to plug into the ModelRouter control plane.
 */
public interface InferenceProvider {

    /**
     * Returns the unique provider identifier.
     * Must be idempotent, deterministic, and never null.
     * Format: lowercase-alphanumeric-with-hyphens.
     */
    ProviderId id();

    /**
     * Executes non-streaming inference synchronously.
     * Must return a Mono that completes with exactly one response or errors.
     * Must map all provider-specific errors to ModelRouter's normalized error taxonomy.
     */
    Mono<InferenceResponse> invoke(InferenceRequest request);

    /**
     * Executes streaming inference.
     * Must return a Flux emitting canonical response chunks.
     * Must handle provider-specific streaming protocols and normalize to InferenceChunk.
     */
    Flux<InferenceChunk> invokeStreaming(InferenceRequest request);

    /**
     * Returns the provider's capabilities.
     * This method must be fast and must never throw exceptions.
     */
    ProviderCapabilities capabilities();

    /**
     * Performs a lightweight check of the provider's health.
     * Must not trigger billing on the provider side.
     */
    Mono<HealthStatus> healthCheck();

    /**
     * Computes a local cost estimate for a request.
     * Must be a pure calculation with no network calls.
     */
    CostEstimate estimateCost(InferenceRequest request);
}
