# PR Review: Microservice Resilience Patterns

## Summary

Review of the `start.sh` script, tests, and Docker stack for best practices. Includes port validation and test verification.

---

## Best Practices Applied

### 1. Port Validation

- **Before Docker (minimal/full)**: Script checks required ports are free before starting containers. Fails early with a clear error if any port is in use.
- **After tests (`--tests-only`)**: Validates ports are free after tests complete, surfacing test process leaks (e.g. EmbeddedKafka not shutting down).

**Ports checked:**

| Port | Service |
|------|---------|
| 8081 | Inventory microservice |
| 8082 | Query microservice |
| 9092 | Kafka |
| 9090 | Prometheus |
| 9411 | Zipkin |

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
- Regex `:${port}[^0-9]` avoids false matches (e.g. 80810 vs 8081)

---

## Potential Concerns

### 1. landoop/fast-data-dev Port Conflict

`landoop/fast-data-dev` exposes Schema Registry on **port 8081**, which is also used by the inventory microservice. With `network_mode: host`, both would contend for 8081.

**Recommendation**: Consider changing inventory to a different port (e.g. 8083) or configuring landoop to use another port for Schema Registry. If the stack runs successfully today, landoop may be configured differently.

### 2. GitHub Workflow vs start.sh

- **Workflow** (`.github/workflows/test.yml`): Uses `mvn test`
- **start.sh --tests-only**: Uses `mvn verify` (tests + JaCoCo)

Both run the same tests. `mvn verify` adds coverage; CI keeps `mvn test` for speed.

### 3. docker-compose.yml Version

`docker-compose.yml` has no `version` key; `docker-compose-minimal.yml` uses `version: '3.9'`. Compose v2 ignores `version`, but consistency can help.

---

## Test Verification

| Run | Result |
|-----|--------|
| `./start.sh --tests-only` | ✅ All tests passed, ports OK |
| `mvn -B test -f query-microservice/pom.xml` | ✅ 20 tests, 0 failures |
| `mvn -B test -f inventory-microservice/pom.xml` | ✅ 19 tests, 0 failures |
| Port check with port 9092 in use | ✅ Correctly fails with clear error |

---

## Script Flow

```
--tests-only:
  mvn verify (query) → mvn verify (inventory) → test summary → coverage summary → port check → exit

minimal:
  port check (9092, 9090, 9411) → docker compose up

full:
  mvn package (tests) → port check (all 5 ports) → docker compose build → docker compose up
```
