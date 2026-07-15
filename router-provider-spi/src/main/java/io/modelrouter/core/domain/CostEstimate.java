package io.modelrouter.core.domain;

import java.util.Objects;

/**
 * An estimated cost for an inference request.
 *
 * <p>Use the {@link #UNKNOWN} sentinel when the provider cannot
 * estimate cost ahead of time.
 *
 * @param estimatedCostUsd the estimated cost; {@code -1.0} for unknown
 * @param currency         the currency code (default {@code "USD"})
 */
public record CostEstimate(double estimatedCostUsd, String currency) {

    public static final String USD = "USD";

    /** Sentinel value indicating the cost is unknown. */
    public static final CostEstimate UNKNOWN = new CostEstimate(-1.0, USD);

    /**
     * Validates that currency is non-null.
     */
    public CostEstimate {
        Objects.requireNonNull(currency, "Currency cannot be null");
    }

    /**
     * Returns whether this estimate represents a known cost.
     *
     * @return {@code true} if the cost is known (not the {@link #UNKNOWN} sentinel)
     */
    public boolean isKnown() {
        return estimatedCostUsd >= 0.0;
    }
}
