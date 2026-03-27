# ELK / Kibana: Best Practices and Dashboard Guidance

This document complements [KIBANA_DASHBOARDS_PROPOSAL.md](KIBANA_DASHBOARDS_PROPOSAL.md) and [elk/kibana-dashboards/README.md](../elk/kibana-dashboards/README.md). It frames **Kibana** usage for services that log to Elasticsearch via `application-logs` → Kafka Connect, from the perspectives of a **lead operations** viewpoint (day-to-day reliability, triage, runbooks) and **architecture** (cross-cutting signals, boundaries between logs vs metrics/traces, and what production-grade implies).

---

## How logging fits this project

- **Path**: Query and Inventory emit JSON logs (Logstash encoder) to Kafka topic `application-logs`, ingested by Kafka Connect into Elasticsearch; **Kibana** data view `application-logs*`.
- **Enriched fields today** (see [README](../README.md) and [KIBANA_DASHBOARDS_PROPOSAL.md](KIBANA_DASHBOARDS_PROPOSAL.md)): `@timestamp`, `level`, `logger_name`, `message`, `thread_name`, `traceId`, `spanId`, `parentSpanId`, `service`, `environment`, `host`, `stack_trace` (on errors).
- **Correlation**: Use `traceId` with **Zipkin** for request-scoped investigation; **Prometheus/Grafana** already cover RED-style numeric signals (rates, latencies, JVM, Kafka lag, etc.)—see README “Grafana dashboards”.

**Architectural split (recommended)**:

| Signal | Primary home in this stack | Role |
|--------|----------------------------|------|
| Latency / throughput / error **rates** as numbers | Grafana (Actuator/Micrometer) | SLOs, alerting, capacity |
| **Why** something failed, message context, stack traces | Kibana (logs) | Root cause, pattern search |
| End-to-end request path | Zipkin + log `traceId` | Distributed debugging |

Kibana dashboards should **not duplicate** Grafana time-series unless there is a clear ops reason (for example teaching demos); they should emphasize **log-native** questions: volume, severity, error fingerprints, correlation, and audit-style narratives.

---

## Kibana dashboard best practices (industry + Elastic)

