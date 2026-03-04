# Testing Guide

## Overview

The project uses **JUnit 5**, **Mockito**, **MockMvc**, **EmbeddedKafka**, and **AssertJ** for unit and integration tests. Minimum 80% code coverage is targeted for services and controllers.

## Test Categories

### Unit Tests (standalone, no Spring context or minimal)

| Module | Test Class | Purpose |
|--------|------------|---------|
| **Query** | `MainControllerTest` | HTTP endpoints via MockMvc; mocks PetService, InventoryService, ReservationService, PetShopOrderService |
| **Query** | `PetServiceTest` | getPets (single, status, all, tags), adoptPetById (Redis, token validation) |
| **Query** | `ReservationServiceTest` | createReservation, validateAndRelease, getReservationStatus, cleanup, isRedisAvailable |
| **Query** | `ReservationCleanupJobTest` | Scheduled cleanup when Redis available/unavailable |
| **Query** | `InventoryServiceTest` | getInventory, getOrderLive, refresh (REST and gRPC paths) |
| **Query** | `InventoryGrpcClientTest` | gRPC client JSON parsing, getOrder found/not-found |
| **Query** | `PetShopOrderServiceTest` | getAllOrders, getOrderById |
| **Query** | `AdoptionEventSenderTest` | send() invokes StreamBridge |
| **Query** | `AdoptionEventTest` | AdoptionEvent.createFromPet() |
| **Query** | `OrderEventTest` | OrderEvent.createPetShopOrder() |
| **Query** | `OrderEventConsumerConfigurationTest` | Consumer saves to repository |
| **Query** | `TracingHeaderConfigurationTest` | X-Zipkin-Trace-Id filter |
| **Inventory** | `MainControllerTest` | HTTP endpoints; mocks InventoryService, OrderService |
| **Inventory** | `InventoryServiceTest` | getInventory with filters, refreshInventoryAndOrders |
| **Inventory** | `OrderServiceTest` | getOrderById (success, empty) |
| **Inventory** | `OrderEventSenderTest` | send() invokes StreamBridge |
| **Inventory** | `OrderEventTest` | OrderEvent.createPetShopOrder() |
| **Inventory** | `AdoptionEventProcessorConfigurationTest` | Processor sends AdoptionCongratulationEvent |
| **Inventory** | `AdoptionCongratulationEventTest` | AdoptionCongratulationEvent.from() |
| **Inventory** | `InventoryGrpcServiceTest` | gRPC server GetInventory, GetOrder, RefreshInventory |

### Integration Tests (@SpringBootTest + EmbeddedKafka)

| Module | Test Class | Purpose |
|--------|------------|---------|
| **Query** | `Resilience4jIntegrationTest` | Circuit breaker, retry, rate limiter for PetService and InventoryService (mocked RestTemplate) |
| **Query** | `TracingPropagationTest` | X-Zipkin-Trace-Id in HTTP responses, tracer in context |
| **Inventory** | `Resilience4jIntegrationTest` | Circuit breaker, retry for InventoryService and OrderService (mocked RestTemplate) |
| **Inventory** | `TracingPropagationTest` | Trace propagation, empty inventory → 500 |

## Test Configuration

- **application-test.yml**: Disables Redis, scheduling, admin client; `inventory.grpc.enabled: false` so tests use REST.
- **EmbeddedKafka**: All integration tests use `@EmbeddedKafka` for Kafka-backed Spring Cloud Stream.

## Running Tests

```bash
# All tests for a module
cd query-microservice && ./mvnw test
cd inventory-microservice && ./mvnw test

# With coverage (verify)
./mvnw verify

# Single test class
./mvnw test -Dtest=MainControllerTest
```

## gRPC Testing

- **Unit**: `InventoryGrpcServiceTest` (inventory) and `InventoryGrpcClientTest` (query) test gRPC adapter logic with mocks.
- **Integration**: Tests run with `inventory.grpc.enabled: false`; Query uses REST and mocked RestTemplate. End-to-end gRPC (Query → Inventory over wire) would require both services running or Testcontainers.

## Key Test Patterns

- **MockMvc standaloneSetup**: Controllers tested in isolation with mocked services.
- **ReflectionTestUtils**: Inject mocks when `@Autowired` or constructor injection not used.
- **@Order / TestMethodOrder**: Resilience4j tests require ordered execution for circuit breaker state.
- **@DirtiesContext**: Inventory Resilience4j uses it for circuit breaker reset.
