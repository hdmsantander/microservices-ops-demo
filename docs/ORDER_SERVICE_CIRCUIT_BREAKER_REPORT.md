# OrderService Circuit Breaker & Resilience Scenarios Report

## Overview

This report documents the OrderService resilience logic for retrieving orders from the PetShop API, the different scenarios needed to trigger expected circuit breaker behavior, and the expected runtime behavior including probability considerations for `ThreadLocalRandom.nextInt(1, 11)` (range 1–10).

---

## 1. OrderService Logic Summary

### 1.1 Update Flow

- **Method:** `updateOrders()` — iterates **3 times**; each iteration draws a random order ID
- **Order ID selection:** `ThreadLocalRandom.current().nextInt(1, 11)` → range **1–10** (PetStore: IDs ≤5 or >10 valid; 6–10 return 404)
- **Per iteration:** Calls `self.fetchOrder(orderId)` (via AOP proxy for Circuit Breaker/Retry)
- **Error handling:**  
  - `HttpClientErrorException.NotFound` (404): Logged, no retry (via `ignoreExceptions` in Retry config)  
  - Other exceptions: Logged, fallback returns `null`

### 1.2 Resilience Configuration (application.yml)

| Pattern      | Instance     | Settings |
|-------------|--------------|----------|
| CircuitBreaker | orderService | failureRateThreshold: 50, slowCallRateThreshold: 100, slowCallDurationThreshold: 60s, permittedNumberOfCallsInHalfOpenState: 5, slidingWindowSize: 100, minimumNumberOfCalls: 5, **ignoreExceptions: NotFound** |
| Retry       | orderService | maxAttempts: 5, waitDuration: 500ms, exponentialBackoff, **ignoreExceptions: NotFound** |

---

## 2. Mock Response Scenarios for Testing

### 2.1 Scenario Matrix

| # | Scenario                  | Mock Response                         | Expected API Calls | Events Sent | Circuit State After |
|---|---------------------------|----------------------------------------|--------------------|-------------|---------------------|
| 1 | All success                | `200 OK` + `OrderDto` for each order   | 3                  | 0–3         | CLOSED              |
| 2 | All 404 (expected)         | `404 Not Found` for each order         | 3 (no retry)       | 0           | CLOSED*             |
| 3 | All 500 (server error)     | `500 Internal Server Error` for all   | 3–15†              | 0           | OPEN               |
| 4 | Mix 404 and 200            | Mixed 404/200 per order                | 3                  | 0–3         | CLOSED              |
| 5 | Circuit OPEN               | (after scenario 3)                     | 0 (fallback only)  | 0           | OPEN               |

\* 404 is excluded from circuit breaker metrics via ignoreExceptions; Retry also ignores 404.  
† 3 fetches × up to 5 retries each; circuit needs ≥5 failures to OPEN, so ≥2 `updateOrders()` runs.

### 2.2 Scenario Details

#### Scenario 1: All Success

- **Mock:** `when(restTemplate.getForEntity(...)).thenReturn(ResponseEntity.ok(orderDto))` for all 3 calls  
- **Behavior:** 3 API calls per `updateOrders()`, 0–3 events depending on which random IDs (1–5) succeed  
- **Validation:** `verify(restTemplate, times(3))`, `verify(orderEventSender, times(3))` when all 3 IDs are valid

#### Scenario 2: All 404 (Expected)

- **Mock:** `doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, ...))`  
- **Behavior:** 1 API call per fetch (no retry for 404), 0 events  
- **Validation:** `verify(restTemplate, times(3))`, `verify(orderEventSender, never())`

#### Scenario 3: All 500 (Server Error)

- **Mock:** `doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))`  
- **Behavior:** Retry up to 5 times per fetch; after retries exhausted, fallback returns `null`; Circuit Breaker records failures. With 3 fetches per run, ≥2 runs needed to reach 5 failures (OPEN)  
- **Validation:** `verify(restTemplate, atLeast(3))`, `verify(orderEventSender, never())`

