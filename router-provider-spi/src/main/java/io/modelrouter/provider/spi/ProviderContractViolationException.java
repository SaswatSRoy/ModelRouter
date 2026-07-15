package io.modelrouter.provider.spi;

/**
 * Thrown when an inference provider violates its API contract (e.g., throwing unexpected exceptions
 * or violating timeouts in health check).
 */
public class ProviderContractViolationException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    public ProviderContractViolationException(String message) {
        super(message);
    }

    public ProviderContractViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
