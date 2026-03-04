# Observability Proposal Validation Analysis

**Document version:** 1.0  
**Date:** 2025-03-04  
**Source:** Validation of ENDPOINTS_PROPOSAL_OBSERVABILITY.md against microservices-ops-demo codebase  
**Branch:** cursor/endpoints-proposal-observability-8f0b

---

## Executive Summary

This document validates the Observability-First Endpoints Proposal (v4) against the current state of the microservices-ops-demo project. The proposal is **largely sound** with several corrections needed to align with the actual codebase. Key findings:

- **Port alignment:** Proposal uses 8082/8081; codebase uses 8086/8085 — **correct to current**.
- **Reservation flow:** Redis-based design is well thought out; adoption requires new dependencies and Docker changes.
- **Filter semantics:** Pet and inventory filter logic is correctly specified.
- **Gaps identified:** Order endpoints, inventory refresh, and service integration paths need clarification.

---

## 1. Current State vs. Proposal Comparison

### 1.1 Ports and Configuration

| Service    | Proposal | Current | Recommendation                  |
|-----------|----------|---------|----------------------------------|
| Query     | 8082     | 8086    | **Keep 8086** — align proposal   |
| Inventory | 8081     | 8085    | **Keep 8085** — align proposal   |

**Action:** Update proposal to reference ports 8085 (inventory) and 8086 (query).

### 1.2 Query Microservice Endpoints

| Endpoint                      | Proposal | Current | Gap / Change |
|------------------------------|----------|---------|--------------|
| `GET /v1/pet`                | —        | ✅ Exists (status required) | Rename to `/v1/pets`, add id/tags filters |
| `GET /v1/pets`               | ✅       | —       | New; replaces `/pet`; supports list-all (no params), by status, by id |
| `POST /v1/pet/{id}/adopt`    | —        | ✅ Exists | Keep; add reservation requirement |
| `POST /v1/pets/{id}/reserve` | ✅       | —       | New; requires Redis |
| `POST /v1/pets/{id}/adopt`   | ✅       | —       | Modify existing; require `X-Reservation-Token` |
| `DELETE /v1/pets/{id}/reserve` | ✅     | —       | New |
| `GET /v1/reservations/{id}`  | ✅       | —       | New |
| `GET /v1/orders`             | ✅       | ✅ Exists | No change |
| `GET /v1/orders/{orderId}`   | ✅       | —       | **New** — `PetShopOrderRepository.findById()` exists |
| `GET /v1/orders/{orderId}/live` | ✅    | —       | **New** — requires Inventory to expose `GET /v1/order/{orderId}` |
| `GET /v1/inventory`          | ✅       | ✅ Exists | Add filter params proxy |
| `GET /v1/adoptions/stats`    | ✅       | —       | New; reads from `pet.adoptions` counter |
| `POST /v1/inventory/refresh` | ✅       | —       | New; proxy to Inventory — **Inventory must expose** |

### 1.3 Inventory Microservice Endpoints

| Endpoint                   | Proposal | Current | Gap / Change |
|---------------------------|----------|---------|--------------|
| `GET /v1/inventory`       | ✅       | ✅ Exists | Add filters: `status`, `lowStockThreshold` |
| `GET /v1/order/{orderId}` | ✅       | —       | **New** — OrderService fetches from PetStore but no REST endpoint |
| `POST /v1/inventory/refresh` | ✅    | —       | **New** — `refreshOrders()` exists as `@Scheduled` only; expose HTTP |

---

## 2. Technical Validation

### 2.1 PetStore API Compatibility

**PetStore v2 base URL:** `https://petstore.swagger.io/v2`

| Operation   | Proposal Mapping              | PetStore Endpoint                  | Status |
|------------|--------------------------------|------------------------------------|--------|
| Single pet | `id` filter → GET /pet/{id}    | `GET /pet/{id}`                    | ✅     |
| List pets  | `status` + optional tags       | `GET /pet/findByStatus?status={s}` | ✅     |
| Tags filter| In-memory after findByStatus   | (findByTags deprecated/poor design)| ✅     |

