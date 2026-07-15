package io.modelrouter.provider.fake;

import io.modelrouter.core.domain.CostEstimate;
import io.modelrouter.core.domain.HealthStatus;
import io.modelrouter.core.domain.InferenceChunk;
import io.modelrouter.core.domain.InferenceRequest;
import io.modelrouter.core.domain.InferenceResponse;
import io.modelrouter.core.domain.PrivacyTier;
import io.modelrouter.core.domain.ProviderCapabilities;
import io.modelrouter.core.domain.ProviderCapability;
import io.modelrouter.core.domain.ProviderException;
import io.modelrouter.core.domain.ProviderId;
import io.modelrouter.core.domain.RoutingInfo;
import io.modelrouter.core.domain.TokenUsage;
import io.modelrouter.provider.spi.InferenceProvider;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Fake provider adapter for testing, local simulation, and contract verification.
 */
public class FakeInferenceProvider implements InferenceProvider {

    private final ProviderId id = new ProviderId("fake");
    private final FakeProviderConfig config;
    private final ProviderCapabilities capabilities;

    public FakeInferenceProvider() {
        this(FakeProviderConfig.DEFAULT);
    }

    public FakeInferenceProvider(FakeProviderConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.capabilities = new ProviderCapabilities(
                id,
                Set.of(ProviderCapability.CHAT, ProviderCapability.CONTEXT_128K),
                List.of("fake-model-1", "fake-model-2"),
                PrivacyTier.CLOUD_ALLOWED,
                128000
        );
    }

    @Override
    public ProviderId id() {
        return id;
    }

    @Override
    public Mono<InferenceResponse> invoke(InferenceRequest request) {
        if (config.shouldFail()) {
            return Mono.error(new ProviderException(
                    config.failureCategory(), id, 500, "Simulated provider failure"
            ));
        }

        Mono<InferenceResponse> response = Mono.just(new InferenceResponse(
                "fake-req-" + System.nanoTime(),
                "This is a canned fake response content.",
                "stop",
                new TokenUsage(10, 20, 0.001),
                new RoutingInfo(id, "fake-model-1", 1, config.responseDelayMs())
        ));

        if (config.responseDelayMs() > 0) {
            response = response.delayElement(Duration.ofMillis(config.responseDelayMs()));
        }

        return response;
    }

    @Override
    public Flux<InferenceChunk> invokeStreaming(InferenceRequest request) {
        if (config.shouldFail() && config.failAfterChunks() == 0) {
            return Flux.error(new ProviderException(
                    config.failureCategory(), id, 500, "Simulated provider failure at start of stream"
            ));
        }

        return Flux.interval(Duration.ofMillis(config.streamChunkIntervalMs()))
                .take(5)
                .flatMap(index -> {
                    int i = index.intValue() + 1;
                    if (config.shouldFail() && config.failAfterChunks() == i) {
                        return Mono.error(new ProviderException(
                                config.failureCategory(), id, 500, "Simulated provider failure at chunk " + i
                        ));
                    }
                    if (i == 5) {
                        return Mono.just(new InferenceChunk(
                                " chunk-" + i,
                                true,
                                new TokenUsage(10, 20, 0.001),
                                new RoutingInfo(id, "fake-model-1", 1, config.streamChunkIntervalMs() * 5)
                        ));
                    } else {
                        return Mono.just(new InferenceChunk(
                                " chunk-" + i,
                                false,
                                null,
                                null
                        ));
                    }
                });
    }

    @Override
    public ProviderCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public Mono<HealthStatus> healthCheck() {
        return Mono.just(config.healthStatus());
    }

    @Override
    public CostEstimate estimateCost(InferenceRequest request) {
        return new CostEstimate(0.001, CostEstimate.USD);
    }
}
