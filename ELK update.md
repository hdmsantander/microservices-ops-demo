# ELK / Kibana: Best Practices and Dashboard Guidance (Project Update)

This document complements [docs/KIBANA_DASHBOARDS_PROPOSAL.md](docs/KIBANA_DASHBOARDS_PROPOSAL.md) and [elk/kibana-dashboards/README.md](elk/kibana-dashboards/README.md). It frames **Kibana** usage for services that log to Elasticsearch via `application-logs` → Kafka Connect, from the perspectives of a **chief operations developer** (day‑to‑day reliability, triage, runbooks) and a **senior architect** (cross‑cutting signals, boundaries between logs vs metrics/traces, and what “production‑grade” implies).

---

## How logging fits this project

- **Path**: Query and Inventory emit JSON logs (Logstash encoder) to Kafka topic `application-logs`, ingested by Kafka Connect into Elasticsearch; **Kibana** data view `application-logs*`.
- **Enriched fields today** (see README and proposal): `@timestamp`, `level`, `logger_name`, `message`, `thread_name`, `traceId`, `spanId`, `parentSpanId`, `service`, `environment`, `host`, `stack_trace` (on errors).
- **Correlation**: Use `traceId` with **Zipkin** for request‑scoped investigation; **Prometheus/Grafana** already cover RED‑style numeric signals (rates, latencies, JVM, Kafka lag, etc.)—see README “Grafana dashboards”.

**Architectural split (recommended)**:

| Signal | Primary home in this stack | Role |
|--------|----------------------------|------|
| Latency / throughput / error **rates** as numbers | Grafana (Actuator/Micrometer) | SLOs, alerting, capacity |
| **Why** something failed, message context, stack traces | Kibana (logs) | Root cause, pattern search |
| End‑to‑end request path | Zipkin + log `traceId` | Distributed debugging |

Kibana dashboards should **not duplicate** Grafana time‑series unless there is a clear ops reason (e.g. teaching demos); they should emphasize **log‑native** questions: volume, severity, error fingerprints, correlation, and audit‑style narratives.

---

## Kibana dashboard best practices (industry + Elastic)

