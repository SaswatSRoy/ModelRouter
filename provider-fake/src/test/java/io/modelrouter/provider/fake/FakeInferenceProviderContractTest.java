package io.modelrouter.provider.fake;

import io.modelrouter.provider.spi.InferenceProvider;
import io.modelrouter.provider.spi.InferenceProviderContractTest;

/**
 * Contract verification test for the FakeInferenceProvider.
 */
class FakeInferenceProviderContractTest extends InferenceProviderContractTest {

    @Override
    protected InferenceProvider createProvider() {
        return new FakeInferenceProvider();
    }
}
