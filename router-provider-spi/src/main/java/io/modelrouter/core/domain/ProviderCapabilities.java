package io.modelrouter.core.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Describes the capabilities and configuration of an inference provider.
 *
 * @param providerId            the unique provider identifier
 * @param supportedCapabilities set of capability strings this provider supports
 * @param availableModels       list of model IDs available from this provider
 * @param privacyTier           the privacy tier of this provider
 * @param maxContextTokens      maximum context window size in tokens
 */
public record ProviderCapabilities(
        ProviderId providerId,
        Set<String> supportedCapabilities,
        List<String> availableModels,
        PrivacyTier privacyTier,
        int maxContextTokens
) {

    /**
     * Validates and creates defensive copies of collections.
     */
    public ProviderCapabilities {
        Objects.requireNonNull(providerId, "providerId cannot be null");
        Objects.requireNonNull(privacyTier, "privacyTier cannot be null");
        supportedCapabilities = supportedCapabilities != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(supportedCapabilities))
                : Set.of();
        availableModels = availableModels != null
                ? Collections.unmodifiableList(List.copyOf(availableModels))
                : List.of();
    }

    /**
     * Checks whether this provider supports the given capability.
     *
     * @param capability the capability to check
     * @return {@code true} if the capability is supported
     */
    public boolean supports(String capability) {
        return supportedCapabilities.contains(capability);
    }

    /**
     * Checks whether this provider supports <em>all</em> of the required capabilities.
     *
     * @param required the set of required capabilities
     * @return {@code true} if every required capability is supported
     */
    public boolean supportsAll(Set<String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        return supportedCapabilities.containsAll(required);
    }
}
