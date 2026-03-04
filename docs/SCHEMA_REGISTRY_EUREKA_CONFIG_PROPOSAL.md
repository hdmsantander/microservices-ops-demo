# Proposal: Schema Registry, Eureka & Spring Config Server

## Document Info

| Version | Date | Summary |
|---------|------|---------|
| 1.0 | 2025-03-04 | Initial evaluation of Schema Registry, Eureka, and Spring Config Server for microservices-ops-demo |

**Related docs:** [IMPROVEMENT_REPORT.md](IMPROVEMENT_REPORT.md), [GRPC_IMPLEMENTATION.md](GRPC_IMPLEMENTATION.md), [TESTING.md](TESTING.md)

---

## 1. Executive Summary

This document evaluates whether **Schema Registry**, **Eureka** (service discovery), and **Spring Config Server** are a good fit for the microservices-ops-demo project. The analysis considers:

- Current architecture (2 microservices, Kafka, gRPC/REST, Redis)
- Version compatibility (Spring Boot 4.0.3, Spring Cloud 2025.1.0)
- Testability and added overhead
- Future implementations (scaling, multi-environment, schema evolution)

---

## 2. Current State

### 2.1 Stack Overview

| Component | Version | Purpose |
|-----------|---------|---------|
| Spring Boot | 4.0.3 | Microservices runtime |
| Spring Cloud | 2025.1.0 (Oakwood) | Stream, Circuit Breaker |
| Kafka | landoop/fast-data-dev | Events (order-events-v1, adoption-events-v1, etc.) |
| Redis | 7-alpine | Reservations (reserve-then-adopt) |
| gRPC | 1.68.2 | Query ↔ Inventory sync (GetInventory, GetOrder, Refresh) |
| REST | — | Fallback; PetStore API proxy |

### 2.2 Configuration

- **Per-service**: `application.yml` + `application-test.yml` in each microservice
- **Secrets/env**: Override via `SPRING_CLOUD_STREAM_KAFKA_BINDER_BROKERS`, `REDIS_*`, etc.
- **Hardcoded**: `inventory.service-url: http://localhost:8085`, `grpc.client.inventory.address: static://localhost:9090`

### 2.3 Kafka Events

| Topic | Producer | Consumer | Serialization |
|-------|----------|---------|---------------|
| order-events-v1 | Inventory | Query | JacksonJsonSerializer |
| adoption-events-v1 | Query | Inventory | JacksonJsonSerializer |
| adoption-congratulation-events-v1 | Inventory | (external) | JacksonJsonSerializer |

**Known issue** ([IMPROVEMENT_REPORT.md](IMPROVEMENT_REPORT.md)): Query and Inventory each define their own `OrderEvent`, `AdoptionEvent` classes — schema drift risk.

### 2.4 Service Discovery

- **Current**: Static URLs; Query knows Inventory at `localhost:8085` / `localhost:9090`
- **Scale**: Single instance per service (Docker `network_mode: host`)

---

## 3. Technology Evaluations

### 3.1 Schema Registry

#### What It Is

A central service that stores and versions schemas for Kafka messages. Producers/consumers fetch schemas by ID; messages carry schema ID instead of full schema. Supports Avro, JSON Schema, Protobuf.

#### Current Fit

- **landoop/fast-data-dev** already includes Confluent Schema Registry (typically port 8081).
- Project uses **JacksonJsonSerializer** — no Schema Registry integration today.
- **OrderEvent schema drift** is a documented pitfall; Schema Registry would enforce consistency.

#### Pros

| Benefit | Description |
|---------|-------------|
| Schema evolution | Backward/forward compatibility checks before deployment |
| Single source of truth | One schema per subject; no duplicate POJOs in Query vs Inventory |
| Validation | Rejects invalid messages at producer/consumer |
| Observability | Schema versioning aids debugging and audits |

#### Cons

| Drawback | Description |
|----------|-------------|
| Migration effort | Must switch from Jackson JSON to Avro or JSON Schema; shared schema module |
| landoop version | Bundled Schema Registry may be older; Confluent client version alignment needed |
| Test overhead | Integration tests need Schema Registry (Testcontainers) or schema-aware mocks |
| Latency | Extra network call to Schema Registry on first message per subject |

#### Version Compatibility

| Component | Requirement | Project |
|-----------|-------------|---------|
| Confluent Platform / Schema Registry | 5.x+ for JSON Schema | landoop/fast-data-dev (older bundle) |
| kafka-json-schema-serializer | Matches Schema Registry | Must align with landoop SR version |
| Spring Kafka | Supports custom serdes | ✅ 4.x (Spring Boot 4) |

**Risk**: landoop/fast-data-dev is not actively maintained; Schema Registry version may be outdated. Consider **confluentinc/cp-kafka** or **apache/kafka** + standalone Schema Registry for production-style demos.