**Pet model:** Uses `List<Tag>` with `Tag.name`. Tags filter must match `Tag.name` (case-sensitive per proposal).

**Pet ID type:** PetStore returns `long`; current `Pet` model uses `String id`. Jackson maps `long` → `String` automatically.

### 2.2 Inventory Structure

PetStore `GET /v2/store/inventory` returns:

```json
{ "available": 5, "pending": 2, "sold": 10, ... }
```

Proposal filter semantics (status first, then lowStockThreshold) are correct. Keys in PetStore are lowercase (`available`, `pending`, `sold`).

### 2.3 Order Data Flow

| Component           | Current                                      | Proposal                                    |
|--------------------|-----------------------------------------------|---------------------------------------------|
| PetShopOrder       | JPA entity, `orderId` (Integer) as PK         | Same                                        |
| Order source       | Kafka `order-events-v1` from Inventory        | Same                                        |
| Live order fetch   | —                                            | Inventory → PetStore `GET /store/order/{id}`|
| OrderDto           | Exists in Inventory                           | Same; maps to PetStore response             |

**Gap:** Inventory `OrderService.fetchOrder(int)` exists but is package-private; no controller exposes it. Proposal requires adding `GET /v1/order/{orderId}` to Inventory.

### 2.4 Redis and Reservation Flow

| Item                       | Proposal                                  | Validation                                 |
|---------------------------|--------------------------------------------|--------------------------------------------|
| Dependency                | `spring-boot-starter-data-redis`           | Not in Query `pom.xml` — add               |
| Docker                    | Redis container (redis:7-alpine, 6379)     | Not in docker-compose — add                |
| Key schema                | `reservation:pet:{petId}`                  | Sound                                      |
| SET `reservations:active`  | SADD/SREM, SCARD for gauge                 | Sound; avoids KEYS/DBSIZE                  |
| TTL cleanup               | Scheduled job, SMEMBERS + EXISTS + SREM   | Sound for demo scale                       |

**Pet ID in Redis:** Use `String.valueOf(petId)` consistently for keys and set members.

### 2.5 Service-to-Service Communication

| Call                          | From  | To      | Current URL                          | Notes |
|-------------------------------|-------|---------|--------------------------------------|-------|
| Query → Inventory (inventory) | Query | Inventory| `http://localhost:8085/v1/inventory` | Hardcoded; add query params for filters |
| Query → Inventory (order)     | Query | Inventory| —                                    | New; `http://localhost:8085/v1/order/{id}` |
| Query → Inventory (refresh)  | Query | Inventory| —                                    | New; `POST http://localhost:8085/v1/inventory/refresh` |
| Inventory → PetStore         | Inventory | PetStore| `https://petstore.swagger.io/v2/...`| Existing |

**Docker network:** Current docker-compose uses `network_mode: host`; `localhost` works. If switching to bridge network, use service names (e.g. `http://inventory-microservice:8085`).

---

## 3. Proposal Gaps and Corrections

### 3.1 Port Numbers

- **Correction:** Use 8085 (Inventory) and 8086 (Query) throughout the proposal.

### 3.2 Inventory Refresh Endpoint

- **Current:** `OrderService.refreshOrders()` is triggered only by `@Scheduled(fixedDelay = 20000)`.
- **Proposal:** `POST /v1/inventory/refresh` to trigger on-demand.
- **Implementation:** Add controller method in Inventory that calls `orderService.updateOrders()` and/or a dedicated `inventoryService.refresh()` if needed. Note: `refreshOrders` updates orders, not inventory. Proposal says "refresh" — clarify:
  - **Option A:** Refresh orders (current behavior) — rename to `POST /v1/orders/refresh` in Inventory.
  - **Option B:** Re-fetch inventory from PetStore — add `inventoryService.refreshInventory()` that re-caches or re-fetches.
- **Recommendation:** For demo, "inventory refresh" can mean triggering the order sync; document as such, or add both behaviors.

### 3.3 Orders Live vs. Event-Synced

- **GET /v1/orders/{orderId}:** From Query local DB (event-synced). `PetShopOrderRepository.findById(orderId)` — supported.
- **GET /v1/orders/{orderId}/live:** From PetStore via Inventory. Requires Inventory `GET /v1/order/{orderId}`.

