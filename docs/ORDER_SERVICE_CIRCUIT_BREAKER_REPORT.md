# OrderService Circuit Breaker & Resilience Scenarios Report

## Overview

This report documents the OrderService resilience logic for retrieving orders from the PetShop API, the different scenarios needed to trigger expected circuit breaker behavior, and the expected runtime behavior including probability considerations for the PetStore API's `nextInt`-based order ID availability.

---

## 1. OrderService Logic Summary

### 1.1 Update Flow

- **Method:** `updateOrders()` — iterates over order IDs `{1, 2, 3, 4, 5}` (PetStore accepts IDs ≤5 or >10)
- **Per order:** Calls `self.fetchOrder(orderId)` through the AOP proxy (enabling Circuit Breaker and Retry)
- **Error handling:**  
  - `HttpClientErrorException.NotFound` (404): Logged, no retry (via `ignoreExceptions` in Retry config)  
  - Other exceptions: Logged, fallback returns `null`

### 1.2 Resilience Configuration (application.yml)

| Pattern      | Instance     | Settings |
|-------------|--------------|----------|
| CircuitBreaker | orderService | failureRateThreshold: 20, slowCallRateThreshold: 100, slowCallDurationThreshold: 60s, permittedNumberOfCallsInHalfOpenState: 5, slidingWindowSize: 20, minimumNumberOfCalls: 5 |
| Retry       | orderService | maxAttempts: 5, waitDuration: 500ms, exponentialBackoff, **ignoreExceptions: NotFound** |

---

## 2. Mock Response Scenarios for Testing

### 2.1 Scenario Matrix

| # | Scenario                  | Mock Response                         | Expected API Calls | Events Sent | Circuit State After |
|---|---------------------------|----------------------------------------|--------------------|-------------|---------------------|
| 1 | All success                | `200 OK` + `OrderDto` for each order   | 5                  | 5           | CLOSED              |
| 2 | All 404 (expected)         | `404 Not Found` for each order         | 5 (no retry)       | 0           | CLOSED*             |
| 3 | All 500 (server error)     | `500 Internal Server Error` for all   | 5–25†              | 0           | OPEN               |
| 4 | Mix 404 and 200            | Alternating 404/200 per order          | 5                  | 2           | CLOSED              |
| 5 | Circuit OPEN               | (after scenario 3)                     | 0 (fallback only)  | 0           | OPEN               |

\* 404 may or may not be counted as failures depending on Circuit Breaker config; Retry ignores 404.  
† See §4 on retry behavior; observed behavior may be 5 calls due to proxy/retry interaction.

### 2.2 Scenario Details

#### Scenario 1: All Success

- **Mock:** `when(restTemplate.getForEntity(...)).thenReturn(ResponseEntity.ok(orderDto))` for all 5 orders  
- **Behavior:** 5 API calls, 5 events sent to Kafka  
- **Validation:** `verify(restTemplate, times(5))`, `verify(orderEventSender, times(5))`

#### Scenario 2: All 404 (Expected)

- **Mock:** `doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, ...))`  
- **Behavior:** 1 API call per order (no retry for 404), 0 events  
- **Validation:** `verify(restTemplate, times(5))`, `verify(orderEventSender, never())`

#### Scenario 3: All 500 (Server Error)

- **Mock:** `doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))`  
- **Behavior:** Retry up to 5 times per order; after retries exhausted, fallback returns `null`; Circuit Breaker records failures  
- **Validation:** `verify(restTemplate, atLeast(5))`, `verify(orderEventSender, never())`

#### Scenario 4: Mix 404 and 200

- **Mock:** `thenThrow(notFound).thenReturn(ok).thenThrow(notFound).thenReturn(ok).thenThrow(notFound)`  
- **Behavior:** 5 API calls, 2 events (only for successful orders)  
- **Validation:** `verify(restTemplate, times(5))`, `verify(orderEventSender, times(2))`

#### Scenario 5: Circuit OPEN

