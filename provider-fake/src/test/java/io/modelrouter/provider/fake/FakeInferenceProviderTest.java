package io.modelrouter.provider.fake;

import static org.junit.jupiter.api.Assertions.*;

import io.modelrouter.core.domain.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Unit tests specific to the FakeInferenceProvider.
 */
class FakeInferenceProviderTest {

    @Test
    void testConfigurableDelay() {
        FakeProviderConfig config = new FakeProviderConfig(150, 10, HealthStatus.UP, false, ErrorCategory.UNKNOWN, -1);
        FakeInferenceProvider provider = new FakeInferenceProvider(config);

        InferenceRequest request = InferenceRequest.builder()
                .tenantId("tenant-1")
                .messages(List.of(new Message("user", "hi")))
                .build();

        long start = System.currentTimeMillis();
        StepVerifier.create(provider.invoke(request))
                .assertNext(res -> {
                    long elapsed = System.currentTimeMillis() - start;
                    assertTrue(elapsed >= 150, "Should have delayed response by at least 150ms");
                    assertEquals("fake", res.routing().providerId().value());
                })
                .verifyComplete();
    }

    @Test
    void testSimulatedFailure() {
        FakeProviderConfig config = new FakeProviderConfig(0, 0, HealthStatus.UP, true, ErrorCategory.RETRYABLE_SERVER_ERROR, -1);
        FakeInferenceProvider provider = new FakeInferenceProvider(config);

        InferenceRequest request = InferenceRequest.builder()
                .tenantId("tenant-1")
                .messages(List.of(new Message("user", "hi")))
                .build();

        StepVerifier.create(provider.invoke(request))
                .expectErrorMatches(t -> t instanceof ProviderException && 
                        ((ProviderException) t).getCategory() == ErrorCategory.RETRYABLE_SERVER_ERROR)
                .verify();
    }

    @Test
    void testStreamingFailureAfterChunks() {
        FakeProviderConfig config = new FakeProviderConfig(0, 10, HealthStatus.UP, true, ErrorCategory.RETRYABLE_RATE_LIMITED, 3);
        FakeInferenceProvider provider = new FakeInferenceProvider(config);

        InferenceRequest request = InferenceRequest.builder()
                .tenantId("tenant-1")
                .messages(List.of(new Message("user", "hi")))
                .build();

        StepVerifier.create(provider.invokeStreaming(request))
                .expectNextCount(2) // Emits chunk-1 and chunk-2
                .expectErrorMatches(t -> t instanceof ProviderException &&
                        ((ProviderException) t).getCategory() == ErrorCategory.RETRYABLE_RATE_LIMITED)
                .verify();
    }
}
