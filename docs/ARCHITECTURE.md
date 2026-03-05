# Architecture and Flows

This document describes the system architecture, component relationships, and key operational flows for the microservices-ops-demo.

## System Overview

```mermaid
flowchart TB
    subgraph External["External APIs"]
        PetStore["Pet Store API<br/>petstore.swagger.io"]
    end

    subgraph Config["Configuration"]
        ConfigServer["Config Server<br/>:8888"]
    end

    subgraph Microservices["Microservices"]
        Query["Query Service<br/>:8086"]
        Inventory["Inventory Service<br/>:8085, :9090 gRPC"]
        Admin["Admin Server<br/>:8089"]
    end

    subgraph Data["Data & Messaging"]
        Redis["Redis<br/>:6379"]
        subgraph Kafka["Apache Kafka :9092"]
            OE["order-events-v1"]
            AE["adoption-events-v1"]
            ACE["adoption-congratulation-events-v1"]
            ZipkinTopic["zipkin"]
            AppLogs["application-logs"]
        end
    end

    subgraph Observability["Observability"]
        Zipkin["Zipkin<br/>:9411"]
        Prometheus["Prometheus<br/>:9412"]
        Grafana["Grafana<br/>:3000"]
        subgraph ELK["Log Analytics"]
            ES["Elasticsearch<br/>:9200"]
            Kibana["Kibana<br/>:5601"]
        end
        KafkaConnect["Kafka Connect<br/>:8084"]
    end

    subgraph Exporters["Prometheus Exporters"]
        RedisExp["redis-exporter<br/>:9121"]
        ESExp["elasticsearch-exporter<br/>:9114"]
        KafkaExp["kafka-exporter<br/>:9308"]
    end

    ConfigServer -.->|config| Query
    ConfigServer -.->|config| Inventory
    ConfigServer -.->|config| Admin

    PetStore <-->|HTTP| Query
    PetStore <-->|HTTP| Inventory
    Query <-->|gRPC/REST| Inventory
    Query -->|reservations| Redis

    Inventory -->|produce| OE
    OE -->|consume| Query
    Query -->|produce| AE
    AE -->|consume| Inventory
    Inventory -->|produce| ACE

    Query -->|traces| ZipkinTopic
    Inventory -->|traces| ZipkinTopic
    ZipkinTopic --> Zipkin

    Query -->|logs| AppLogs
    Inventory -->|logs| AppLogs
    AppLogs --> KafkaConnect
    KafkaConnect --> ES
    ES <--> Kibana

    Prometheus -->|scrape| Query
    Prometheus -->|scrape| Inventory
    Prometheus -->|scrape| Admin
    Prometheus -->|scrape| ConfigServer
    Prometheus --> RedisExp
    Prometheus --> ESExp
    Prometheus --> KafkaExp
    Prometheus --> Grafana

    Redis --> RedisExp
    ES --> ESExp
    Kafka --> KafkaExp
```

---

## Docker Startup Flow

The full stack starts in seven layers. Each layer waits for its dependencies to be healthy before starting.

```mermaid
flowchart TD
    subgraph L1["Layer 1: Infrastructure"]
        Redis[Redis]
        Kafka[Kafka]
        ES[Elasticsearch]
    end

    subgraph L2["Layer 2: Config"]
        Config[config-server]
    end

    subgraph L3["Layer 3: Exporters"]
        RedisExp[redis-exporter]
        ESExp[elasticsearch-exporter]
        KafkaExp[kafka-exporter]
    end

    subgraph L4["Layer 4: Observability"]
        Zipkin[Zipkin]
        Prometheus[Prometheus]
        Grafana[Grafana]
    end

    subgraph L5["Layer 5: ELK"]
        Kibana[Kibana]
        Connect[Kafka Connect]
        ElkInit[elk-init]
    end

    subgraph L6["Layer 6: Admin"]
        Admin[admin-server]
    end

    subgraph L7["Layer 7: Microservices"]
        Query[query-microservice]
        Inventory[inventory-microservice]
    end

    Redis --> RedisExp
    Kafka --> KafkaExp
    ES --> ESExp
    Kafka --> Zipkin
    RedisExp --> Prometheus
    Prometheus --> Grafana

    ES --> Kibana
    Kafka --> Connect
    ES --> Connect
    Connect --> ElkInit
    Kibana --> ElkInit

    Config --> Admin
    Kafka --> Admin
    Config --> Query
    Config --> Inventory
    Redis --> Query
    Kafka --> Query
    Prometheus --> Query
    Zipkin --> Query
    ElkInit --> Query
    ElkInit --> Inventory
    Kafka --> Inventory
    Prometheus --> Inventory
    Zipkin --> Inventory
```

