package io.modelrouter.core.domain;

import java.util.Objects;

/**
 * A string-based value object representing a single provider capability.
 *
 * <p>Examples: {@code "128k-context"}, {@code "function-calling"},
 * {@code "vision"}, {@code "chat"}.
 *
 * @param value the capability identifier, must be non-null and non-blank
 */
public record ProviderCapability(String value) {

    public static final String CHAT = "chat";
    public static final String VISION = "vision";
    public static final String CONTEXT_128K = "128k-context";
    public static final String FUNCTION_CALLING = "function-calling";

    /**
     * Validates the capability value on construction.
     */
    public ProviderCapability {
        Objects.requireNonNull(value, "ProviderCapability value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ProviderCapability value cannot be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
