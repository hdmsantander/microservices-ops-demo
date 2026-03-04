# Spring Cloud Config Server

## Overview

The microservices-ops-demo uses **Spring Cloud Config Server** for centralized configuration. Query, Inventory, and Admin Server fetch their config from the Config Server at startup.

## Architecture

```
                    ┌─────────────────────┐
                    │   Config Server     │
                    │   localhost:8888    │
                    │   (native backend)  │
                    └──────────┬──────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
         ▼                     ▼                     ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ Query Service   │  │ Inventory Svc    │  │ Admin Server    │
│ :8086           │  │ :8085, :9090     │  │ :8089           │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

## Config Server

- **Port**: 8888
- **Backend**: Native (classpath) — config files in `config-server/src/main/resources/config/`
- **Endpoints**: `/actuator/health`, `/actuator/info`, `/{application}/{profile}` (Config Server API)

### Config Files

| File | Purpose |
|------|---------|
| `config/application.yml` | Shared config: Kafka binder, tracing, resilience4j base |
| `config/query-microservice.yml` | Query-specific: port, datasource, redis, reservation, inventory, gRPC |
| `config/inventory-microservice.yml` | Inventory-specific: port, gRPC server, datasource, admin |
| `config/admin-server.yml` | Admin-specific: port |

## Client Configuration

Each microservice uses `spring.config.import`:

```yaml
spring:
  application:
    name: query-microservice
  config:
    import: optional:configserver:http://localhost:8888
```

- **optional:** — if Config Server is unreachable, the app falls back to local `application.yml`
- **URL**: Override via `SPRING_CONFIG_IMPORT` or `spring.config.import` for different environments

## Startup Order (Docker)

1. Redis, Kafka, Prometheus, Zipkin, Grafana
2. **Config Server** (must be healthy before apps)
3. Admin Server, Query, Inventory

## Running Without Config Server

- **Tests**: Set `spring.cloud.config.enabled: false` in `application-test.yml`
- **Local dev**: Config Server is optional; services use local config when Config Server is down
- **Docker**: Config Server is built and started by `./start.sh` (full mode)

## Environment Overrides

Override Config Server URL for different environments:

```bash
# Docker Compose
SPRING_CONFIG_IMPORT=optional:configserver:http://config-server:8888

# Or in application.yml per profile
spring:
  config:
    import: optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}
```

## Adding New Config

1. Edit `config-server/src/main/resources/config/{application}.yml`
2. Rebuild Config Server: `cd config-server && ./mvnw package -DskipTests`
3. Restart clients, or use `@RefreshScope` + `/actuator/refresh` for dynamic updates (advanced)

## Related

- [SCHEMA_REGISTRY_EUREKA_CONFIG_PROPOSAL.md](SCHEMA_REGISTRY_EUREKA_CONFIG_PROPOSAL.md) — evaluation that led to Config Server adoption
