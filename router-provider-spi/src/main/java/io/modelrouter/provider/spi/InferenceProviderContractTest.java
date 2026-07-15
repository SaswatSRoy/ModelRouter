package io.modelrouter.provider.spi;

import static org.junit.jupiter.api.Assertions.*;

import io.modelrouter.core.domain.*;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Abstract contract test suite verifying that an InferenceProvider implementation
 * conforms to the SPI contracts.
 */
public abstract class InferenceProviderContractTest {

    protected InferenceProvider provider;

    protected abstract InferenceProvider createProvider();

    @BeforeEach
    void setUpContractTest() {
        provider = createProvider();
        assertNotNull(provider, "createProvider() must not return null");
    }

    @Test
    void testIdContract() {
        ProviderId id = provider.id();
        assertNotNull(id, "Provider id must not be null");
        assertNotNull(id.value(), "Provider id value must not be null");
        assertTrue(id.value().matches("^[a-z0-9-]+$"), 
                "Provider id must be lowercase-alphanumeric-with-hyphens");
    }

    @Test
    void testCapabilitiesContract() {
        assertDoesNotThrow(() -> {
            ProviderCapabilities caps = provider.capabilities();
            assertNotNull(caps, "capabilities() must not return null");
            assertEquals(provider.id(), caps.providerId(), "Capabilities provider ID must match");
            assertNotNull(caps.supportedCapabilities(), "supportedCapabilities must not be null");
            assertNotNull(caps.availableModels(), "availableModels must not be null");
            assertNotNull(caps.privacyTier(), "privacyTier must not be null");
        });
    }

    @Test
    void testHealthCheckContract() {
        StepVerifier.create(provider.healthCheck())
                .assertNext(status -> assertNotNull(status, "healthCheck must return a status"))
                .expectComplete()
                .verify(Duration.ofSeconds(5)); // healthCheck must complete within 5 seconds
    }

    @Test
    void testEstimateCostContract() {
        InferenceRequest req = InferenceRequest.builder()
                .tenantId("test")
                .messages(List.of(new Message("user", "test")))
                .build();

        assertDoesNotThrow(() -> {
            CostEstimate cost = provider.estimateCost(req);
            assertNotNull(cost, "estimateCost must not return null");
        });
    }
}