The following align with Elastic’s [Dashboard guidelines](https://www.elastic.co/guide/en/integrations-developer/current/dashboard-guidelines.html) and common production observability practice:

1. **Stable stack**: Build and export dashboards on **released** Kibana versions; version-control exported NDJSON (this repo provisions from `elk/kibana-dashboards/`).
2. **Fewer panels per dashboard**: Split by persona or question; link dashboards with **Markdown** notes or **drilldowns** instead of one overcrowded board.
3. **Top-down layout**: Put summary KPIs and “is the system healthy?” charts at the top; increase detail toward the bottom.
4. **Lens first**: Prefer **Lens** for new panels; keep titles purposeful and non-redundant.
5. **Scoped queries**: Prefer panel-level filters (e.g. `service`, `level`, `logger_name`) over querying the entire index without bounds—reduces load and clarifies intent.
6. **Controls**: Use **dashboard controls** (dropdowns) for `service`, `environment`, and time ranges operators care about.
7. **Performance**: Sensible auto-refresh (not faster than needed); cap table “top N”; avoid huge unbounded keyword aggregations in production.
8. **By value**: Prefer visualizations **embedded in the dashboard** (by value) so imports are self-contained unless a panel must be reused identically everywhere.
9. **Governance**: When log schema changes, update data views, ingest mappings, and dashboards in the same change; document field semantics for consumers.

---

## Production-level metrics and “what to watch” (SRE framing)

These frameworks are widely used to decide **what** to measure; in this project they map across **Grafana + Zipkin + Kibana**:

### Four Golden Signals (Google SRE)

- **Latency**: Percentiles (p50/p95/p99) per route or dependency—**Grafana** from HTTP/gRPC timers; in **logs**, watch slow-request log lines or structured `duration_ms` if added.
- **Traffic**: Requests/sec, events/sec—**Grafana**; **logs** show bursts as log volume per `service`.
- **Errors**: 5xx rate, timeouts, circuit-breaker opens—**Grafana**; **logs** carry exceptions, `stack_trace`, and WARN/ERROR patterns.
- **Saturation**: Thread pools, connection pools, GC, Kafka consumer lag—**Grafana**/exporters; **logs** may show “pool exhausted”, retries—treat as secondary evidence.

### RED (microservices)

- **Rate**, **Errors**, **Duration** per service—primarily **metrics**; logs support **Errors** via ERROR count and message clustering.

### USE (resources)

- **Utilization**, **Saturation**, **Errors** for Redis, Kafka, Elasticsearch, JVM—**Grafana** in this repo; correlate with log spikes when incidents occur.

**Takeaway for Kibana**: Dashboards should answer **“what broke, where, and in what pattern?”** and **“which traces/users/orders are affected?”**—not replace latency histograms that Micrometer already exposes.

---

## Kibana panels vs metrics in the Prometheus scrape stream

Provisioned Kibana dashboards do **not** read Prometheus; every number is a **document count or aggregation** over `application-logs*`. Use the table below when triaging: start from a spike or drop in Kibana, then open Grafana (**Application observability**, **Pet Shop Overview**, or **Infrastructure**) on the **same time range** and check the paired metrics (all come from `/actuator/prometheus` on the apps plus exporters defined in [prometheus.yml](../prometheus/prometheus.yml)).

| Kibana panel / query (concept) | What logs actually measure | Pair in Grafana (dashboard) — Prometheus family |
|--------------------------------|----------------------------|--------------------------------------------------|
| Log volume over time (all levels) | Emitted log lines per bucket | **Traffic proxy**: `rate(http_server_requests_seconds_count[5m])` by instance; **Pet Shop Overview** — `pets_queried_total`, `inventory_queries_total`, `orders_queries_total`; Kafka: `rate(kafka_producer_record_send_total[5m])` (**Infrastructure**) |
| Volume split by `service` | Lines tagged `query-microservice` vs `inventory-microservice` | Same HTTP/timer metrics filtered mentally by instance (**8086** = Query, **8085** = Inventory) or use `instance` label on scrape targets |
| Volume by `level` | INFO / WARN / ERROR counts | **Errors (HTTP)**: `http_server_requests_seconds_count` with `status=~"5.."`; not identical to log ERROR but often rises together |
| ERROR count (Lens metric, time picker) | Documents with `level: ERROR` | **Latency/errors context**: timer error rates if instrumented; business counters dropping — `orders_updated_total`, `pet_adoptions_total` (**Pet Shop Overview**); Redis failures `reservations_redis_unavailable_total` |
| WARN count | Documents with `level: WARN` | Often precursors to HTTP 4xx/5xx or circuit events; compare `http_server_requests_seconds_count` by `status` |
| Errors / warnings over time | ERROR or WARN document histograms | **Timers**: `rate(*_time_seconds_count[5m])` vs `rate(*_time_seconds_sum[5m]) / rate(*_time_seconds_count[5m])` for `pet_query_time`, `pet_adoption_time`, `inventory_query_time`, `orders_*_time` (**Pet Shop Overview**) |
| Errors by `service` | ERROR count grouped by `service` | Per-service HTTP load and status on **8085** / **8086**; JVM pressure `jvm_memory_used_bytes` (**Infrastructure**) |
| ERROR/WARN **over time by service** (Log Severity dashboard) | Same as above, time-split per service | **Application observability**: `http_server_requests_seconds_count` by `status` (4xx/5xx rates); **Pet Shop Overview**: domain timers if one service degrades |
| ERROR with `stack_trace` (metric) | Thrown exceptions logged with stack | **Application observability** 5xx rate; **Pet Shop Overview** business/error counters (`reservations_redis_unavailable_total`, etc.) |
| Top error `logger_name` | Which Java loggers emit ERROR | Narrow code area; pair with **Zipkin** for that window if traces exist |
| Logs with `traceId` (metric / time series) | Documents where trace ID is present | **Zipkin** trace count for the same interval; HTTP request rate as volume ceiling |
| Logs per minute by service | Throughput of log lines | Same as “log volume” row; **Elasticsearch** `sum(elasticsearch_indices_docs)` growth (**Infrastructure**) as storage sanity check |

**Important distinctions**

- **Log ERROR ≠ HTTP 5xx**: stack traces and `logger_name` explain failures that metrics may not label per route until you add structured fields (see below).
- **`service` (logs) ↔ `instance` (Prometheus)**: logs carry `service`; Prometheus uses host/port on scrape targets — map **8086** to Query and **8085** to Inventory when comparing.
- **Elasticsearch document counts** in Grafana reflect **all** indices the exporter sees; Kibana panels target **`application-logs*`** only — use both to detect pipeline issues (ES growing but Kibana flat → Connect/consumption lag: **Infrastructure** `kafka_consumergroup_lag`).

---

## Log querying and performance (Kibana / Elasticsearch)

These practices keep searches and dashboards cheap as `application-logs` grows:

1. **Bound time first**: Always set the time picker (or dashboard default) before heavy Discover queries; full-index scans dominate cost.
2. **Filter on keyword fields**: Prefer `level`, `service`, `logger_name`, `environment`, `host`, `traceId` — they map to efficient filters. Use **Discover** filters or KQL, not only free-text search, when possible.
3. **Full-text (`message`) last**: Leading wildcards (`*timeout`) and broad `message:` queries are expensive on analyzed text. Narrow with `level: ERROR` and `service: "query-microservice"` first, then search message.
4. **Discover vs Lens tables**: For “last N errors with full message”, use **Discover** with columns; Lens tables on analyzed `message` are fragile (see proposal doc). Prefer `logger_name` + `stack_trace` for aggregation-friendly views.
5. **Dashboard auto-refresh**: Use a conservative interval (for example 1m) for demos; shorter intervals multiply ES load with every viewer.
6. **Aggregation size**: Keep “Top N” loggers/services to modest N (10–25) for responsiveness.
7. **Correlation workflow**: `traceId` exact match from Zipkin is cheap; avoid `traceId: *` alone on huge windows without additional filters.

**Pipeline / stack tuning (beyond the demo)**

- **Kafka Connect sink**: For higher throughput, tune connector `batch.size`, `linger.ms`, and max tasks in line with [Confluent Elasticsearch Sink](https://docs.confluent.io/kafka-connect-elasticsearch/current/) guidance; watch **consumer lag** in Grafana while changing batch behavior.
- **Elasticsearch**: Demo uses a single node and 512m heap — **not** sized for sustained high log rates. In production: ILM or rollover policies, appropriate shard count, and `refresh_interval` tradeoffs for indexing vs search freshness.
- **Indices**: One data stream or index pattern per concern (app vs audit vs Connect logs) avoids giant mixed `application-logs*` and simplifies retention.

---

## ELK stack improvements (roadmap)

Reasonable next steps when moving from this demo toward something production-like:

| Area | Current demo | Improvement |
|------|----------------|-------------|
| **Security** | Elasticsearch/Kibana without auth | Enable Elastic security, TLS, roles for Kibana spaces and index privileges |
| **Lifecycle** | Logs accumulate in `application-logs*` | ILM: hot–warm–delete or rollover by size/age; shrink retention for noisy indices |
| **Ingest** | Kafka Connect JSON sink | Optional ingest pipelines (enrich, truncate `stack_trace` length, PII scrub); explicit mappings for fields you aggregate on (`message.keyword` if needed) |
| **Observability of ELK** | Grafana ES exporter only | Stack Monitoring or Metricbeat for ES/Kibana/Connect JVM and queue depth |
| **Resilience** | Single ES node | Multi-node cluster, snapshot repository, documented restore runbook |
| **Connect ops** | Connector registered once by elk-init | Health checks on connector `FAILED` state; alert on task failures (metrics or log scrape from Connect) |
| **Cost / quality** | All levels to Kafka | Sampling for INFO, guaranteed ERROR/WARN; dynamic log level via Spring Boot actuator where appropriate |

---

## What to log and structure (ops + architect)

**Already strong in this project**: JSON logs, trace MDC, `service` / `environment` / `host`, Kafka pipeline into ES.

**High-value additions for production-style operations** (implementation is optional; fields should be consistent once introduced):

| Area | Suggested structured fields / practices | Why |
|------|----------------------------------------|-----|
| HTTP/gRPC | `http_method`, `http_path` or `grpc_service`, `grpc_method`, `status_code`, `outcome` (success/failure) | Filter errors by route; RED-like views from logs when metrics gap |
| Duration | `duration_ms` on request/access logs | Link slow logs to traces without opening every span |
| Errors | Stable `error.type` / `error.code`; keep `stack_trace` | Group “same” failures in Kibana; reduce noise on `message` text |
| Kafka | `topic`, `partition`, `consumer_group` (on failures) | Debug `order-events-v1`, `adoption-events-v1`, `application-logs` issues |
| Business context | `pet_id`, `order_id`, `reservation_token` (hashed if sensitive) | Support “single entity” investigations |
| Security / abuse | `client_ip` (policy-permitting), auth failure reason codes | Separate compliance-oriented dashboard if needed |
| Config | Log effective `spring.application.name`, profile, important feature flags at startup | “Drift” and misconfiguration hunts |

**Sampling**: At high volume, consider sampling **INFO** access logs while keeping **ERROR** and **WARN** complete—architects trade cost vs fidelity; ops needs guaranteed error paths.

---

## Implemented vs future dashboards

**Shipped in `elk/kibana-dashboards/`** (imported by elk-init; bodies generated by [render_kibana_dashboards.py](../elk/scripts/render_kibana_dashboards.py) unless you hand-edit NDJSON):

| Dashboard file | Role |
|----------------|------|
| `log-overview.ndjson` | Executive-style log health: volume by time/service/level + ERROR/WARN **metric** counts for the **selected time range** |
| `microservices-health.ndjson`, `logs-by-service-level.ndjson` | Combined ERROR/WARN trends and quick service/level splits |
| `error-warning-monitoring.ndjson` | Incident-style: errors/warnings over time, errors by service, top error loggers |
| `trace-correlation.ndjson` | `traceId` volume over time + instructions (Discover + Zipkin + Grafana) |
| `log-operations.ndjson` | Logs/minute by service, logger pie, traced log count |
| `service-query-logs.ndjson` | Query-only (`service: "query-microservice"`) |
| `service-inventory-logs.ndjson` | Inventory-only (`service: "inventory-microservice"`) |
| `log-severity-by-service.ndjson` | ERROR/WARN over time split by service; INFO volume; ERROR with `stack_trace` |

**Provisioning**: [docker-init.sh](../elk/init/docker-init.sh) and [provision-kibana.sh](../elk/provision-kibana.sh) treat Kibana imports as successful only when the API returns `"success": true`, and import NDJSON in sorted order so the data view is created before dependent dashboards.

---

## Proposed **additional** dashboards (future / optional)

The items below extend the model when you add fields, indices, or operational scope.

### Kafka & pipeline health (logs + ops)

- Logs from Kafka clients (if logged): producer send failures, consumer errors.
- **Connect / ES** (if you ingest connector logs into the same or another index): connector task failures, DLQ—today Connect logs may be Docker-only; architects often add a small “pipeline” index for this in production.

### Security & configuration (optional, production)

- Auth failures, config fetch failures from Config Server, unexpected profile.
- Rate of ERROR from `org.springframework` packages after releases.

### Business / domain events (optional)

- Structured adoption/order events if logged at INFO with stable keys—useful for product analytics; keep PII policy in mind.

### Richer triage in Kibana

- **Recent errors table** with full `message` text works best in **Discover** (or after adding a `message.keyword` / length field)—see [KIBANA_DASHBOARDS_PROPOSAL.md](KIBANA_DASHBOARDS_PROPOSAL.md).
- Optional: breakdown by `http_path` / `status_code` once those exist as structured log fields.

---

## Representing metrics and values in Kibana (practical tips)

- **Use consistent units** in titles (“errors per minute”, “log events per second”).
- Prefer **time series** for trends, **top values** for dimensions (`logger_name`, `service`), **tables** for last N raw events with links to Discover.
- **Color**: Use one accent for ERROR/WARN; avoid rainbow categoricals on the same chart.
- **Dashboard variables**: `service`, `environment`, optional `traceId` text control for paste-from-Zipkin workflows.
- **Cross-link**: Markdown panel with URLs to Grafana (Pet Shop Overview / Infrastructure) and Zipkin—operators already use multiple UIs; one place that lists them reduces onboarding time.

**Interfaces (this stack)**:

| UI | Port | Role |
|----|------|------|
| Grafana | 3000 | **Application observability** (HTTP 4xx/5xx, p95, reservation timers), Pet Shop Overview, Infrastructure |
| Prometheus | 9412 | Raw targets and scrape health |
| Zipkin | 9411 | Trace timeline; source of `traceId` for Kibana |
| Kibana | 5601 | Log search, dashboards on `application-logs*` |

---

## Relation to existing artifacts

| Artifact | Purpose |
|----------|---------|
| [KIBANA_DASHBOARDS_PROPOSAL.md](KIBANA_DASHBOARDS_PROPOSAL.md) | Panel-by-panel proposal, KQL examples, mapping to NDJSON files |
| [elk/kibana-dashboards/](../elk/kibana-dashboards/) | Provisioned NDJSON dashboards |
| [render_kibana_dashboards.py](../elk/scripts/render_kibana_dashboards.py) | Regenerates dashboard NDJSON (except `application-logs-dataview.ndjson`) |
| Grafana provisioned JSON under `grafana/provisioning/` | Numeric SLO-friendly metrics—pair with Kibana for investigations |

---

## References

- Elastic: [Dashboard guidelines](https://www.elastic.co/guide/en/integrations-developer/current/dashboard-guidelines.html)
- SRE metrics overview: [Four Golden Signals, RED, USE](https://logz.io/blog/evops-sre-metrics/) (and Google SRE book—latency, traffic, errors, saturation)
- Grafana: [RED method](https://grafana.com/blog/the-red-method-how-to-instrument-your-services/) (microservices request metrics—complements log-based error analysis)

---

*Aligned with the Microservices OPS Demo stack (Query, Inventory, Kafka, Elasticsearch, Kibana, Zipkin, Prometheus/Grafana) as described in the project README. This file supersedes the root-level “ELK update” draft; use this path for stable links.*
