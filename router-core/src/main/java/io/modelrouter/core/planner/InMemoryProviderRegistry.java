package io.modelrouter.core.planner;

import io.modelrouter.core.domain.ProviderId;
import io.modelrouter.provider.spi.InferenceProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory implementation of the ProviderRegistry port.
 */
public class InMemoryProviderRegistry implements ProviderRegistry {

    private final Map<ProviderId, InferenceProvider> providers = new ConcurrentHashMap<>();

    @Override
    public List<InferenceProvider> getAllProviders() {
        return new ArrayList<>(providers.values());
    }

    @Override
    public Optional<InferenceProvider> getProvider(ProviderId id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(id));
    }

    public void register(InferenceProvider provider) {
        Objects.requireNonNull(provider, "provider cannot be null");
        providers.put(provider.id(), provider);
    }

    public void unregister(ProviderId id) {
        if (id != null) {
            providers.remove(id);
        }
    }
}
