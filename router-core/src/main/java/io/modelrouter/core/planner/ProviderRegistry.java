package io.modelrouter.core.planner;

import io.modelrouter.core.domain.ProviderId;
import io.modelrouter.provider.spi.InferenceProvider;
import java.util.List;
import java.util.Optional;

/**
 * Port interface for looking up registered providers.
 */
public interface ProviderRegistry {

    /**
     * Returns all registered inference providers.
     */
    List<InferenceProvider> getAllProviders();

    /**
     * Gets a provider by its ID.
     */
    Optional<InferenceProvider> getProvider(ProviderId id);
}
