# ModelRouter — Domain Model

This document provides the canonical domain language and structural rules for the ModelRouter project. It defines the core domain entities, their responsibilities, lifecycle, boundaries, and the invariants that must never be broken.

Every architectural decision and future RFC must reference this document and align with its terminology and principles.

## 1. Project Vocabulary

The system is designed around explicit terminology to avoid conceptual overlap. Ensure all documentation and code use these exact terms.

## 2. Core Domain Objects

### **InferenceRequest**
The canonical request object accepted by the Gateway. Encapsulates the prompt, required capabilities, model preferences, and tenant context. The request represents *intent*, not a specific destination.

### **RoutingPolicy**
The deterministic object resolved by the Policy Engine that dictates constraints (cost, latency, privacy) and routing preferences.

### **ExecutionPlan**
An immutable ordered sequence of `ProviderCandidate`s produced by the Execution Planner. This replaces concepts like "fallback chain" or "ranked candidate list". The Execution Runtime consumes this plan.

### **ExecutionRuntime**
The subsystem responsible for consuming the `ExecutionPlan`, executing requests, managing retries and fallbacks, enforcing circuit breaker states, and normalizing responses.

### **ProviderAdapter**
A concrete implementation of the inference provider contract. It encapsulates all provider-specific SDKs, error mappings, and HTTP client interactions. 

### **ProviderCapability**
A discrete feature or requirement (e.g., `128k-context`, `function-calling`, `vision`) that providers declare and `InferenceRequest`s require.

### **ProviderCandidate**
A `(provider, model)` pair evaluated for eligibility. Enriched during planning with live scores to become a scored candidate, and finally placed into the `ExecutionPlan`.

### **CapabilityRegistry**
A provider-neutral repository mapping models and providers to their supported `ProviderCapability` sets, cost models, and structural constraints.

### **RequestAnalyzer**
A pluggable component responsible for analyzing an `InferenceRequest` to determine semantic intent, complexity, or safety. Formerly referred to as "Intent Classifier," this generalized analyzer informs routing policies and optimization steps.

### **ContextOptimizer**
A policy-driven pre-execution pipeline that applies transformations to the prompt context (e.g., summarization, compression, or trimming) before routing occurs.

### **ContextPolicy**
Configuration defining if and how context optimization should occur (e.g., max token limits for summarization, strict truncation).

### **FeedbackEvent**
An append-only signal from client applications (e.g., user satisfaction, task success metric, error report) that is ingested by ModelRouter to inform historical provider scoring and future adaptive routing.

### **AdaptiveRoutingEngine**
An advanced routing strategy component that adjusts provider rankings dynamically based on historical execution data and ingested `FeedbackEvent`s.

### **InferenceResponse**
The canonical, normalized response object returned to the client, encapsulating the model output, token usage, and optional tracing metadata.

## 3. Domain Boundaries and Ownership

Each subsystem owns exactly one responsibility. No subsystem should have overlapping responsibilities, and data boundaries are strict.

* **Request Analysis** → Owned by `RequestAnalyzer`. Outputs augmented request context and intent metrics.
* **Policy Engine** → Resolves constraints and overrides. Outputs `RoutingPolicy`.
* **Execution Planner** → Owns candidate selection and scoring. Consumes `RoutingPolicy` and outputs `ExecutionPlan`.
* **Execution Runtime** → Owns invocation orchestration, fallback, and circuit breaking. Consumes `ExecutionPlan` and outputs `InferenceResponse`.
* **Provider Adapter** → Owns provider-specific communication and translation. Implements the SPI and exposes only provider-agnostic data to the Execution Runtime.

## 4. Architectural Invariants

To maintain long-term maintainability, the following invariants are enforced:

1. **Applications never select providers**: Applications express intent via `InferenceRequest`. The Control Plane owns destination routing.
2. **ExecutionPlan is immutable**: Once the Execution Planner generates an `ExecutionPlan`, it cannot be altered by the Execution Runtime. 
3. **RoutingPolicy is deterministic**: Given the same tenant configuration and request overrides, the Policy Engine will always resolve the same `RoutingPolicy`.
4. **ProviderAdapters never leak provider-specific models**: Adapters map strictly to `InferenceRequest` and `InferenceResponse`. No upstream provider objects cross the adapter boundary.
5. **FeedbackEvents are append-only**: Feedback signals are recorded as an immutable log for offline processing or scoring; they do not immediately mutate in-flight request state.
6. **CapabilityRegistry is provider-neutral**: Capabilities are defined abstractly (e.g., `vision`, `tool-use`). The registry does not encode provider-specific implementation details.

## 5. Evolution Principles

- **Prefer composition over inheritance**: Interfaces and SPIs govern boundaries. Pluggable components like `RequestAnalyzer` and `ContextOptimizer` must remain independent of core routing logic.
- **Avoid premature specialization**: Generalize abstractions when obvious (e.g., `RequestAnalyzer` over `IntentClassifier`) but avoid speculative abstractions for unproven needs.
- **Strict Data Contracts**: Subsystems communicate via immutable data structures.
