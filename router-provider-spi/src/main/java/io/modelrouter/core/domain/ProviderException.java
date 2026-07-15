package io.modelrouter.core.domain;

import java.util.Objects;

/**
 * Exception thrown by inference providers, classified into an
 * {@link ErrorCategory} to drive retry/fail-fast decisions in the
 * execution runtime.
 */
public class ProviderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCategory category;
    private final ProviderId providerId;
    private final int httpStatus;
    private final String providerMessage;

    /**
     * Constructs a new provider exception.
     *
     * @param category        the error classification
     * @param providerId      the provider that raised the error
     * @param httpStatus      the HTTP status code, or {@code 0} if unavailable
     * @param providerMessage a human-readable message from the provider
     */
    public ProviderException(ErrorCategory category, ProviderId providerId,
                             int httpStatus, String providerMessage) {
        super(formatMessage(category, providerId, httpStatus, providerMessage));
        this.category = Objects.requireNonNull(category, "ErrorCategory cannot be null");
        this.providerId = Objects.requireNonNull(providerId, "ProviderId cannot be null");
        this.httpStatus = httpStatus;
        this.providerMessage = providerMessage;
    }

    /**
     * Constructs a new provider exception with a cause.
     *
     * @param category        the error classification
     * @param providerId      the provider that raised the error
     * @param httpStatus      the HTTP status code, or {@code 0} if unavailable
     * @param providerMessage a human-readable message from the provider
     * @param cause           the underlying cause
     */
    public ProviderException(ErrorCategory category, ProviderId providerId,
                             int httpStatus, String providerMessage, Throwable cause) {
        super(formatMessage(category, providerId, httpStatus, providerMessage), cause);
        this.category = Objects.requireNonNull(category, "ErrorCategory cannot be null");
        this.providerId = Objects.requireNonNull(providerId, "ProviderId cannot be null");
        this.httpStatus = httpStatus;
        this.providerMessage = providerMessage;
    }

    /**
     * Returns the error classification category.
     *
     * @return the error category
     */
    public ErrorCategory getCategory() {
        return category;
    }

    /**
     * Returns the provider that raised the error.
     *
     * @return the provider ID
     */
    public ProviderId getProviderId() {
        return providerId;
    }

    /**
     * Returns the HTTP status code, or {@code 0} if not available.
     *
     * @return the HTTP status code
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * Returns the human-readable message from the provider.
     *
     * @return the provider message, may be {@code null}
     */
    public String getProviderMessage() {
        return providerMessage;
    }

    /**
     * Returns whether this error is retryable based on its category.
     *
     * @return {@code true} if the error is retryable
     */
    public boolean isRetryable() {
        return category.isRetryable();
    }

    private static String formatMessage(ErrorCategory category, ProviderId providerId,
                                        int httpStatus, String providerMessage) {
        return "Provider '%s' error [%s] (HTTP %d): %s".formatted(
                providerId.value(), category, httpStatus,
                providerMessage != null ? providerMessage : "no message");
    }
}
