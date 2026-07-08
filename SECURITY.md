# Security Policy

This document describes how to report security vulnerabilities in ModelRouter and what to expect after reporting.

> **Note:** This is the vulnerability disclosure policy. For the security architecture and design of ModelRouter itself, see [`docs/security.md`](docs/security.md).

## Reporting a Vulnerability

**Do not open a public GitHub Issue for security vulnerabilities.**

Instead, report vulnerabilities by emailing:

**modelrouter-security@googlegroups.com**

Include the following in your report:

- **Description** of the vulnerability.
- **Steps to reproduce** or a proof-of-concept.
- **Impact assessment** — what an attacker could achieve.
- **Affected component** — which module, adapter, or subsystem is involved.
- **Suggested fix** (optional but appreciated).

### PGP Encryption

If you need to send sensitive details, request our PGP public key by emailing the address above with the subject line `PGP Key Request`.

## Response SLA

| Stage | Target |
|-------|--------|
| Acknowledgment | Within **72 hours** of receipt |
| Initial triage and severity assessment | Within **7 days** |
| Fix or mitigation plan communicated | Within **30 days** for critical/high severity |

We will keep you informed of progress and coordinate disclosure timing with you.

## Scope

The following are **in scope** for this security policy:

- Authentication and authorization bypass (API key leakage, JWT validation flaws).
- Tenant isolation failures (cross-tenant data access).
- Privacy Tier enforcement bypass (data routed to cloud providers when `LOCAL_ONLY` is specified).
- Injection attacks against the routing pipeline or provider adapters.
- Denial-of-service vulnerabilities in the hot path (e.g., unbounded resource consumption).
- Secrets exposure (API keys, credentials in logs, config, or responses).
- Dependency vulnerabilities with a demonstrated exploit path in ModelRouter.

The following are **out of scope**:

- Vulnerabilities in upstream provider APIs (report to the respective provider).
- Social engineering attacks.
- Issues requiring physical access to infrastructure.
- Denial-of-service via volumetric network flooding (infrastructure-level concern).
- Vulnerabilities in dependencies without a demonstrated impact on ModelRouter.

## Supported Versions

| Version | Supported |
|---------|-----------|
| `main` (development) | ✅ Yes |
| Latest release | ✅ Yes |
| Older releases | ❌ Best-effort only |

As ModelRouter is currently in the design phase with no released versions, security reports against the `main` branch and all design documents are accepted.

## Disclosure Policy

We follow coordinated disclosure:

1. The reporter and maintainers agree on a disclosure timeline (default: **90 days** from report).
2. A fix is developed and tested.
3. A security advisory is published via GitHub Security Advisories.
4. The reporter is credited (unless they prefer anonymity).

## Recognition

We gratefully acknowledge security researchers who report vulnerabilities responsibly. With your permission, we will credit you in the security advisory and in a `SECURITY-CREDITS.md` file.
