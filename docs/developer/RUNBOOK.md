# Runbook — Common operational procedures

Last Updated: August 4, 2026

This runbook contains quick remediation steps for common issues in the ClaimAssist AI Platform. It is deliberately concise and references existing developer documentation for deeper details.

---

1) Service fails to start (CrashLoopBackOff)
- Check pod logs: `kubectl logs <pod-name> -n claimassist-core`
- Inspect startup exceptions in logs (missing env vars, DB connection failures)
- Verify environment variables and Kubernetes Secrets
- Verify database reachable (see `docs/developer/DATABASE.md`)
- For local dev, run the service locally with `mvn spring-boot:run` and inspect console logs

2) Database connectivity issues
- Check PostgreSQL pod/container: `kubectl get pods -n claimassist-infra`
- Verify credentials in Kubernetes Secret (see `k8s/configmap-secrets.yaml`)
- Run `psql` inside the DB pod and validate tables: `
  psql -U claimassist -d claims_db -c '\dt'`
- Check Flyway migration status and errors (see `docs/developer/DATABASE.md`)

3) Outbox backlog (many PENDING events)
- Check OutboxPublisher logs in Claims Service
- Query DB for oldest pending events: `SELECT * FROM outbox_events WHERE status='PENDING' ORDER BY created_at LIMIT 50;`
- Verify Kafka broker connectivity and topic availability (see `docs/developer/KAFKA.md`)
- Confirm publisher schedule and exceptions in logs

4) Kafka consumer lag
- Check consumer group lag: `kafka-consumer-groups --bootstrap-server <broker> --describe --group claims-service-consumers`
- Verify consumers are healthy and not stuck in processing
- If lag is due to slow processing, increase consumer concurrency or optimize handler
- Dead-letter topics (.DLT) contain messages that failed processing permanently (see `docs/developer/KAFKA.md`)

5) Authentication / JWT issues
- Verify Keycloak is running and realm is imported (`realm-export.json` exists)
- Validate issuer URI and JWKS endpoint accessibility
- Check clock skew between services and Keycloak
- Inspect token contents and roles (see `docs/developer/SECURITY.md`)

6) Observability checks
- Prometheus scrape endpoints: `/actuator/prometheus` on each service
- Trace lookup: Zipkin UI `http://zipkin:9411`
- Check logs in Kubernetes: `kubectl logs -f deployment/claims-service -n claimassist-core`

7) Emergency rollback (deploy issues)
- Use Helm rollback: `helm rollback claimassist-platform <revision> -n claimassist-core`
- Verify rollout status: `kubectl rollout status deployment/claims-service -n claimassist-core`

8) Contact & escalation
- On-call team or repository maintainers (document offline)

---

References
- Detailed architecture: `docs/developer/ARCHITECTURE.md`
- Kafka & messaging: `docs/developer/KAFKA.md`
- Database: `docs/developer/DATABASE.md`
- Deployment: `docs/developer/DEPLOYMENT.md`
- Security: `docs/developer/SECURITY.md`



