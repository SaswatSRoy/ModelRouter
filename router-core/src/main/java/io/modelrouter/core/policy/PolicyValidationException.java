package io.modelrouter.core.policy;

/**
 * Thrown when policy merge would violate tightening-only invariants or when a resolved policy is internally inconsistent.
 */
public class PolicyValidationException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    public PolicyValidationException(String message) {
        super(message);
    }

    public PolicyValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
