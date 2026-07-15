package io.modelrouter.core.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an inference request to be routed to a provider.
 *
 * <p>Use the {@link Builder} obtained via {@link #builder()} for
 * convenient construction. The {@code id} field is auto-generated
 * as a UUID if not explicitly set.
 *
 * @param id             unique request identifier (auto-generated if null)
 * @param tenantId       the tenant originating the request
 * @param messages       the conversation messages
 * @param stream         whether to use streaming response
 * @param policyOverride per-request policy overrides (nullable)
 */
public record InferenceRequest(
        String id,
        String tenantId,
        List<Message> messages,
        boolean stream,
        RequestPolicyOverride policyOverride
) {

    /**
     * Validates required fields and creates a defensive copy of messages.
     * Auto-generates an id if null.
     */
    public InferenceRequest {
        id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        Objects.requireNonNull(messages, "messages cannot be null");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages cannot be empty");
        }
        messages = List.copyOf(messages);
    }

    /**
     * Creates a new builder for constructing {@link InferenceRequest} instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link InferenceRequest}.
     */
    public static final class Builder {
        private String id;
        private String tenantId;
        private List<Message> messages;
        private boolean stream;
        private RequestPolicyOverride policyOverride;

        private Builder() {
        }

        /**
         * Sets the request identifier. If not set, a UUID is auto-generated.
         *
         * @param id the request id
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the tenant identifier.
         *
         * @param tenantId the tenant id
         * @return this builder
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * Sets the conversation messages.
         *
         * @param messages the message list
         * @return this builder
         */
        public Builder messages(List<Message> messages) {
            this.messages = messages;
            return this;
        }

        /**
         * Sets whether the response should be streamed.
         *
         * @param stream {@code true} for streaming
         * @return this builder
         */
        public Builder stream(boolean stream) {
            this.stream = stream;
            return this;
        }

        /**
         * Sets the per-request policy override.
         *
         * @param policyOverride the override, may be {@code null}
         * @return this builder
         */
        public Builder policyOverride(RequestPolicyOverride policyOverride) {
            this.policyOverride = policyOverride;
            return this;
        }

        /**
         * Builds the {@link InferenceRequest}.
         *
         * @return a new inference request
         */
        public InferenceRequest build() {
            return new InferenceRequest(id, tenantId, messages, stream, policyOverride);
        }
    }
}
