package io.modelrouter.core.execution;

import io.modelrouter.core.domain.CircuitBreakerState;
import io.modelrouter.core.domain.ErrorCategory;
import io.modelrouter.core.domain.ExecutionPlan;
import io.modelrouter.core.domain.InferenceChunk;
import io.modelrouter.core.domain.InferenceRequest;
import io.modelrouter.core.domain.InferenceResponse;
import io.modelrouter.core.domain.ProviderException;
import io.modelrouter.core.domain.RoutingInfo;
import io.modelrouter.core.domain.ScoredCandidate;
import io.modelrouter.core.planner.ProviderRegistry;
import io.modelrouter.provider.spi.InferenceProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Execution engine responsible for walking the ExecutionPlan, executing requests against provider adapters,
 * managing pre-first-byte retries, and falling back on failures.
 */
public class ExecutionEngine {

    private final ProviderRegistry providerRegistry;
    private final CircuitBreakerRegistry cbRegistry;
    private final ResponseNormalizer normalizer = new ResponseNormalizer();

    public ExecutionEngine(ProviderRegistry providerRegistry, CircuitBreakerRegistry cbRegistry) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry cannot be null");
        this.cbRegistry = Objects.requireNonNull(cbRegistry, "cbRegistry cannot be null");
    }

    /**
     * Executes non-streaming inference.
     */
    public Mono<InferenceResponse> execute(InferenceRequest request, ExecutionPlan plan) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(plan, "plan cannot be null");
        return executeCandidate(request, plan, 0, new ArrayList<>());
    }

    /**
     * Executes streaming inference.
     */
    public Flux<InferenceChunk> executeStreaming(InferenceRequest request, ExecutionPlan plan) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(plan, "plan cannot be null");
        return executeStreamingCandidate(request, plan, 0, new ArrayList<>());
    }

    private Mono<InferenceResponse> executeCandidate(
            InferenceRequest request,
            ExecutionPlan plan,
            int candidateIndex,
            List<CandidateFailure> accumulatedFailures) {

        if (candidateIndex >= plan.candidates().size()) {
            return Mono.error(new CandidateExhaustedException("All candidates exhausted", accumulatedFailures));
        }

        ScoredCandidate candidate = plan.candidates().get(candidateIndex);
        CircuitBreaker cb = cbRegistry.getOrCreate(candidate.providerId(), candidate.modelId());

        if (cb.getState() == CircuitBreakerState.OPEN) {
            accumulatedFailures.add(new CandidateFailure(
                    candidate.providerId(),
                    candidate.modelId(),
                    ErrorCategory.UNKNOWN,
                    "Circuit breaker open",
                    0
            ));
            return executeCandidate(request, plan, candidateIndex + 1, accumulatedFailures);
        }

        if (!cb.allowRequest()) {
            accumulatedFailures.add(new CandidateFailure(
                    candidate.providerId(),
                    candidate.modelId(),
                    ErrorCategory.UNKNOWN,
                    "Circuit breaker limit exceeded",
                    0
            ));
            return executeCandidate(request, plan, candidateIndex + 1, accumulatedFailures);
        }

        Optional<InferenceProvider> providerOpt = providerRegistry.getProvider(candidate.providerId());
        if (providerOpt.isEmpty()) {
            cb.recordFailure();
            accumulatedFailures.add(new CandidateFailure(
                    candidate.providerId(),
                    candidate.modelId(),
                    ErrorCategory.NON_RETRYABLE_PROVIDER_ERROR,
                    "Provider adapter not registered",
                    0
            ));
            return executeCandidate(request, plan, candidateIndex + 1, accumulatedFailures);
        }

        InferenceProvider provider = providerOpt.get();
        AtomicInteger attemptCounter = new AtomicInteger(0);

        return Mono.defer(() -> {
            int attempt = attemptCounter.incrementAndGet();
            long attemptStart = System.currentTimeMillis();
            return provider.invoke(request)
                    .map(response -> {
                        cb.recordSuccess();
                        long duration = System.currentTimeMillis() - attemptStart;
                        RoutingInfo routingInfo = new RoutingInfo(candidate.providerId(), candidate.modelId(), attempt, duration);
                        return normalizer.normalize(response, routingInfo);
                    });
        })
        .retryWhen(reactor.util.retry.Retry.from(companion -> companion.flatMap(signal -> {
            Throwable failure = signal.failure();
            int attempt = attemptCounter.get();
            if (attempt >= plan.policy().retryPolicy().maxAttempts()) {
                return Mono.error(failure);
            }
            if (failure instanceof ProviderException pe && pe.getCategory().isRetryable()) {
                cb.recordFailure();
                long backoff = plan.policy().retryPolicy().computeBackoff(attempt - 1);
                return Mono.delay(java.time.Duration.ofMillis(backoff));
            }
            return Mono.error(failure);
        })))
        .onErrorResume(error -> {
            cb.recordFailure();

            ErrorCategory category = ErrorCategory.UNKNOWN;
            String message = error.getMessage();
            if (error instanceof ProviderException pe) {
                category = pe.getCategory();
                if (pe.getProviderMessage() != null) {
                    message = pe.getProviderMessage();
                }
            }

            accumulatedFailures.add(new CandidateFailure(
                    candidate.providerId(),
                    candidate.modelId(),
                    category,
                    message,
                    attemptCounter.get()
            ));

            return executeCandidate(request, plan, candidateIndex + 1, accumulatedFailures);
        });
    }

    private Flux<InferenceChunk> executeStreamingCandidate(
            InferenceRequest request,
            ExecutionPlan plan,
            int candidateIndex,
            List<CandidateFailure> accumulatedFailures) {

        if (candidateIndex >= plan.candidates().size()) {
            return Flux.error(new CandidateExhaustedException("All candidates exhausted", accumulatedFailures));
        }

        ScoredCandidate candidate = plan.candidates().get(candidateIndex);
        CircuitBreaker cb = cbRegistry.getOrCreate(candidate.providerId(), candidate.modelId());

        if (cb.getState() == CircuitBreakerState.OPEN) {
            accumulatedFailures.add(new CandidateFailure(
                    candidate.providerId(),
                    candidate.modelId(),
                    ErrorCategory.UNKNOWN,
                    "Circuit breaker open",
                    0
            ));
            return executeStreamingCandidate(request, plan, candidateIndex + 1, accumulatedFailures);
        }

        if (!cb.allowRequest()) {
            accumulatedFailures.add(new CandidateFailure(
                    candidate.providerId(),
                    candidate.modelId(),
                    ErrorCategory.UNKNOWN,
                    "Circuit breaker limit exceeded",
                    0
            ));
            return executeStreamingCandidate(request, plan, candidateIndex + 1, accumulatedFailures);
        }

        Optional<InferenceProvider> providerOpt = providerRegistry.getProvider(candidate.providerId());
        if (providerOpt.isEmpty()) {
            cb.recordFailure();
            accumulatedFailures.add(new CandidateFailure(
                    candidate.providerId(),
                    candidate.modelId(),
                    ErrorCategory.NON_RETRYABLE_PROVIDER_ERROR,
                    "Provider adapter not registered",
                    0
            ));
            return executeStreamingCandidate(request, plan, candidateIndex + 1, accumulatedFailures);
        }

        InferenceProvider provider = providerOpt.get();
        AtomicBoolean emitted = new AtomicBoolean(false);
        AtomicInteger attemptCounter = new AtomicInteger(0);

        return Flux.defer(() -> {
            int attempt = attemptCounter.incrementAndGet();
            long attemptStart = System.currentTimeMillis();
            emitted.set(false);

            return provider.invokeStreaming(request)
                    .doOnNext(chunk -> {
                        emitted.set(true);
                        cb.recordSuccess();
                    })
                    .map(chunk -> {
                        if (chunk.done()) {
                            long duration = System.currentTimeMillis() - attemptStart;
                            RoutingInfo routingInfo = new RoutingInfo(candidate.providerId(), candidate.modelId(), attempt, duration);
                            return new InferenceChunk(chunk.delta(), true, chunk.usage(), routingInfo);
                        }
                        return chunk;
                    });
        })
        .retryWhen(reactor.util.retry.Retry.from(companion -> companion.flatMap(signal -> {
            Throwable failure = signal.failure();
            int attempt = attemptCounter.get();
            if (emitted.get()) {
                return Mono.error(failure); // No retries/fallback after first chunk emitted
            }
            if (attempt >= plan.policy().retryPolicy().maxAttempts()) {
                return Mono.error(failure);
            }
            if (failure instanceof ProviderException pe && pe.getCategory().isRetryable()) {
                cb.recordFailure();
                long backoff = plan.policy().retryPolicy().computeBackoff(attempt - 1);
                return Mono.delay(java.time.Duration.ofMillis(backoff));
            }
            return Mono.error(failure);
        })))
        .onErrorResume(error -> {
            if (emitted.get()) {
                return Flux.error(error); // Propagate error directly if stream has started
            }

            cb.recordFailure();

            ErrorCategory category = ErrorCategory.UNKNOWN;
            String message = error.getMessage();
            if (error instanceof ProviderException pe) {
                category = pe.getCategory();
                if (pe.getProviderMessage() != null) {
                    message = pe.getProviderMessage();
                }
            }

            accumulatedFailures.add(new CandidateFailure(
                    candidate.providerId(),
                    candidate.modelId(),
                    category,
                    message,
                    attemptCounter.get()
            ));

            return executeStreamingCandidate(request, plan, candidateIndex + 1, accumulatedFailures);
        });
    }
}
