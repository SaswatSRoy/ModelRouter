package io.modelrouter.core.execution;

import io.modelrouter.core.domain.CircuitBreakerState;
import io.modelrouter.core.domain.ProviderId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry managing per-(provider, model) circuit breakers.
 */
public class CircuitBreakerRegistry {

    private final ConcurrentHashMap<String, CircuitBreaker> registry = new ConcurrentHashMap<>();

    public CircuitBreaker getOrCreate(ProviderId providerId, String modelId) {
        return getOrCreate(providerId, modelId, CircuitBreakerConfig.DEFAULT);
    }

    public CircuitBreaker getOrCreate(ProviderId providerId, String modelId, CircuitBreakerConfig config) {
        String key = keyOf(providerId, modelId);
        return registry.computeIfAbsent(key, k -> new CircuitBreaker(config));
    }

    public Map<String, CircuitBreakerState> getAllStates() {
        Map<String, CircuitBreakerState> states = new HashMap<>();
        registry.forEach((k, cb) -> states.put(k, cb.getState()));
        return states;
    }

    public void reset(ProviderId providerId, String modelId) {
        String key = keyOf(providerId, modelId);
        registry.remove(key);
    }

    private String keyOf(ProviderId providerId, String modelId) {
        return providerId.value() + ":" + modelId;
    }
}
