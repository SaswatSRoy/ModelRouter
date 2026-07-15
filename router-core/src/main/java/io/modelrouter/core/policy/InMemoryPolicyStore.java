package io.modelrouter.core.policy;

import io.modelrouter.core.domain.RoutingPolicy;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of the PolicyStore port.
 */
public class InMemoryPolicyStore implements PolicyStore {

    private volatile RoutingPolicy systemDefault;
    private final Map<String, RoutingPolicy> tenantDefaults = new ConcurrentHashMap<>();

    @Override
    public Optional<RoutingPolicy> getSystemDefault() {
        return Optional.ofNullable(systemDefault);
    }

    @Override
    public Optional<RoutingPolicy> getTenantDefault(String tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tenantDefaults.get(tenantId));
    }

    public void setSystemDefault(RoutingPolicy policy) {
        this.systemDefault = policy;
    }

    public void setTenantDefault(String tenantId, RoutingPolicy policy) {
        if (tenantId != null && policy != null) {
            tenantDefaults.put(tenantId, policy);
        }
    }

    public void removeTenantDefault(String tenantId) {
        if (tenantId != null) {
            tenantDefaults.remove(tenantId);
        }
    }
}