#### Scenario 4: Mix IDs 1–5 (success) vs 6–10 (404)

- **Mock:** `thenAnswer` returning 200 for order IDs 1–5, throwing 404 for 6–10  
- **Behavior:** 3 API calls, 0–3 events depending on random IDs drawn  
- **Validation:** `verify(restTemplate, times(3))`, `verify(orderEventSender, atLeast(0))`, `atMost(3)`

#### Scenario 5: Circuit OPEN

- **Precondition:** Scenario 3 executed (≥5 failures, circuit OPEN)  
- **Mock:** Same 500 stub (not used when circuit is OPEN)  
- **Behavior:** No API calls; fallback used immediately  
- **Validation:** `clearInvocations(restTemplate)` before next `updateOrders()`, then `verify(restTemplate, never())`

---

## 3. Circuit Breaker State Transitions

### 3.1 Conditions for OPEN

- `minimumNumberOfCalls`: 5 (must have at least 5 evaluated calls)
- `failureRateThreshold`: 50%  
- **Example:** 5 calls, 5 failures → 100% > 50% → OPEN

### 3.2 Runtime Sequence

```
1. updateOrders() × 1: 3 iterations, each fetchOrder(random 1–10)
   - Each fetchOrder: 1 success or 1–5 attempts (depending on Retry)
   - minimumNumberOfCalls=5 → need ≥2 updateOrders() runs to evaluate circuit
   - After ≥5 failures: circuit OPEN

2. Next updateOrders(): if OPEN → all 3 fetchOrder calls use fallback, 0 API calls
```

### 3.3 Sliding Window

- **slidingWindowSize:** 100 (last 100 calls)
- **Behavior:** Old successes/failures slide out; circuit re-evaluates when new calls are recorded

---

## 4. Probability Considerations (PetStore API)

### 4.1 Order ID Availability

- **PetStore API rules:** Order IDs ≤5 or >10 are valid; IDs 6–10 return 404.
- **Current OrderService:** Uses `ThreadLocalRandom.current().nextInt(1, 11)` — **3 iterations**, each with a random ID in range **1–10**.

### 4.2 nextInt(1, 11) Probability (Current Implementation)

`ThreadLocalRandom.current().nextInt(1, 11)` yields values 1–10 (inclusive 1, exclusive 11):

| Outcome      | IDs   | Count | P(per call) |
|--------------|-------|-------|-------------|
| 200 OK       | 1–5   | 5     | 50%         |
| 404 Not Found| 6–10  | 5     | 50%         |

- **Per `updateOrders()` (3 fetches):** Expected 1.5 success, 1.5×404.
- **404 handling:** Retry does not retry (ignoreExceptions); 1 API call per 404 order.
- **Circuit Breaker:** 404 is still recorded as a failure unless explicitly ignored in Circuit Breaker config. With 3 fetches and 50% 404, expect ~1.5 failures per run; circuit needs ≥2 runs to reach 5 evaluated calls and can open after 2–3 runs if most calls fail.

### 4.3 Expected Runtime Behavior

| Scenario                 | Approx. API load (per updateOrders) | Notes                          |
|--------------------------|-------------------------------------|--------------------------------|
| Random IDs (1–10)        | 3 calls                             | 3 fetches per run              |
| Mixed valid/404          | 3 calls                             | 404 no retry, 1 call per fetch  |
| External 500 errors      | 3–15 calls                          | 3 fetches × up to 5 retries    |
| Circuit OPEN             | 0 calls                             | Fallback only                  |

---

## 5. Test Coverage Summary

