# Docker Container Best Practices

This document describes the Docker and Docker Compose setup following Spring Cloud container best practices.

## Microservice Dockerfiles

All Spring Boot services (Query, Inventory, Config Server, Admin Server) use a consistent pattern:

### Build Strategy

- **Multi-stage**: Builder stage extracts layers; runtime stage uses minimal Alpine base
- **Layered JAR**: Spring Boot layers copied in order of change frequency (dependencies → loader → snapshot-deps → application) for optimal cache reuse
- **Java 21**: Aligned with project `java.version`

### Security

- **Non-root user**: Run as `spring` (UID/GID 10001)
- **Minimal base**: `eclipse-temurin:21-jre-alpine`
- **OCI labels**: `org.opencontainers.image.*` for maintainability

### JVM Tuning

- `-XX:+UseContainerSupport`: Respect container memory limits
- `-XX:MaxRAMPercentage=75.0`: Use up to 75% of container memory for heap
- `-XX:InitialRAMPercentage=50.0`: Start with 50% for faster startup

### Graceful Shutdown

- **Exec form ENTRYPOINT**: Ensures Java receives SIGTERM
- **stop_grace_period: 30s**: Docker Compose waits up to 30s before SIGKILL
- Spring Boot handles SIGTERM: stops accepting new requests, drains in-flight work

### Health Checks

- **Readiness probe**: `/actuator/health/readiness` (Spring Boot 4 default)
- **Fallback**: `/actuator/health` for compatibility
- Used for **dependency ordering**: dependent services start only when upstream is healthy

## Docker Compose Startup Order

The full stack starts in seven layers:

| Layer | Services | Depends On |
|-------|----------|------------|
| 1 | redis, kafka, elasticsearch | (none) |
| 2 | config-server | (none) |
| 3 | redis-exporter, elasticsearch-exporter, kafka-exporter | Their backends (healthy) |
| 4 | zipkin, prometheus, grafana | kafka; redis-exporter; prometheus |
| 5 | kibana, kafka-connect, elk-init | elasticsearch; kafka+elasticsearch; kafka-connect+kibana |
| 6 | admin-server | config-server, kafka |
| 7 | query-microservice, inventory-microservice | config, redis, kafka, prometheus, zipkin, elk-init |

### Health Check Conditions

- `service_healthy`: Wait until the service's healthcheck reports healthy
- `service_started`: Wait only until the container has started (for exporters without healthchecks)
- `service_completed_successfully`: For one-off tasks (elk-init)

### Graceful Shutdown Order

When stopping (`docker compose down`), Compose stops services in reverse dependency order. Spring Boot services have `stop_grace_period: 30s` to allow in-flight requests to complete.

## Minimal vs Full Stack

- **Minimal** (`docker-compose-minimal.yml`): redis, kafka, redis-exporter, zipkin, prometheus. For running microservices locally.
- **Full** (`docker-compose.yml`): Complete stack including Config Server, Admin, Elasticsearch, Kibana, Kafka Connect, and both microservices.

## Building Images

```bash
# Build all
docker compose build

# Build single service
docker compose build query-microservice
```

Ensure JARs are built first: `./start.sh` runs `mvn package` before `docker compose build`.
