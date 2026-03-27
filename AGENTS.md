# AGENTS.md

## Cursor Cloud specific instructions

### Overview

This is a Spring Boot 4.0.3 microservices observability demo ("Pet Shop"). Two microservices (Query on :8086, Inventory on :8085) integrate with the external Swagger PetStore API, communicate via gRPC and REST, and exchange events over Kafka. See `README.md` for full architecture details.

### Prerequisites (already installed in snapshot)

- Java 21, Docker (with fuse-overlayfs + iptables-legacy for nested container support)
- Maven 3.9.6 (via `./mvnw` wrappers in each module)

### Build order

The `inventory-grpc-api` module **must** be installed to the local Maven repo before building Query or Inventory:
```
cd inventory-grpc-api && ../query-microservice/mvnw -q install -DskipTests
```
Note: `inventory-grpc-api` does not have its own `.mvn/wrapper/` directory; use the wrapper from another module or the downloaded Maven binary at `~/.m2/wrapper/dists/apache-maven-3.9.6/*/bin/mvn`.

### Running tests

Tests use EmbeddedKafka and mocks — no Docker required:
```
cd query-microservice && ./mvnw verify
cd inventory-microservice && ./mvnw verify
```
Both enforce 80% JaCoCo coverage.

### Running the application locally

1. **Start infrastructure** (Redis + Kafka are required):
   ```
   docker run -d --name redis-demo --network host redis:7-alpine
   docker run -d --name kafka-demo --network host -e ADV_HOST=127.0.0.1 -e RUNTESTS=0 landoop/fast-data-dev
   ```
   Wait ~30s for Kafka to be ready: `docker exec kafka-demo kafka-topics --bootstrap-server localhost:9092 --list`

2. **Start microservices** (each in a separate terminal):
   ```
   cd inventory-microservice && ./mvnw spring-boot:run -Dspring-boot.run.profiles=development
   cd query-microservice && ./mvnw spring-boot:run -Dspring-boot.run.profiles=development
   ```

3. **Verify**: `curl http://localhost:8086/v1/pets?status=available`

### Docker Compose gotchas in Cloud Agent VMs

`docker compose` with resource limits (`deploy.resources.limits`) fails in the nested cgroup v2 environment due to "threaded mode" cgroup errors. Workaround: run infrastructure containers directly with `docker run` (no resource limits), or use `./start.sh --tests-only` for test-only mode which requires no Docker.

### Key API endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/v1/pets?status=available` | GET | List pets from PetStore |
| `/v1/inventory` | GET | Get inventory counts |
| `/v1/pets/{id}/reserve` | POST | Reserve a pet (returns reservationId) |
| `/v1/pets/{id}/adopt` | POST | Adopt pet (needs `X-Reservation-Token` header) |
| `/v1/orders` | GET | List orders synced via Kafka |
| `/v1/orders/{id}/live` | GET | Fetch live order from Inventory |
| `/swagger-ui.html` | GET | Swagger UI (Query :8086, Inventory :8085) |