### 3.4 Pet Resource Pluralization

- **Current:** `/v1/pet` (singular)
- **Proposal:** `/v1/pets` (plural)
- **Impact:** Breaking change for any existing clients; acceptable for demo.

### 3.5 Adoptions Stats

- **Current:** `pet.adoptions` counter exists in `PetService.adoptPetById`.
- **Proposal:** `GET /v1/adoptions/stats` returns `{ totalAdoptions }`.
- **Implementation:** Use `meterRegistry.find("pet.adoptions").counter().map(Counter::count).orElse(0)` or cache value. Counters in Micrometer are cumulative; total is the count.

---

## 4. Suggested Technologies for Demo Stack

The following technologies fit the existing observability and resilience focus:

| Technology | Purpose | Integration | Priority |
|------------|---------|-------------|----------|
| **Redis** | Reservation store (already in proposal) | Spring Data Redis, docker-compose | High |
| **Grafana** | Dashboards for Prometheus metrics | Add to docker-compose, datasource Prometheus | High |
| **Testcontainers** | Integration tests with Redis, Kafka | Maven deps, JUnit `@Testcontainers` | Medium |
| **Spring Boot Admin** | Single dashboard for health, metrics, env | Spring Boot Admin Server + client deps | Medium |
| **Docker Compose healthchecks** | Ordered startup, readiness | `healthcheck` in docker-compose | Low |
| **OpenTelemetry Collector** | Optional multi-backend tracing | Replace direct Zipkin if needed | Low |
| **Chaos Mesh / Chaos Monkey** | Resilience demo | Optional; adds complexity | Low |

### 4.1 Grafana (Recommended)

- **Why:** Prometheus scrapes metrics but has limited visualization. Grafana provides ready-made dashboards for JVM, Spring Boot, and Kafka.
- **Add:** Grafana service in docker-compose, configurable datasource for Prometheus at `http://prometheus:9412` (or `localhost:9412` with host network).
- **Pre-built:** Use `micrometer` or `spring boot` dashboards from Grafana marketplace.

### 4.2 Testcontainers

- **Why:** Ensures tests run against real Redis and Kafka; avoids flaky embedded alternatives.
- **Add:** `org.testcontainers:testcontainers`, `org.testcontainers:kafka`, `org.testcontainers:junit-jupiter` for Query reservation tests.

### 4.3 Spring Boot Admin

- **Why:** Centralized view of all Spring Boot apps (health, metrics, logs, env). Good for demos.
- **Add:** Admin Server as new optional service; Query and Inventory register as clients.

---

## 5. Metrics and Observability Validation

### 5.1 Existing Metrics

| Metric            | Service  | Exists | Notes                              |
|-------------------|----------|--------|------------------------------------|
| `pet.adoptions`   | Query    | ✅     | Counter in PetService              |
| `orders.updated`  | Inventory| ✅     | Counter in OrderService           |
| `orders.size`     | Query    | ✅     | Gauge on PetShopOrderRepository    |
| `pet.query.time`  | Query    | ✅     | @Timed on getPetListByStatus       |
| `pet.adoption.time` | Query  | ✅     | @Timed on adoptPetById             |
| `inventory.query.time` | Both | ✅ | @Timed on getInventory             |
| `orders.query.time` | Inventory| ✅    | @Timed on updateOrders             |

### 5.2 New Metrics (Proposal)

| Metric                       | Service | Validation                              |
|-----------------------------|---------|----------------------------------------|
| `pets.queried`              | Query   | Add with tags `operation`, `filter`     |
| `reservations.created`      | Query   | Add                                     |
| `reservations.expired`      | Query   | Add                                     |
| `reservations.released`     | Query   | Add                                     |
| `reservations.conflict`     | Query   | Add (409 on POST reserve)               |
| `reservations.cleanup.removed` | Query| Add                                     |
| `reservations.active`       | Query   | Gauge from SCARD `reservations:active`  |
| `inventory.queries`         | Both    | Add with `view`/`filter` tags           |
| `orders.queries`            | Inventory| Add                                   |
| `inventory.low_stock.count` | Inventory| DistributionSummary                   |
| `reservation.create.time`   | Query   | Timer                                   |
| `reservation.release.time`  | Query   | Timer                                   |
| `reservation.cleanup.time`  | Query   | Timer                                   |
| `orders.live.query.time`    | Query   | Timer                                   |