| Test                             | Scenario | Assertions                                              |
|----------------------------------|----------|---------------------------------------------------------|
| `orderService_updateOrders_all_success_sends_events` | 1        | 3 API calls, events for successful fetches             |
| `orderService_updateOrders_404_no_retry_one_call_per_fetch` | 2        | 3 API calls, 0 events                                  |
| `orderService_updateOrders_500_triggers_retry_then_fallback` | 3        | ≥3 API calls, 0 events                                 |
| `orderService_updateOrders_circuit_open_no_api_calls_after_threshold` | 5        | 0 API calls after opening circuit (2 runs = 6 failures) |
| `orderService_updateOrders_mix_ids_1_to_5_success_6_to_10_404` | 4        | 3 API calls, 0–3 events (ID-based thenAnswer)          |
| `orderService_updateOrders_minimum_calls_before_circuit_evaluates` | Edge    | ≥3 API calls, verifies retry/fallback before threshold  |
| `orderService_updateOrders_response_body_null_returns_no_event` | fetchOrder return null | Stub `ResponseEntity.ok().build()`; covers null-body branch |

*Note: Tests use `thenAnswer` with order ID from the request map to simulate PetStore behaviour (IDs 1–5 success, 6–10 404). Inventory stubs use `doReturn().when()` and `eq(INVENTORY_URL)` for reliable matching.*

---

## 6. Code Coverage Report (OrderService)

Coverage from `mvn verify` (JaCoCo):

| Metric       | Covered | Total | Coverage |
|--------------|---------|-------|----------|
| Instructions | 96      | 111   | **86%**  |
| Branches     | 5       | 6     | **83%**  |
| Lines        | 22      | 26    | **85%**  |
| Methods      | 5       | 5     | **100%** |

### Test Logic and Coverage Mapping

| Test | Covers | Logic |
|------|--------|-------|
| `orderService_updateOrders_all_success_sends_events` | Success path | 3 iterations, all return 200+OrderDto; 3 events sent |
| `orderService_updateOrders_404_no_retry_one_call_per_fetch` | 404 handling | Retry ignores 404; 3 API calls, 0 events; fallback returns null |
| `orderService_updateOrders_500_triggers_retry_then_fallback` | 500 + fallback | Retries up to 5× per fetch; fallback invoked; 0 events |
| `orderService_updateOrders_circuit_open_no_api_calls_after_threshold` | Circuit OPEN | 2 runs (6 failures) open circuit; 3rd run uses fallback, 0 API calls |
| `orderService_updateOrders_mix_ids_1_to_5_success_6_to_10_404` | Mixed IDs | `thenAnswer` by order ID (1–5 OK, 6–10 404); 3 calls, 0–3 events |
| `orderService_updateOrders_minimum_calls_before_circuit_evaluates` | Pre-threshold | Verifies retry/fallback before circuit opens |
| `orderService_updateOrders_response_body_null_returns_no_event` | Null body branch | Stub `ResponseEntity.ok().build()`; fetchOrder returns null; 0 events |

### Uncovered (defensive code)

- **catch(NotFound)** in `updateOrders`: CircuitBreaker fallback handles 404 before propagation.
- **catch(Exception)** in `updateOrders`: Fallback handles all exceptions; never propagates.

Report path: `inventory-microservice/target/site/jacoco/mx.hdmsantander.opsdemo.inventory.service/OrderService.html`

---

## 7. Recommendations

1. **404 and Circuit Breaker:**  
   Implemented: `ignoreExceptions` for `HttpClientErrorException.NotFound` is configured on the `orderService` circuit breaker so expected 404s from random order fetches (PetStore: only IDs 1–5 exist) do not trip the circuit.

2. **Retry vs. Circuit Breaker:**  
   Aspect order (Circuit Breaker=1, Retry=2) ensures Retry is inner and Circuit Breaker is outer, so retries occur before the circuit records a failure.

3. **Self-invocation:**  
   `updateOrders()` must call `self.fetchOrder(orderId)` (via `@Lazy @Autowired OrderService self`) so Circuit Breaker and Retry apply; direct `fetchOrder()` calls bypass the AOP proxy.

4. **Observability:**  
   Use Resilience4j metrics and events (and optionally Actuator) to monitor circuit state, retries, and fallbacks in production.
