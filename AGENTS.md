# AGENTS.md

## Cursor Cloud specific instructions

### Project overview
Microservices OPS Demo — a Spring Boot 4.0.3 / Java 21 pet shop system with two microservices (Query on port 8086, Inventory on port 8085/gRPC 9090), communicating via Kafka and gRPC. See `README.md` for full details.

### Build order
`inventory-grpc-api` **must** be installed to the local Maven repo before building Query or Inventory:
```
cd inventory-grpc-api && ./mvnw install -DskipTests
```

### Running tests
```
cd query-microservice && ./mvnw verify
cd inventory-microservice && ./mvnw verify
```
Tests use EmbeddedKafka and mocks — no Docker or external services needed. 80% JaCoCo coverage is enforced.

**Known issue:** `Resilience4jIntegrationTest.inventoryService_returns_inventory_when_api_succeeds` in the inventory microservice may fail locally due to test ordering sensitivity, while CI passes. This is a pre-existing issue, not an environment problem.

### Running the application locally (dev mode)

1. Start infrastructure via Docker Compose minimal stack:
   ```
   docker compose -f docker-compose-minimal.yml up -d
   ```
   This starts Redis (6379), Kafka (9092), Zipkin (9411), Prometheus (9412), and redis-exporter.

2. Start microservices (each in a separate terminal):
   ```
   cd inventory-microservice && ./mvnw spring-boot:run
   cd query-microservice && ./mvnw spring-boot:run
   ```

3. Swagger UIs: http://localhost:8086/swagger-ui.html (Query), http://localhost:8085/swagger-ui.html (Inventory)

### Docker-in-Docker cgroup fix (Cloud Agent VMs)
The Cloud Agent VM runs inside a container with cgroupv2 in `domain threaded` mode, which prevents Docker containers from starting. Before starting `dockerd`, run:
```bash
echo "-cpu -pids -cpuset -io -memory -hugetlb" | sudo tee /sys/fs/cgroup/cgroup.subtree_control
sudo mkdir -p /sys/fs/cgroup/init
for pid in $(cat /sys/fs/cgroup/cgroup.procs 2>/dev/null); do
  echo $pid | sudo tee /sys/fs/cgroup/init/cgroup.procs >/dev/null 2>&1 || true
done
echo "+cpu +io +memory +pids" | sudo tee /sys/fs/cgroup/cgroup.subtree_control
```
Then start dockerd normally. Without this, containers fail with "cannot enter cgroupv2 ... domain controllers -- it is in an invalid state".

### Lint
No dedicated linter config (ESLint/Checkstyle) is configured in this repo. Maven `verify` runs JaCoCo coverage checks which serve as the quality gate.

### Key ports
| Service | Port |
|---------|------|
| Inventory REST | 8085 |
| Query REST | 8086 |
| gRPC | 9090 |
| Config Server | 8888 |
| Admin Server | 8089 |
| Kafka | 9092 |
| Redis | 6379 |
| Zipkin | 9411 |
| Prometheus | 9412 |
| Kafka Web UI | 3030 |