See [DOCKER.md](DOCKER.md) for health checks and graceful shutdown.

---

## Log Flow (ELK)

Application logs are enriched with trace correlation (traceId, spanId), service, environment, and host, then sent to Kafka for ingestion into Elasticsearch.

```mermaid
flowchart LR
    subgraph Apps["Microservices"]
        Q["Query<br/>LogstashEncoder"]
        I["Inventory<br/>LogstashEncoder"]
    end

    subgraph Enrichment["Log Enrichment"]
        direction TB
        M["MDC: traceId, spanId<br/>Custom: service, env, host<br/>Errors: stack_trace"]
    end

    subgraph Pipeline["Pipeline"]
        K[(Kafka<br/>application-logs)]
        KC["Kafka Connect<br/>ES Sink"]
        ES[(Elasticsearch)]
        Kibana["Kibana<br/>Discover & Dashboards"]
    end

    Q -->|JSON logs| M
    I -->|JSON logs| M
    M --> K
    K --> KC
    KC --> ES
    ES --> Kibana
```

**Trace correlation**: Copy a `traceId` from Zipkin (http://localhost:9411) and filter in Kibana by `traceId: "..."` to see all logs for that request across Query and Inventory.

See [LOGGING_KAFKA.md](LOGGING_KAFKA.md) and [ELK_LOGGING.md](ELK_LOGGING.md).

---

## Pet Adoption Flow

End-to-end flow when a client adopts a pet via the Query service.

```mermaid
sequenceDiagram
    participant Client
    participant Query as Query Service
    participant Redis
    participant PetStore
    participant Kafka
    participant Inventory as Inventory Service
    participant Zipkin

    Client->>Query: POST /v1/pets/{id}/adopt
    Query->>Query: Check reservation token (Redis)
    Query->>PetStore: POST /pet/{id} (adopt)
    PetStore-->>Query: 200 OK
    Query->>Query: Record adoption metric
    Query->>Kafka: adoption-events-v1
    Query->>Zipkin: trace span
    Kafka->>Inventory: consume adoption
    Inventory->>PetStore: GET /store/inventory
    Inventory->>Kafka: adoption-congratulation-events-v1
    Inventory->>Zipkin: trace span
    Query-->>Client: 200 OK
```

**Notes**:
- Reservation token (from Redis) required when Redis is available.
- Circuit breaker on PetStore/Inventory calls; fallback returns empty on failure.
- Traces and logs include `traceId` for correlation in Zipkin and Kibana.

---

## Pet Reservation Flow

Ticketmaster-style reserve-then-adopt flow. Reservations are stored in Redis with TTL; adoption requires the reservation token when Redis is available.

```mermaid
sequenceDiagram
    participant Client
    participant Query as Query Service
    participant Redis
    participant PetStore

    Note over Client,PetStore: Step 1: Reserve
    Client->>Query: POST /v1/pets/{id}/reserve
    Query->>Redis: SET reservation:pet:{id} (NX, TTL)
    alt Success (key absent)
        Redis-->>Query: OK
        Query->>Query: Add to reservations:active set
        Query-->>Client: 200 + X-Reservation-Token
    else Conflict (already reserved)
        Redis-->>Query: nil
        Query-->>Client: 409 Conflict
    end

    Note over Client,PetStore: Step 2: Adopt (with token)
    Client->>Query: POST /v1/pets/{id}/adopt<br/>X-Reservation-Token: {token}
    Query->>Redis: GET reservation:pet:{id}
    alt Token valid
        Redis-->>Query: value
        Query->>PetStore: POST /pet/{id} (adopt)
        Query->>Redis: DEL reservation, SREM active
        Query-->>Client: 200 OK
    else Token invalid or expired
        Query-->>Client: 400 Bad Request
    end
```

**Notes**: When Redis is unavailable, reserve returns 503; adopt skips token validation (graceful degradation).

---

## Order Sync Flow

Scheduled and event-driven order synchronization between PetStore, Inventory, and Query.

```mermaid
sequenceDiagram
    participant Inv as Inventory Service
    participant PetStore
    participant Kafka
    participant Query as Query Service
    participant DB as H2 (Query DB)

    Note over Inv: Every 20s (scheduled)
    Inv->>Inv: updateOrders()
    loop For 3 random IDs (1–10)
        Inv->>PetStore: GET /store/order/{id}
        alt Order found (200)
            PetStore-->>Inv: OrderDto
            Inv->>Inv: Record metric
            Inv->>Kafka: order-events-v1
        else 404 Not Found
            PetStore-->>Inv: 404 (expected, demo API)
        end
    end

    Kafka->>Query: consume order-events-v1
    Query->>DB: persist PetShopOrder
    Query->>Query: Record metric (orders.size gauge)
```

**Notes**:
- PetStore demo API returns random results; 404 is expected and ignored by circuit breaker.
- Query maintains local order DB; Kafka events keep it in sync.

---

## Trace Correlation Flow

Distributed tracing and log correlation across the observability stack.

```mermaid
flowchart LR
    subgraph Request["HTTP Request"]
        Q["Query"]
        I["Inventory"]
    end

    subgraph Tracing["Micrometer Tracing"]
        MDC["MDC: traceId, spanId"]
    end

    subgraph Backends["Backends"]
        Z[Zipkin]
        K[(Kafka<br/>zipkin topic)]
        AppLogs[(application-logs)]
        Kibana[Kibana]
    end

    Q --> MDC
    I --> MDC
    Q -->|spans| K
    I -->|spans| K
    K --> Z

    Q -->|logs + traceId| AppLogs
    I -->|logs + traceId| AppLogs
    AppLogs --> Kibana

    Z -.->|copy traceId| Kibana
```

**Workflow**:
1. Request enters Query → Micrometer creates trace, propagates traceId/spanId to MDC.
2. Logs (console + Kafka) include traceId, spanId, service, host.
3. gRPC/HTTP calls to Inventory propagate trace context (B3 headers).
4. Zipkin shows the full trace; Kibana shows logs filtered by the same traceId.

---

## Metrics Flow (Prometheus)

```mermaid
flowchart LR
    subgraph Sources["Metrics Sources"]
        SB["Spring Boot<br/>Actuator :8085,8086,8088,8089"]
        Redis["redis-exporter<br/>:9121"]
        ES["elasticsearch-exporter<br/>:9114"]
        Kafka["kafka-exporter<br/>:9308"]
    end

    subgraph Storage["Prometheus"]
        P["Prometheus<br/>:9412"]
    end

    subgraph Dashboards["Grafana"]
        G["Grafana<br/>:3000"]
    end

    SB -->|/actuator/prometheus| P
    Redis -->|/metrics| P
    ES -->|/metrics| P
    Kafka -->|/metrics| P
    P --> G
```

**Dashboards**:
- **Pet Shop Overview**: Business metrics (adoptions, reservations, orders, latencies).
- **Infrastructure**: Redis, Elasticsearch, Kafka, JVM, HTTP, Prometheus targets.

---

## Kafka Topics Summary

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `order-events-v1` | Inventory | Query | Order updates from PetStore |
| `adoption-events-v1` | Query | Inventory | Pet adoption events |
| `adoption-congratulation-events-v1` | Inventory | (external) | Adoption confirmation |
| `zipkin` | Query, Inventory | Zipkin | Distributed traces |
| `application-logs` | Query, Inventory | Kafka Connect → ES | Enriched JSON logs |

---

## Related Documentation

| Doc | Topic |
|-----|-------|
| [DOCKER.md](DOCKER.md) | Container best practices, startup order |
| [ELK_LOGGING.md](ELK_LOGGING.md) | Elasticsearch, Kibana, log schema |
| [LOGGING_KAFKA.md](LOGGING_KAFKA.md) | Log distribution, trace enrichment |
| [GRPC_IMPLEMENTATION.md](GRPC_IMPLEMENTATION.md) | gRPC for Query ↔ Inventory |
| [CONFIG_SERVER.md](CONFIG_SERVER.md) | Centralized configuration |
| [PROFILING.md](PROFILING.md) | Load testing with Gatling |
