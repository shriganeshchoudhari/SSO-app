# Infrastructure

## Reference Architecture (K8s)
- Ingress + TLS termination (cert-manager)
- Namespace: iam
- Deployments: auth-server, admin-ui, account-ui
- Stateful services: Postgres (managed preferred), Infinispan or Redis (session/cache)
- Config: ConfigMaps; Secrets for sensitive data
- Storage: PVCs for DB (encrypted)
- Autoscaling: HPA based on CPU/latency
- NetworkPolicies: restrict east-west; only necessary ports
- Observability: OpenTelemetry Collector, Prometheus, Grafana
- Logging: Centralized (ELK/Opensearch)

## CI/CD
- Build pipelines: unit/integration tests, SAST/DAST
- Container scan (Trivy/Grype)
- Image signing (cosign), provenance (SLSA level target)

## Cost/Capacity
- Size for P50, P95 projected auth traffic
- Scale-out horizontally; limit cache/stateful resources prudently