#### Testability

- **Unit tests**: Continue mocking `StreamBridge`; no Schema Registry needed.
- **Integration tests**: Add Schema Registry container (e.g. `ConfluentContainer` with Schema Registry) or use `KafkaJsonSchemaSerializer` with `mock://` URL in tests.
- **Overhead**: Medium — extra container, schema registration in test setup.

#### Recommendation

| Dimension | Rating | Notes |
|-----------|--------|-------|
| **Fit for current demo** | Low–Medium | Solves schema drift but high migration cost |
| **Fit for future** | High | Valuable if event model grows or more services publish/consume |
| **Priority** | Defer | Prefer shared event API module + contract tests first (lower effort) |

**Alternative**: Create a shared `events-api` module with `OrderEvent`, `AdoptionEvent` POJOs used by both services. Less overhead than Schema Registry, addresses drift. Add Schema Registry when event evolution becomes critical.

---

### 3.2 Eureka (Service Discovery)

#### What It Is

Netflix Eureka: a REST-based service registry. Services register on startup; clients query the registry to resolve service names to instances (host:port). Supports client-side load balancing.

#### Current Fit

- **2 microservices**: Query and Inventory; both know each other’s addresses.
- **Static topology**: Docker Compose, `network_mode: host` — typically one instance per service.
- **gRPC**: Uses `static://localhost:9090`; REST uses `http://localhost:8085`.

#### Pros

| Benefit | Description |
|---------|-------------|
| Dynamic discovery | No hardcoded host:port; survives IP/port changes |
| Health-based routing | Unhealthy instances can be excluded |
| Load balancing | Client-side round-robin when multiple instances exist |
| Spring integration | `DiscoveryClient`, `@LoadBalanced` RestTemplate/WebClient |

#### Cons

| Drawback | Description |
|----------|-------------|
| Overkill for 2 services | Adds Eureka server + client deps; config complexity |
| Single-instance topology | No scaling today; discovery benefits minimal |
| gRPC static address | grpc-spring-boot-starter uses `static://`; Eureka integration non-trivial |
| Extra service | Port 8761; another container and health dependency |

#### Version Compatibility

| Component | Version | Status |
|-----------|---------|--------|
| spring-cloud-starter-netflix-eureka-server | 5.0.0 | ✅ In Spring Cloud 2025.1.0 |
| spring-cloud-starter-netflix-eureka-client | 5.0.0 | ✅ Compatible |
| Spring Boot | 4.0.3 | ✅ Supported |

#### Testability

- **Unit tests**: Mock `DiscoveryClient` or disable Eureka (`eureka.client.enabled=false`).
- **Integration tests**: Eureka server in Testcontainers or `@SpringBootTest` with `eureka.client.register-with-eureka=false`.
- **Overhead**: Low — standard Spring Cloud patterns.

#### Recommendation

| Dimension | Rating | Notes |
|-----------|--------|-------|
| **Fit for current demo** | Low | Only 2 services; static URLs sufficient |
| **Fit for future** | Medium | Becomes useful with 4+ services or Kubernetes (consider K8s native discovery instead) |
| **Priority** | Not recommended | Add when scaling or multi-instance deployment is required |

**Alternative**: If moving to Kubernetes, use **Kubernetes Service Discovery** (Spring Cloud Kubernetes) instead of Eureka.

---

### 3.3 Spring Config Server

#### What It Is

Centralized configuration server. Clients fetch config (e.g. from Git, filesystem, or Vault) at startup and optionally on refresh. Replaces scattered `application.yml` across services.

#### Current Fit

- Config spread across Query, Inventory, Admin Server: Kafka brokers, Redis, resilience4j, inventory URL, gRPC address, Zipkin, etc.
- [IMPROVEMENT_REPORT.md](IMPROVEMENT_REPORT.md) recommends Config Server for “Redis URL, Kafka brokers, feature flags.”

#### Pros

| Benefit | Description |
|---------|-------------|
| Centralized config | Single place for shared and per-service config |
| Environment-specific | `{application}-{profile}.yml` for dev/staging/prod |
| Encryption | Sensitive values encrypted at rest |
| Refresh | `@RefreshScope` for dynamic updates (limited) |
| Audit | Git backend = config change history |

#### Cons

| Drawback | Description |
|----------|-------------|
| Bootstrap / import | Must configure `spring.config.import=configserver:…` or bootstrap |
| Extra service | Config Server on port 8888; Git/native backend |
| Startup dependency | Clients block on Config Server availability |
| Test setup | Tests need local config or `spring.cloud.config.enabled=false` |

#### Version Compatibility

| Component | Version | Status |
|-----------|---------|--------|
| spring-cloud-config-server | 5.0.0 | ✅ In Spring Cloud 2025.1.0 |
| spring-cloud-starter-config | 5.0.0 | ✅ Compatible |
| Spring Boot 4 | 4.0.3 | ✅ Use `spring.config.import` (no legacy bootstrap) |

