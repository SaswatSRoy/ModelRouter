# Contributing to ModelRouter

Thank you for your interest in ModelRouter. This document explains how to contribute at each stage of the project.

## Project Status

ModelRouter is in the **design phase**. The architecture is defined in [RFC-001](docs/rfcs/RFC-001-routing-engine-decomposition.md), and the repository contains documentation, design documents, and project scaffolding. No production code has been written yet.

## How to Contribute Right Now

### Design Review

The most impactful contribution you can make today is reviewing the architecture:

1. Read [RFC-001: Routing Engine Decomposition](docs/rfcs/RFC-001-routing-engine-decomposition.md).
2. Open a GitHub Discussion (category: **Design**) with feedback, questions, or alternative proposals.
3. If you find a specific gap or inconsistency, open an Issue with the `design` label.

### Documentation

- Fix typos, improve clarity, or add missing context to any document in `docs/`.
- Propose new glossary entries via PR against [GLOSSARY.md](GLOSSARY.md).

### Issues and Discussions

- Browse open Issues for anything tagged `good first issue` or `help wanted`.
- Use GitHub Discussions for open-ended questions, feature ideas, or architectural debate.

---

## Future Code Contributions (Phase 1+)

Once implementation begins, all code contributions will follow this process.

### Fork and Branch

```bash
# Fork the repository on GitHub, then:
git clone https://github.com/<your-username>/ModelRouter.git
cd ModelRouter
git remote add upstream https://github.com/SaswatSRoy/ModelRouter.git
git checkout -b feat/your-feature-name
```

### Branch Naming

| Prefix     | Purpose                          |
|------------|----------------------------------|
| `feat/`    | New feature                      |
| `fix/`     | Bug fix                          |
| `docs/`    | Documentation only               |
| `refactor/`| Code restructuring, no behavior change |
| `test/`    | Adding or improving tests        |
| `chore/`   | Build, CI, dependency updates    |

### Pull Request Process

1. Ensure your branch is rebased on the latest `main`.
2. All CI checks must pass (build, tests, linting).
3. PRs require at least one maintainer approval.
4. Squash-merge is the default merge strategy.
5. Link the relevant Issue in the PR description.

---

## Code Standards

These standards will be enforced once implementation begins:

| Area            | Standard                                      |
|-----------------|-----------------------------------------------|
| Language        | Java 21 (LTS) — use records, sealed interfaces, pattern matching |
| Framework       | Spring Boot 3 / Spring WebFlux                |
| Build           | Gradle (Kotlin DSL)                           |
| Testing         | JUnit 5 + Testcontainers for integration tests |
| Coverage        | Minimum 80% line coverage on core modules     |
| Style           | Google Java Format, enforced by CI            |
| Static Analysis | SpotBugs + Error Prone                        |
| API Docs        | Javadoc on all public interfaces              |

### Provider Adapter Authoring

If you are implementing a new Provider Adapter, follow the contract specification in [`docs/provider-contract.md`](docs/provider-contract.md) (to be published with Phase 1). Every adapter must:

- Implement the `InferenceProvider` SPI.
- Support both blocking and streaming invocation.
- Include integration tests using Testcontainers or WireMock.
- Ship with a default configuration in `application.yml`.

---

## Commit Message Convention

This project uses [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

**Types:** `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`, `perf`.

**Examples:**

```
feat(planner): add LowestLatencyStrategy implementation

Implements the LowestLatencyStrategy that ranks scored candidates
by latency EWMA ascending. Breaks ties by cost/token.

Closes #42
```

```
fix(runtime): handle null response body from Anthropic adapter

The Anthropic API returns HTTP 200 with an empty body on certain
rate-limit scenarios. The adapter now treats this as a retryable error.

Signed-off-by: Jane Doe <jane@example.com>
```

---

## DCO Sign-Off

All commits must be signed off under the [Developer Certificate of Origin (DCO)](https://developercertificate.org/).

Add `-s` to your commit command:

```bash
git commit -s -m "feat(planner): add LowestLatencyStrategy implementation"
```

This appends a `Signed-off-by` trailer certifying that you have the right to submit the contribution under the project's license.

---

## Code of Conduct

All contributors must follow the [Code of Conduct](CODE_OF_CONDUCT.md). Violations can be reported to **modelrouter-conduct@googlegroups.com**.

---

## Questions?

Open a GitHub Discussion or reach out to the maintainers. We are happy to help you find a good first contribution.
