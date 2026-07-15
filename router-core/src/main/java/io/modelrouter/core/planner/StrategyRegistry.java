package io.modelrouter.core.planner;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of available routing strategies.
 */
public class StrategyRegistry {

    private final Map<String, RoutingStrategy> strategies = new ConcurrentHashMap<>();
    private final RoutingStrategy defaultStrategy;

    public StrategyRegistry() {
        PriorityOrderFallbackStrategy priorityFallback = new PriorityOrderFallbackStrategy();
        WeightedRoundRobinStrategy roundRobin = new WeightedRoundRobinStrategy();
        
        strategies.put(priorityFallback.id(), priorityFallback);
        strategies.put(roundRobin.id(), roundRobin);
        
        this.defaultStrategy = priorityFallback;
    }

    public void register(RoutingStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy cannot be null");
        strategies.put(strategy.id(), strategy);
    }

    public RoutingStrategy getStrategy(String strategyId) {
        if (strategyId == null) {
            return defaultStrategy;
        }
        RoutingStrategy strategy = strategies.get(strategyId);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown routing strategy ID: " + strategyId);
        }
        return strategy;
    }

    public RoutingStrategy getDefaultStrategy() {
        return defaultStrategy;
    }
}