**Bootstrap**: Spring Boot 2.4+ prefers `spring.config.import=optional:configserver:http://localhost:8888`. Use `optional:` to allow fallback to local config when Config Server is unavailable.

#### Testability

- **Unit tests**: `spring.cloud.config.enabled=false` or `spring.config.import=optional:configserver:…` (Config Server can be down).
- **Integration tests**: Config Server in Docker or embedded; or local filesystem backend.
- **Overhead**: Medium — new module, repo structure for config files.

#### Recommendation

| Dimension | Rating | Notes |
|-----------|--------|-------|
| **Fit for current demo** | Medium–High | Reduces duplication; supports multi-environment |
| **Fit for future** | High | Essential for production-style deployments |
| **Priority** | Recommended (medium term) | Best ROI of the three for this project |

**Implementation approach**:
1. Add `config-server` module with `@EnableConfigServer`, Git or native backend.
2. Move shared config (Kafka, Redis, common resilience) to Config Server’s backing store (e.g. `config/` directory or Git repo).
3. Clients: `spring.config.import=optional:configserver:http://localhost:8888`.
4. Keep `application.yml` for service-specific defaults; override via Config Server.

---

## 4. Comparison Matrix

| Criterion | Schema Registry | Eureka | Spring Config Server |
|-----------|-----------------|--------|------------------------|
| **Solves current pain** | Schema drift | None (static OK) | Config duplication, env management |
| **Compatibility** | Medium (landoop SR version) | ✅ Full | ✅ Full |
| **Integration effort** | High | Medium | Medium |
| **Test overhead** | Medium | Low | Medium |
| **Operational overhead** | Low (in landoop) | Medium (new service) | Medium (new service) |
| **Future value** | High (events scale) | Medium (multi-instance) | High (prod readiness) |

---

## 5. Recommended Roadmap

### Phase 1 (Current)

- **Schema Registry**: Defer. Use shared `events-api` module + contract tests instead.
- **Eureka**: Skip. Static URLs acceptable for 2-service demo.
- **Config Server**: **Implemented** — see [CONFIG_SERVER.md](CONFIG_SERVER.md). Port 8888, native backend.

### Phase 2 (When Expanding)

1. ~~**Spring Config Server**~~ (done)
   - Add when preparing multi-environment (dev/staging/prod) or when config duplication becomes painful.
   - Use `spring.config.import=optional:configserver:…` for graceful fallback.

2. **Schema Registry** (if events grow)
   - Consider when adding more event types or more consumers.
   - Migrate from Jackson JSON to JSON Schema or Avro; ensure landoop Schema Registry version matches Confluent client, or replace landoop with Confluent Kafka + Schema Registry.

3. **Eureka** (only if scaling)

   - Add when running multiple instances (e.g. Query x2, Inventory x2) and needing client-side load balancing.
   - Or skip in favor of Kubernetes discovery if moving to K8s.

---

## 6. Implementation Notes (If Adopted)

### 6.1 Spring Config Server (Sketch)

```
config-server/
  src/main/resources/
    application.yml          # server.port=8888, spring.profiles.active=native
  config/
    application.yml         # shared: kafka, redis
    query-microservice.yml
    inventory-microservice.yml
```

**Client (Query/Inventory)**:

```yaml
# application.yml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

### 6.2 Schema Registry (Sketch)

- Replace `JacksonJsonSerializer` / `JacksonJsonDeserializer` with `KafkaJsonSchemaSerializer` / `KafkaJsonSchemaDeserializer`.
- Add `schema.registry.url: http://localhost:8081` (landoop default).
- Create JSON schemas for OrderEvent, AdoptionEvent; register via Confluent Maven plugin or at startup.
- Shared `events-api` module holds schemas and generated/POJO types.

### 6.3 Eureka (Sketch)

- New `eureka-server` module: `@EnableEurekaServer`, port 8761.
- Query/Inventory: `spring-cloud-starter-netflix-eureka-client`.
- Replace `inventory.service-url` with `RestTemplate` + `@LoadBalanced` and `http://inventory-microservice/v1/inventory`.
- gRPC: Requires custom `NameResolver` for Eureka; more involved.

---

## 7. Summary

| Technology | Recommendation | Rationale |
|------------|----------------|-----------|
| **Schema Registry** | Defer | High migration cost; shared events module + contract tests addresses drift with less overhead. Revisit when event model grows. |
| **Eureka** | Not recommended now | Only 2 services; static addresses sufficient. Add when scaling or moving to multi-instance. |
| **Spring Config Server** | Recommended (medium term) | Best fit for centralized config and environment management. Compatible, moderate effort, high future value. |

---

*Document generated from project analysis. Update as the architecture evolves.*
