# Changelog: Observability & ELK PR

Summary of changes from the observability proposal implementation and ELK integration.

## Observability Stack

- **Prometheus** (port 9412) — metrics from Actuator and exporters (Redis 9121, Elasticsearch 9114, Kafka 9308)
- **Grafana** (port 3000) — Pet Shop Overview, Infrastructure (Redis, ES, Kafka, JVM, Prometheus targets)
- **Zipkin** (port 9411) — distributed tracing via Kafka
- **Spring Boot Admin** (port 8089) — service monitoring
- **Config Server** (port 8888) — centralized configuration
- **Elasticsearch** (port 9200) + **Kibana** (port 5601) — log analytics
- **Kafka Connect** (port 8083) — Elasticsearch Sink for `application-logs` topic

## New Components

| Component | Description |
|-----------|-------------|
| **config-server** | Spring Cloud Config Server, native backend, serves Query, Inventory, Admin |
| **elk/** | ELK init (Dockerized): topic creation, connector registration, Kibana data view |
| **profiling/** | Gatling load tests; `./start.sh profile` or `cd profiling && ./run.sh 60 2` |
| **redis-exporter, elasticsearch-exporter, kafka-exporter** | Prometheus scrapes infrastructure metrics |

## Log Distribution to Kafka

- **Enriched JSON logs** (LogstashLayout): `@timestamp`, `level`, `logger_name`, `message`, `thread_name`, `traceId`, `spanId`, `parentSpanId`, `service`, `environment`, `host`, `stack_trace` (errors)
- **Trace correlation**: Copy `traceId` from Zipkin → filter in Kibana to see logs across Query and Inventory
- **Kafka appender** (optional profile `kafka-logging`): enabled by default in Docker
- **Topic**: `application-logs` → Kafka Connect → Elasticsearch → Kibana
- **Docs**: [LOGGING_KAFKA.md](LOGGING_KAFKA.md), [ELK_LOGGING.md](ELK_LOGGING.md)

## ELK Initialization (Dockerized)

- **elk/init/** folder: Dockerfile, docker-init.sh, dashboards/ with ndjson for auto-import
- **elk-init** container runs during `docker compose up`: creates topic, registers connector, provisions Kibana data view, imports `elk/init/dashboards/*.ndjson`
- Bundled **log-overview.ndjson** dashboard (empty panels; add visualizations via Kibana Lens)
- Query and Inventory **depend on elk-init** completing before they start
- Single-threaded, blocking `docker compose up` — no background scripts

## Tests & Coverage

- **80% instruction coverage** enforced via JaCoCo `check` goal in Query and Inventory
- **Resilience4jIntegrationTest** (Inventory): fixed mock matcher (`any(String.class)` instead of `eq(INVENTORY_URL)`) for RestTemplate
- All tests pass; coverage: Query ~86%, Inventory ~82%

## Configuration Changes

- **Circuit breaker** (OrderService): `ignoreExceptions` includes `HttpClientErrorException$NotFound` (404)
- **gRPC**: version alignment to 1.68.2; `@GrpcService` on InventoryGrpcService
- **Config Server**: `spring.config.import=optional:configserver:http://localhost:8888`; local fallbacks for tests

## Scripts

- **start.sh**: phased execution (Building, Port check, Building Docker images, Starting stack); errors report failed phase
- **start.sh**: pre-build check for `elk/init` directory; `profile` mode for load testing
- **Maven wrapper**: togglable via `--mvnw` or `USE_MVNW`; auto-enabled in CI/cloud

## Docker Best Practices

- **Multi-stage Dockerfiles**: Layered JAR extraction, non-root user, OCI labels, readiness healthchecks
- **Startup ordering**: 7 layers (infra → config → exporters → observability → ELK → admin → microservices)
- **stop_grace_period**: 30s for Spring Boot services (graceful shutdown)
- **version**: removed from docker-compose files (obsolete in Compose V2, avoids warnings)
- **Docs**: [DOCKER.md](DOCKER.md)

## Documentation Added/Updated

| Doc | Purpose |
|-----|---------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | System overview, Docker startup, log flow, pet adoption, order sync, trace correlation |
| [CONFIG_SERVER.md](CONFIG_SERVER.md) | Config Server setup and usage |
| [DOCKER.md](DOCKER.md) | Container best practices, startup order, health checks |
| [ELK_LOGGING.md](ELK_LOGGING.md) | Elasticsearch, Kibana, Kafka Connect, dashboards |
| [LOGGING_KAFKA.md](LOGGING_KAFKA.md) | Log distribution, trace enrichment |
| [KIBANA_DASHBOARDS_PROPOSAL.md](KIBANA_DASHBOARDS_PROPOSAL.md) | Proposed Kibana dashboards |
| [PROFILING.md](PROFILING.md) | Load testing with Gatling |
| [GRPC_IMPLEMENTATION.md](GRPC_IMPLEMENTATION.md) | gRPC rollout for Query ↔ Inventory |
| [SCHEMA_REGISTRY_EUREKA_CONFIG_PROPOSAL.md](SCHEMA_REGISTRY_EUREKA_CONFIG_PROPOSAL.md) | Schema Registry, Eureka, Config evaluation |