---

## 6. Pitfall and Design Flaw Validation

### 6.1 Technical Pitfalls (Proposal)

| Pitfall          | Validation |
|------------------|------------|
| Redis availability | Add health indicator; consider graceful degradation (direct adopt with warning) |
| Clock skew       | Document; ensure containers use NTP or host time |
| Pet ID type      | Use `String` in Redis; PetStore `long` maps to String via Jackson |
| Race on adopt    | Lua script for atomic get-and-delete recommended |
| Path ordering    | Resolved by filter-based `/v1/pets` |
| Filter semantics | Documented in proposal; correct |
| reservations.active gauge | Resolved with SET + SCARD + cleanup |

### 6.2 Design Flaws

| Flaw                        | Severity | Notes                                      |
|-----------------------------|----------|--------------------------------------------|
| Token in header             | Low      | Demo-only; document for production         |
| No idempotency for adopt    | Medium   | Document single-use tokens                  |
| Query proxy for refresh     | Low      | Adds trace hop; acceptable for demo       |
| Tags filter in-memory       | Medium   | Correct; avoids deprecated PetStore API    |

---

## 7. Implementation Readiness Checklist

### Query Microservice

- [ ] Add `spring-boot-starter-data-redis` to pom.xml
- [ ] Add Redis configuration (host, port)
- [ ] Create `ReservationService` (Redis SET NX EX, SADD/SREM)
- [ ] Create `ReservationCleanupJob` (@Scheduled)
- [ ] Extend `PetService.getPets(id?, status?, tags?)`
- [ ] Add reservation endpoints (reserve, adopt with token, release)
- [ ] Add `GET /v1/reservations/{id}`
- [ ] Add `GET /v1/orders/{orderId}`
- [ ] Add `GET /v1/orders/{orderId}/live` (proxy to Inventory)
- [ ] Add `GET /v1/adoptions/stats`
- [ ] Update `GET /v1/inventory` to forward filter params
- [ ] Add `POST /v1/inventory/refresh` proxy
- [ ] Add new metrics (reservations.*, pets.queried, etc.)
- [ ] Add Redis health indicator

### Inventory Microservice

- [ ] Add `GET /v1/inventory` filters (status, lowStockThreshold)
- [ ] Add `GET /v1/order/{orderId}` (from PetStore)
- [ ] Add `POST /v1/inventory/refresh` (trigger order sync)
- [ ] Add `inventory.queries` with filter tags
- [ ] Add `orders.queries` counter
- [ ] Add `inventory.low_stock.count` DistributionSummary

### Infrastructure

- [ ] Add Redis to docker-compose.yml
- [ ] Add Grafana to docker-compose (optional)
- [ ] Update Prometheus scrape config if new services added

---

## 8. Recommendations Summary

1. **Ports:** Update proposal to 8085/8086.
2. **Inventory refresh:** Clarify semantics (orders vs. inventory) or implement both.
3. **Add Grafana:** For better demo visualization.
4. **Add Testcontainers:** For reliable integration tests with Redis/Kafka.
5. **Service URLs:** Document that `localhost` is used with host network; provide override for bridge network.
6. **Reservation graceful degradation:** When Redis is down, allow direct adopt with a warning metric/log for demo resilience showcase.
7. **OpenAPI:** Ensure Swagger/OpenAPI specs are updated for all new endpoints.

---

## 9. References

- Current codebase: `/workspace` (query-microservice, inventory-microservice)
- Proposal source: ENDPOINTS_PROPOSAL_OBSERVABILITY.md (v4)
- PetStore API: https://petstore.swagger.io/v2
- Spring Data Redis: https://docs.spring.io/spring-data/redis/docs/current/reference/html/
