package io.modelrouter.core.execution;

import java.io.Serial;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when all eligible candidate models in the ExecutionPlan have failed execution.
 */
public class CandidateExhaustedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;
    private final transient List<CandidateFailure> failures;

    public CandidateExhaustedException(String message, List<CandidateFailure> failures) {
        super(message);
        this.failures = failures != null ? List.copyOf(failures) : Collections.emptyList();
    }

    public List<CandidateFailure> getFailures() {
        return failures;
    }
}
