# Changelog: Observability & ELK PR

Summary of changes from the observability proposal implementation and ELK integration.

## Observability Stack

- **Prometheus** (port 9412) — metrics scraping from Actuator
- **Grafana** (port 3000) — provisioned dashboards: Pet Shop Overview, Infrastructure
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

## Log Distribution to Kafka

- **Structured JSON logs** (Logstash encoder): `@timestamp`, `level`, `logger`, `message`, `traceId`, `spanId`, `service`
- **Kafka appender** (optional profile `kafka-logging`): enabled by default in Docker
- **Topic**: `application-logs` → Kafka Connect → Elasticsearch → Kibana
- **Docs**: [LOGGING_KAFKA.md](LOGGING_KAFKA.md), [ELK_LOGGING.md](ELK_LOGGING.md)

## ELK Initialization (Dockerized)

- **elk-init** container runs during `docker compose up`: creates topic, registers connector, provisions Kibana data view
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

- **start.sh**: `profile` mode for load testing; ELK ports (5601, 8083, 9200) in PORTS_FULL
- **Maven wrapper**: togglable via `--mvnw` or `USE_MVNW`; auto-enabled in CI/cloud

## Documentation Added/Updated

| Doc | Purpose |
|-----|---------|
| [CONFIG_SERVER.md](CONFIG_SERVER.md) | Config Server setup and usage |
| [ELK_LOGGING.md](ELK_LOGGING.md) | Elasticsearch, Kibana, Kafka Connect, dashboards |
| [LOGGING_KAFKA.md](LOGGING_KAFKA.md) | Log distribution to Kafka |
| [PROFILING.md](PROFILING.md) | Load testing with Gatling |
| [GRPC_IMPLEMENTATION.md](GRPC_IMPLEMENTATION.md) | gRPC rollout for Query ↔ Inventory |
| [SCHEMA_REGISTRY_EUREKA_CONFIG_PROPOSAL.md](SCHEMA_REGISTRY_EUREKA_CONFIG_PROPOSAL.md) | Schema Registry, Eureka, Config evaluation |
