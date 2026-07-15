package io.modelrouter.core.policy;

import io.modelrouter.core.domain.RoutingPolicy;
import java.util.Optional;

/**
 * Port interface for retrieving routing policies.
 */
public interface PolicyStore {

    /**
     * Gets the system default routing policy.
     */
    Optional<RoutingPolicy> getSystemDefault();

    /**
     * Gets the default routing policy for a specific tenant.
     */
    Optional<RoutingPolicy> getTenantDefault(String tenantId);
}
