# gRPC Implementation for Query ↔ Inventory Communication

## Overview

This document describes the gRPC rollout for internal sync communication between the Query and Inventory microservices, replacing REST for the three primary operations: `getInventory`, `getOrderLive`, and `refresh`.

## Architecture

```
┌─────────────┐     REST (OpenAPI)      ┌─────────────┐
│   Clients   │ ◄──────────────────────► │   Query     │
└─────────────┘                          │   :8086     │
                                         └──────┬──────┘
                                                │
                         ┌──────────────────────┼──────────────────────┐
                         │ gRPC (primary)       │                      │ REST (fallback)
                         ▼                      │                      │
                  ┌─────────────┐         Kafka │               ┌──────────────┐
                  │  Inventory  │ ◄─────────────┼───────────────►│   PetStore   │
                  │ :8085, :9090│               │               └──────────────┘
                  └─────────────┘               │
                         │                      │
                         └──────────────────────┘
```

- **gRPC (port 9090)**: Primary transport for Query → Inventory sync calls when `inventory.grpc.enabled=true`
- **REST (port 8085)**: Fallback when gRPC is disabled (e.g., tests); remains for external/legacy access
- **Kafka**: Unchanged for async events (orders, adoptions)

## Proto Models

**File**: `inventory-grpc-api/src/main/proto/inventory.proto`

### Service Definition

```protobuf
service InventoryService {
  rpc GetInventory(GetInventoryRequest) returns (GetInventoryResponse);
  rpc GetOrder(GetOrderRequest) returns (GetOrderResponse);
  rpc RefreshInventory(RefreshInventoryRequest) returns (RefreshInventoryResponse);
}
```

### Message Definitions

| Message | Purpose |
|---------|---------|
| `GetInventoryRequest` | `status` (optional), `low_stock_threshold` (optional, 0 = not set) |
| `GetInventoryResponse` | `json_body` – raw JSON string (PetStore inventory structure) |
| `GetOrderRequest` | `order_id` (int32) |
| `GetOrderResponse` | `oneof { Order order; NotFound not_found }` |
| `Order` | `id`, `pet_id`, `quantity`, `ship_date`, `status`, `complete` |
| `NotFound` | `message` |
| `RefreshInventoryRequest` | Empty |
| `RefreshInventoryResponse` | `json_body` – refreshed inventory JSON |

### Design Choices

1. **JSON in proto**: Inventory and refresh responses use `string json_body` to preserve the dynamic PetStore structure (`{"available":N,"pending":N,"sold":N,...}`) without duplicating schema.
2. **Structured Order**: `Order` is a proper proto message for type safety on `getOrder`.
3. **Oneof for GetOrder**: `GetOrderResponse` uses `oneof` to distinguish found vs. not-found without extra status fields.

## Module Structure

| Module | Role |
|--------|------|
| `inventory-grpc-api` | Shared proto definitions and generated Java stubs (protobuf + gRPC) |
| `inventory-microservice` | gRPC server (port 9090) via `InventoryGrpcService` |
| `query-microservice` | gRPC client via `InventoryGrpcClient` |

## Configuration

### Inventory (server)

```yaml
grpc:
  server:
    port: 9090
```

### Query (client)

```yaml
inventory:
  grpc:
    enabled: true

grpc:
  client:
    inventory:
      address: static://localhost:9090
      negotiationType: PLAINTEXT
```

### Toggling gRPC

Set `inventory.grpc.enabled: false` to use REST for all Query → Inventory sync calls (e.g., in tests or when Inventory gRPC is unavailable).

## Build & Run

1. **Install shared API** (required before building Query/Inventory):
   ```bash
   cd inventory-grpc-api && ./mvnw install -DskipTests
   ```

2. **Full stack** (start.sh builds api first):
   ```bash
   ./start.sh full
   ```

3. **Tests**: Use `inventory.grpc.enabled: false` in `application-test.yml` so unit/integration tests use REST mocks.

## Dependencies

- **inventory-grpc-api**: `protobuf-java` 3.25.1, `grpc-stub` / `grpc-protobuf` 1.68.2
- **inventory-microservice**: `inventory-grpc-api`, `grpc-server-spring-boot-starter` 3.1.0
- **query-microservice**: `inventory-grpc-api`, `grpc-client-spring-boot-starter` 3.1.0

## Resilience & Observability

- **Resilience4j**: Circuit breaker, retry, and rate limiter remain on the Query `InventoryService` facade; they wrap both gRPC and REST paths.
- **Tracing**: gRPC uses the same B3 propagation as REST; Zipkin captures gRPC spans when tracing is enabled.
- **Metrics**: `@Timed` annotations on the facade methods apply regardless of transport.

## Best Practices Applied

1. **Shared API module**: Single proto source of truth for client and server.
2. **Transport toggle**: gRPC can be disabled without code changes.
3. **Graceful fallback**: REST remains available for tests and compatibility.
4. **Proto versioning**: Package `mx.hdmsantander.opsdemo.inventory.v1` allows future `v2` without breaking clients.
5. **Health**: Inventory exposes gRPC server; Query gRPC client health can be monitored via Spring Boot Actuator when enabled.

## Port Summary

| Service | REST | gRPC |
|---------|------|------|
| Query | 8086 | — |
| Inventory | 8085 | 9090 |
