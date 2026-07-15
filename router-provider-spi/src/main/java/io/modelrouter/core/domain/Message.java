package io.modelrouter.core.domain;

import java.util.Objects;

/**
 * Represents a single message in a conversation for inference requests.
 *
 * @param role    the message role (e.g. "system", "user", "assistant"),
 *                must be non-null and non-blank
 * @param content the message content, must be non-null and non-blank
 */
public record Message(String role, String content) {

    /**
     * Validates that both role and content are non-null and non-blank.
     */
    public Message {
        Objects.requireNonNull(role, "Message role cannot be null");
        if (role.isBlank()) {
            throw new IllegalArgumentException("Message role cannot be blank");
        }
        Objects.requireNonNull(content, "Message content cannot be null");
        if (content.isBlank()) {
            throw new IllegalArgumentException("Message content cannot be blank");
        }
    }
}