- **Precondition:** Scenario 3 executed (≥5 failures, circuit OPEN)  
- **Mock:** Same 500 stub (not used when circuit is OPEN)  
- **Behavior:** No API calls; fallback used immediately  
- **Validation:** `clearInvocations(restTemplate)` before 6th `updateOrders()`, then `verify(restTemplate, never())`

---

## 3. Circuit Breaker State Transitions

### 3.1 Conditions for OPEN

- `minimumNumberOfCalls`: 5 (must have at least 5 evaluated calls)
- `failureRateThreshold`: 20%  
- **Example:** 5 calls, 5 failures → 100% > 20% → OPEN

### 3.2 Runtime Sequence

```
1. updateOrders() × 1: fetchOrder(1)…fetchOrder(5)
   - Each fetchOrder: 1 success or 1–5 attempts (depending on Retry)
   - After 5 failures: circuit records failure rate and can OPEN

2. Next updateOrders(): if OPEN → all 5 fetchOrder calls use fallback, 0 API calls
```

### 3.3 Sliding Window

- **slidingWindowSize:** 20 (last 20 calls)
- **Behavior:** Old successes/failures slide out; circuit re-evaluates when new calls are recorded

---

## 4. Probability Considerations (PetStore API)

### 4.1 Order ID Availability

- **PetStore API rules:** Order IDs ≤5 or >10 are valid; IDs 6–10 return 404.
- **Current OrderService:** Uses fixed IDs `{1, 2, 3, 4, 5}` to avoid 404 (per code comment).
- **Original design (README):** Orders "generated randomly as integers in the range of 1-10".

### 4.2 nextInt(1-10) Probability for Original Design

If order IDs were chosen with `Random().nextInt(10) + 1` (range 1–10):

| Outcome      | IDs   | Count | P(per call) |
|--------------|-------|-------|-------------|
| 200 OK       | 1–5   | 5     | 50%         |
| 404 Not Found| 6–10  | 5     | 50%         |

- **Per `updateOrders()` (5 orders):** Expected 2.5 success, 2.5×404.
- **404 handling:** Retry does not retry (ignoreExceptions); 1 API call per 404 order.
- **Circuit Breaker:** 404 is still recorded as a failure unless explicitly ignored in Circuit Breaker config. With 5 orders and 50% 404, expect ~2.5 failures per run; circuit would typically open after a few runs.

### 4.3 Expected Runtime Behavior

| Scenario                 | Approx. API load (per updateOrders) | Notes                          |
|--------------------------|-------------------------------------|--------------------------------|
| All IDs valid (1–5)      | 5 calls                             | No 404, normal flow             |
| Mixed valid/404          | 5 calls                             | 404 no retry, 1 call per order  |
| External 500 errors      | 5–25 calls                          | Depends on retry configuration  |
| Circuit OPEN             | 0 calls                             | Fallback only                  |

---

## 5. Test Coverage Summary

| Test                             | Scenario | Assertions                                              |
|----------------------------------|----------|---------------------------------------------------------|
| `orderService_updateOrders_all_success_sends_events` | 1        | 5 API calls, 5 events                                  |
| `orderService_updateOrders_404_no_retry_one_call_per_order` | 2        | 5 API calls, 0 events                                  |
| `orderService_updateOrders_500_triggers_retry_then_fallback` | 3        | ≥5 API calls, 0 events                                 |
| `orderService_updateOrders_circuit_open_no_api_calls` | 5        | 0 API calls after opening circuit                      |
| `orderService_updateOrders_mix_404_and_success`      | 4        | 5 API calls, 2 events                                  |

---

## 6. Recommendations

1. **404 and Circuit Breaker:**  
   If 404 should not contribute to opening the circuit, add `ignoreExceptions` for `HttpClientErrorException.NotFound` in the Circuit Breaker config.

2. **Retry vs. Circuit Breaker:**  
   Aspect order (Circuit Breaker=1, Retry=2) ensures Retry is inner and Circuit Breaker is outer, so retries occur before the circuit records a failure.

3. **Observability:**  
   Use Resilience4j metrics and events (and optionally Actuator) to monitor circuit state, retries, and fallbacks in production.
