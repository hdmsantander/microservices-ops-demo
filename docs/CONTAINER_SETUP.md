# Container Setup and Environment

This document describes the Docker and Docker Compose setup, environment configuration, and startup flow for the microservices-ops-demo.

## Environment Overview

All services use `network_mode: host` for direct access to localhost ports. Resource limits are set via `deploy.resources.limits.memory` for predictability.

### Port Reference (Full Stack)

| Port | Service |
|------|---------|
| 6379 | Redis |
| 8888 | Config Server |
| 8085 | Inventory microservice |
| 8086 | Query microservice |
| 8089 | Admin Server |
| 9090 | Inventory gRPC |
| 9092 | Kafka |
| 9200 | Elasticsearch |
| 5601 | Kibana |
| 8084 | Kafka Connect REST |
| 9411 | Zipkin |
| 9412 | Prometheus |
| 3000 | Grafana |
| 3030 | Kafka Web UI (landoop) |
| 8081 | Schema Registry (landoop) |
| 9121 | redis-exporter |
| 9114 | elasticsearch-exporter |
| 9308 | kafka-exporter |

### Key Environment Variables

| Variable | Service | Default | Purpose |
|----------|---------|---------|---------|
| `SPRING_PROFILES_ACTIVE` | Query, Inventory | `development,kafka-logging` (Docker) | Enables Kafka logging to ELK |
| `SPRING_CONFIG_IMPORT` | Query, Inventory, Admin | `optional:configserver:http://localhost:8888` | Config Server URL |
| `KAFKA_BOOTSTRAP_SERVERS` | Zipkin, microservices | `localhost:9092` | Kafka broker |
| `ELASTICSEARCH_HOSTS` | Kibana | `["http://localhost:9200"]` | Elasticsearch URL |
| `CONNECT_LISTENERS` | Kafka Connect | `http://0.0.0.0:8084` (via patch) | REST API binding |
| `GF_SECURITY_ADMIN_USER` / `GF_SECURITY_ADMIN_PASSWORD` | Grafana | admin/admin | Demo only |

## Startup Flow

The full stack starts in seven layers. Each layer waits for its dependencies to be healthy.

```
Layer 1: redis, kafka, elasticsearch (infrastructure)
Layer 2: config-server
Layer 3: redis-exporter, elasticsearch-exporter, kafka-exporter
Layer 4: zipkin, prometheus, grafana
Layer 5: kibana, kafka-connect, elk-init (elk-init runs once, then exits)
Layer 6: admin-server
Layer 7: query-microservice, inventory-microservice
```

**elk-init** creates the `application-logs` topic, registers the Kafka Connect Elasticsearch Sink connector, provisions the Kibana data view, and imports dashboards. Query and Inventory start only after elk-init completes successfully.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full diagram.

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

## Health Check Conditions

- `service_healthy`: Wait until the service's healthcheck reports healthy
- `service_started`: Wait only until the container has started (for exporters without healthchecks)
- `service_completed_successfully`: For one-off tasks (elk-init)

## Minimal vs Full Stack

- **Minimal** (`docker-compose-minimal.yml`): redis, kafka, redis-exporter, zipkin, prometheus. For running microservices locally.
- **Full** (`docker-compose.yml`): Complete stack including Config Server, Admin, Elasticsearch, Kibana, Kafka Connect, and both microservices.

## Building Images

```bash
# Build all
docker compose -f docker-compose.yml build

# Build single service
docker compose -f docker-compose.yml build query-microservice
```

Ensure JARs are built first: `./start.sh` runs `mvn package` before `docker compose build`.

## Resource Limits (Full Stack)

| Service | Memory Limit |
|---------|--------------|
| redis | 256M |
| kafka | 1G |
| elasticsearch | 1G |
| kibana | 1G |
| kafka-connect | 1.5G |
| grafana | 512M |
| config-server, admin-server | 512M each |
| query-microservice, inventory-microservice | 512M each |

Total full stack: ~6–7 GB RAM limit.
