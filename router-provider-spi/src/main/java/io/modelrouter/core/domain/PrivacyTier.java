package io.modelrouter.core.domain;

/**
 * Defines the privacy tier for data handling during inference.
 *
 * <p>{@link #LOCAL_ONLY} is the most restrictive: data must not leave
 * the local environment. {@link #CLOUD_ALLOWED} permits cloud-based
 * inference providers.
 *
 * <p>Tightening-only merge semantics: a privacy tier can only become
 * <em>more</em> restrictive, never less.
 */
public enum PrivacyTier {

    /** Data must remain on-premises; no cloud providers allowed. */
    LOCAL_ONLY,

    /** Data may be sent to cloud-based providers. */
    CLOUD_ALLOWED;

    /**
     * Checks whether this tier can satisfy the {@code required} privacy tier.
     *
     * <p>{@code LOCAL_ONLY} satisfies both {@code LOCAL_ONLY} and
     * {@code CLOUD_ALLOWED}. {@code CLOUD_ALLOWED} can only satisfy
     * {@code CLOUD_ALLOWED}.
     *
     * @param required the privacy tier that must be satisfied
     * @return {@code true} if this tier meets or exceeds the requirement
     */
    public boolean canSatisfy(PrivacyTier required) {
        if (this == LOCAL_ONLY) {
            return true;
        }
        // CLOUD_ALLOWED can only satisfy CLOUD_ALLOWED
        return required == CLOUD_ALLOWED;
    }

    /**
     * Returns the more restrictive of this tier and the {@code other} tier.
     *
     * <p>Used during hierarchical policy merge where privacy can only
     * tighten, never loosen.
     *
     * @param other the other privacy tier to compare against
     * @return the more restrictive tier ({@code LOCAL_ONLY} wins)
     */
    public PrivacyTier tighten(PrivacyTier other) {
        if (this == LOCAL_ONLY || other == LOCAL_ONLY) {
            return LOCAL_ONLY;
        }
        return CLOUD_ALLOWED;
    }
}
