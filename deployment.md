# Deployment Model

See [diagrams/deployment-diagram.mmd](../diagrams/deployment-diagram.mmd).

## Deployables

| Deployable | Contains | Scaling profile |
|---|---|---|
| `modelrouter-gateway` | `router-ingress` + `router-core` + all `provider-adapters` + `router-cache` client + `router-security` | Horizontally autoscaled, stateless, the hot path |
| `modelrouter-admin` | `router-admin` (CQRS command side, policy/provider/tenant CRUD, audit) | Low-traffic, can run at low replica count |
| `modelrouter-dashboard` | `router-dashboard` (Phase 2+) | Low-traffic, independent release cadence |

Splitting the gateway from admin/dashboard means the hot request path's release cadence, scaling, and blast radius are fully decoupled from the management plane's.

## Container Strategy

Every deployable ships as a minimal OCI image (JLink-trimmed JRE base, non-root user, distroless where feasible) built via Gradle's Docker plugin, with reproducible builds gated in CI. No deployable requires build-time secrets baked into the image; all runtime configuration is externalized (env vars / mounted config / secrets backend, per [docs/security.md](security.md)).

## Kubernetes Topology

```
Namespace: modelrouter
├── Deployment: modelrouter-gateway   (HPA: 3–50 replicas, scales on p99 latency + in-flight requests)
├── Deployment: modelrouter-admin     (2 replicas, no autoscale needed at expected load)
├── Deployment: modelrouter-dashboard (2 replicas)
├── Service + Ingress (gateway)       TLS termination, or passthrough if mesh-managed
├── ConfigMap: non-secret runtime config
├── Secret refs → external secrets backend (Vault / cloud KMS / K8s Secrets)
├── ServiceMonitor (Prometheus Operator) → scrape gateway + admin metrics endpoints
└── NetworkPolicy: gateway can reach egress (providers) + Redis + Kafka + Postgres(admin only reaches Postgres)
```

External, not modeled/owned by ModelRouter's own manifests:

- **Redis** — managed service or operator-managed cluster, shared cache + rate-limit + health-state store.
- **PostgreSQL** — managed service, policy/tenant/provider registry + audit log.
- **Kafka** — shared cluster or managed offering (MSK/Confluent/Redpanda), usage-event pipeline.
- **OTel Collector, Prometheus, Grafana, Langfuse** — standard observability stack, ModelRouter exports to it rather than bundling it.

## Helm Chart (Phase 1 deliverable)

A single Helm chart (`charts/modelrouter`) with subcharts/values for gateway, admin, and dashboard, parameterized for: replica counts, resource requests/limits, autoscaling thresholds, external dependency connection strings, secrets-backend selection, and provider default configuration. This is the primary supported install path for v1; a Kubernetes Operator with CRDs is a Phase 3 goal (RFC-001 §7) built once the resource model has proven stable through the REST admin API.

## Scaling Behavior

- **Horizontal, stateless scaling of the gateway** is the primary scaling lever (ARCHITECTURE.md §6) — no session affinity required, any pod serves any request.
- **HPA metrics**: CPU as a baseline, plus custom Prometheus metrics (in-flight request count, p99 routing overhead) for more accurate scale-out ahead of saturation, since inference-proxying load doesn't correlate tightly with CPU the way typical web workloads do.
- **Redis and Kafka scale independently** per standard operational practice for those systems; ModelRouter's connection pooling and backpressure settings are tuned to degrade gracefully (queueing, then shedding via rate limiting) rather than cascading failure if either is under strain.

## Local Development

`docker-compose.yml` (Phase 1 deliverable) brings up gateway + Redis + Postgres + Kafka + a local OTel collector, with mock/sandboxed provider adapters where real API keys aren't available, so a contributor can run the full request path locally without cloud credentials.

## Multi-Region (Phase 3+)

Gateway deployments per region, each region's `CandidateEnumerator`/scoring aware of regional provider endpoints and latency, with policy able to express regional constraints (data residency) alongside privacy tier. Redis/Postgres/Kafka multi-region topology is an operational decision left to the adopter's existing infrastructure practices, not prescribed by ModelRouter itself in v1.
