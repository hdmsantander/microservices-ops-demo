# PR Review: Microservice Resilience Patterns

## Summary

Review of the `start.sh` script, tests, and Docker stack for best practices. Includes port validation, test verification, and port assignments that avoid conflicts with infrastructure (e.g. landoop Schema Registry on 8081).

---

## Best Practices Applied

### 1. Port Validation

- **Before Docker (minimal/full)**: Script checks required ports are free before starting containers. Fails early with a clear error if any port is in use.
- **After tests (`--tests-only`)**: Validates ports are free after tests complete, surfacing test process leaks (e.g. EmbeddedKafka not shutting down).

**Ports checked:**

| Port | Service |
|------|---------|
| 8085 | Inventory microservice |
| 8086 | Query microservice |
| 9092 | Kafka |
| 9090 | Prometheus |
| 9411 | Zipkin |

*Note: Ports 8085/8086 were chosen to avoid conflict with `landoop/fast-data-dev` Schema Registry (8081), Kafka Connect (8083), and other common services.*

### 2. Test Summary & Coverage

- Maven Surefire reports parsed for Maven-style output: `Tests run: X, Failures: Y, Errors: Z, Skipped: W`
- JaCoCo coverage summary with instruction % per microservice
- Report paths printed for HTML coverage

### 3. Fail-Fast Behavior

- `set -e` for immediate exit on errors
- Port check runs after `mvn package` (tests) and before `docker compose` in full mode
- Reduces risk of Docker startup failures due to port conflicts

### 4. Portable Port Detection

- Uses `ss` on Linux, with fallback to `nc` or Bash `/dev/tcp`
- Regex `:${port}[^0-9]` avoids false matches (e.g. 80860 vs 8086)

### 5. Docker Compose Consistency

- `docker-compose.yml` and `docker-compose-minimal.yml` both use `version: '3.9'` for consistency

---

## Resolved: landoop Port Conflict

Previously, inventory used port 8081, which conflicted with `landoop/fast-data-dev` Schema Registry. **Resolved** by moving microservices to 8085 (inventory) and 8086 (query).

---

## Minor Notes

### GitHub Workflow vs start.sh

- **Workflow** (`.github/workflows/test.yml`): Uses `mvn test`
- **start.sh --tests-only**: Uses `mvn verify` (tests + JaCoCo)

Both run the same tests. `mvn verify` adds coverage; CI keeps `mvn test` for speed.

---

## Test Verification

| Run | Result |
|-----|--------|
| `./start.sh --tests-only` | ✅ All tests passed, ports OK |
| `mvn -B test -f query-microservice/pom.xml` | ✅ Pass |
| `mvn -B test -f inventory-microservice/pom.xml` | ✅ Pass |
| Port check with port in use | ✅ Correctly fails with clear error |

---

## Script Flow

```
--tests-only:
  mvn verify (query) → mvn verify (inventory) → test summary → coverage summary → port check (8085, 8086, 9092, 9090, 9411) → exit

minimal:
  port check (9092, 9090, 9411) → docker compose up

full:
  mvn package (tests) → port check (all 5 ports) → docker compose build → docker compose up
```

---

## Files Updated for Port Change

| File | Change |
|------|--------|
| `inventory-microservice/.../application.yml` | `server.port: 8085` |
| `query-microservice/.../application.yml` | `server.port: 8086` |
| `query-microservice/.../InventoryService.java` | `http://localhost:8085/v1/inventory` |
| `query-microservice/.../MainController.java` | OpenAPI description |
| `query-microservice/Dockerfile` | Health check `localhost:8086` |
| `inventory-microservice/Dockerfile` | Health check `localhost:8085` |
| `prometheus/prometheus.yml` | Targets `8085`, `8086` |
| `start.sh` | `PORTS_FULL="8085 8086 ..."` |
| `README.md` | All port references updated |