The following align with Elastic’s [Dashboard guidelines](https://www.elastic.co/guide/en/integrations-developer/current/dashboard-guidelines.html) and common production observability practice:

1. **Stable stack**: Build and export dashboards on **released** Kibana versions; version‑control exported NDJSON (this repo already provisions from `elk/kibana-dashboards/`).
2. **Fewer panels per dashboard**: Split by persona or question; link dashboards with **Markdown** notes or **drilldowns** instead of one overcrowded board.
3. **Top‑down layout**: Put summary KPIs and “is the system healthy?” charts at the top; increase detail toward the bottom.
4. **Lens first**: Prefer **Lens** for new panels; keep titles purposeful and non‑redundant.
5. **Scoped queries**: Prefer panel‑level filters (e.g. `service`, `level`, `logger_name`) over querying the entire index without bounds—reduces load and clarifies intent.
6. **Controls**: Use **dashboard controls** (dropdowns) for `service`, `environment`, and time ranges operators care about.
7. **Performance**: Sensible auto‑refresh (not faster than needed); cap table “top N”; avoid huge unbounded keyword aggregations in production.
8. **By value**: Prefer visualizations **embedded in the dashboard** (by value) so imports are self‑contained unless a panel must be reused identically everywhere.
9. **Governance**: When log schema changes, update data views, ingest mappings, and dashboards in the same change; document field semantics for consumers.

---

## Production‑level metrics and “what to watch” (SRE framing)

These frameworks are widely used to decide **what** to measure; in this project they map across **Grafana + Zipkin + Kibana**:

### Four Golden Signals (Google SRE)

- **Latency**: Percentiles (p50/p95/p99) per route or dependency—**Grafana** from HTTP/gRPC timers; in **logs**, watch slow‑request log lines or structured `duration_ms` if added.
- **Traffic**: Requests/sec, events/sec—**Grafana**; **logs** show bursts as log volume per `service`.
- **Errors**: 5xx rate, timeouts, circuit‑breaker opens—**Grafana**; **logs** carry exceptions, `stack_trace`, and WARN/ERROR patterns.
- **Saturation**: Thread pools, connection pools, GC, Kafka consumer lag—**Grafana**/exporters; **logs** may show “pool exhausted”, retries—treat as secondary evidence.

### RED (microservices)

- **Rate**, **Errors**, **Duration** per service—primarily **metrics**; logs support **Errors** via ERROR count and message clustering.

### USE (resources)

- **Utilization**, **Saturation**, **Errors** for Redis, Kafka, Elasticsearch, JVM—**Grafana** in this repo; correlate with log spikes when incidents occur.

**Takeaway for Kibana**: Dashboards should answer **“what broke, where, and in what pattern?”** and **“which traces/users/orders are affected?”**—not replace latency histograms that Micrometer already exposes.

---

## What to log and structure (ops + architect)

**Already strong in this project**: JSON logs, trace MDC, `service` / `environment` / `host`, Kafka pipeline into ES.

**High‑value additions for production‑style operations** (implementation is optional; fields should be consistent once introduced):

| Area | Suggested structured fields / practices | Why |
|------|----------------------------------------|-----|
| HTTP/gRPC | `http_method`, `http_path` or `grpc_service`, `grpc_method`, `status_code`, `outcome` (success/failure) | Filter errors by route; RED‑like views from logs when metrics gap |
| Duration | `duration_ms` on request/access logs | Link slow logs to traces without opening every span |
| Errors | Stable `error.type` / `error.code`; keep `stack_trace` | Group “same” failures in Kibana; reduce noise on `message` text |
| Kafka | `topic`, `partition`, `consumer_group` (on failures) | Debug `order-events-v1`, `adoption-events-v1`, `application-logs` issues |
| Business context | `pet_id`, `order_id`, `reservation_token` (hashed if sensitive) | Support “single entity” investigations |
| Security / abuse | `client_ip` (policy‑permitting), auth failure reason codes | Separate compliance‑oriented dashboard if needed |
| Config | Log effective `spring.application.name`, profile, important feature flags at startup | “Drift” and misconfiguration hunts |

**Sampling**: At high volume, consider sampling **INFO** access logs while keeping **ERROR** and **WARN** complete—architects trade cost vs fidelity; ops needs guaranteed error paths.

---

## Implemented vs future dashboards

**Shipped in `elk/kibana-dashboards/`** (imported by elk-init; bodies generated by `elk/scripts/render_kibana_dashboards.py` unless you hand-edit NDJSON):

| Dashboard file | Role |
|----------------|------|
| `log-overview.ndjson` | Executive-style log health: volume by time/service/level + ERROR/WARN **metric** counts for the **selected time range** |
| `microservices-health.ndjson`, `logs-by-service-level.ndjson` | Combined ERROR/WARN trends and quick service/level splits |
| `error-warning-monitoring.ndjson` | Incident-style: errors/warnings over time, errors by service, top error loggers |
| `trace-correlation.ndjson` | `traceId` volume over time + instructions (Discover + Zipkin + Grafana) |
| `log-operations.ndjson` | Logs/minute by service, logger pie, traced log count |
| `service-query-logs.ndjson` | Query-only (`service: "query-microservice"`) |
| `service-inventory-logs.ndjson` | Inventory-only (`service: "inventory-microservice"`) |

**Provisioning fix**: `elk/init/docker-init.sh` and `elk/provision-kibana.sh` treat Kibana imports as successful only when the API returns `"success": true`, and import NDJSON in sorted order so the data view is created before dependent dashboards.

---

## Proposed **additional** dashboards (future / optional)

The items below extend the model when you add fields, indices, or operational scope.

### Kafka & pipeline health (logs + ops)

- Logs from Kafka clients (if logged): producer send failures, consumer errors.
- **Connect / ES** (if you ingest connector logs into the same or another index): connector task failures, DLQ—today Connect logs may be Docker‑only; architects often add a small “pipeline” index for this in production.

### Security & configuration (optional, production)

- Auth failures, config fetch failures from Config Server, unexpected profile.
- Rate of ERROR from `org.springframework` packages after releases.

### Business / domain events (optional)

- Structured adoption/order events if logged at INFO with stable keys—useful for product analytics; keep PII policy in mind.

### Richer triage in Kibana

- **Recent errors table** with full `message` text works best in **Discover** (or after adding a `message.keyword` / length field)—see [docs/KIBANA_DASHBOARDS_PROPOSAL.md](docs/KIBANA_DASHBOARDS_PROPOSAL.md).
- Optional: breakdown by `http_path` / `status_code` once those exist as structured log fields.

---

## Representing metrics and values in Kibana (practical tips)

- **Use consistent units** in titles (“errors per minute”, “log events per second”).
- Prefer **time series** for trends, **top values** for dimensions (`logger_name`, `service`), **tables** for last N raw events with links to Discover.
- **Color**: Use one accent for ERROR/WARN; avoid rainbow categoricals on the same chart.
- **Dashboard variables**: `service`, `environment`, optional `traceId` text control for paste‑from‑Zipkin workflows.
- **Cross‑link**: Markdown panel with URLs to Grafana (Pet Shop Overview / Infrastructure) and Zipkin—this repo’s operators already use multiple UIs; one place that lists them reduces onboarding time.

---

## Relation to existing artifacts

| Artifact | Purpose |
|----------|---------|
| [docs/KIBANA_DASHBOARDS_PROPOSAL.md](docs/KIBANA_DASHBOARDS_PROPOSAL.md) | Panel-by-panel proposal, KQL examples, mapping to NDJSON files |
| [elk/kibana-dashboards/](elk/kibana-dashboards/) | Provisioned NDJSON dashboards |
| [elk/scripts/render_kibana_dashboards.py](elk/scripts/render_kibana_dashboards.py) | Regenerates dashboard NDJSON (except `application-logs-dataview.ndjson`) |
| Grafana provisioned JSON | Numeric SLO‑friendly metrics—pair with Kibana for investigations |

---

## References

- Elastic: [Dashboard guidelines](https://www.elastic.co/guide/en/integrations-developer/current/dashboard-guidelines.html)
- SRE metrics overview: [Four Golden Signals, RED, USE](https://logz.io/blog/evops-sre-metrics/) (and Google SRE book—latency, traffic, errors, saturation)
- Grafana: [RED method](https://grafana.com/blog/the-red-method-how-to-instrument-your-services/) (microservices request metrics—complements log‑based error analysis)

---

*Document version: aligned with Microservices OPS Demo stack (Query, Inventory, Kafka, Elasticsearch, Kibana, Zipkin, Prometheus/Grafana) as described in the project README.*
