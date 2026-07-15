package io.modelrouter.core.domain;

import java.util.Objects;

public record ProviderId(String value) implements java.io.Serializable {
    public ProviderId {
        Objects.requireNonNull(value, "ProviderId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ProviderId cannot be blank");
        }
        if (!value.matches("^[a-z0-9-]+$")) {
            throw new IllegalArgumentException("ProviderId must be lowercase-alphanumeric-with-hyphens");
        }
    }
}
