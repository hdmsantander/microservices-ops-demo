# Kibana Dashboards Proposal for Log Monitoring and Processing

## Overview

This document proposes Kibana dashboards tailored to the current log setup: structured JSON logs from Query and Inventory microservices with fields `@timestamp`, `level`, `logger`, `message`, `traceId`, `spanId`, and `service`. Logs are ingested from the `application-logs` Kafka topic via Kafka Connect Elasticsearch Sink.

## Log Schema Reference

| Field | Type | Description |
|-------|------|-------------|
| `@timestamp` | date | Log timestamp (ISO8601) |
| `level` | keyword | INFO, WARN, ERROR |
| `logger_name` | keyword | Logger name |
| `message` | text | Log message |
| `thread_name` | keyword | Thread name |
| `traceId` | keyword | Micrometer trace ID (correlate with Zipkin) |
| `spanId` | keyword | Micrometer span ID |
| `parentSpanId` | keyword | Parent span (when available) |
| `service` | keyword | query-microservice, inventory-microservice |
| `environment` | keyword | Spring profile |
| `host` | keyword | Hostname |
| `stack_trace` | text | Exception stack (errors only) |

---

## Proposed Dashboard 1: Log Overview

**Purpose**: High-level log health and volume across the ecosystem.

| Panel                 | Type      | Configuration |
|-----------------------|-----------|----------------|
| Log volume by service | Pie chart | Aggregation: Count, Split by: `service` |
| Log volume by level   | Bar chart | X: `level`, Y: Count |
| Log volume over time  | Area chart| X: `@timestamp` (Date Histogram), Y: Count |
| Error count (last 24h)| Stat      | Filter: `level: "ERROR"`, Count |
| WARN count (last 24h) | Stat     | Filter: `level: "WARN"`, Count |

---

## Proposed Dashboard 2: Error & Warning Monitoring

**Purpose**: Focus on anomalies and operational issues.

| Panel                 | Type      | Configuration |
|-----------------------|-----------|----------------|
| Errors over time      | Line chart| Filter: `level: "ERROR"`, X: `@timestamp`, Y: Count |
| Warnings over time    | Line chart| Filter: `level: "WARN"`, X: `@timestamp`, Y: Count |
| Errors by service     | Bar chart | Filter: `level: "ERROR"`, Group by: `service` |
| Top error loggers     | Data table| Filter: `level: "ERROR"`, Group by: `logger_name`, Count |
| Recent errors (table) | Data table| Filter: `level: "ERROR"`, Columns: `@timestamp`, `service`, `logger_name`, `message` |

---

## Proposed Dashboard 3: Trace Correlation

**Purpose**: Correlate logs across services using Zipkin trace IDs.

| Panel                 | Type      | Configuration |
|-----------------------|-----------|----------------|
| Logs by traceId       | Discover embed | Filter: `traceId: *` (variable), show recent logs |
| Trace flow (example)  | Markdown  | Instructions: paste traceId from Zipkin, filter in Discover |

**Usage**: Copy a `traceId` from Zipkin (http://localhost:9411), then in Kibana Discover filter by `traceId: "YOUR_TRACE_ID"` to see all related logs from both Query and Inventory services.

---

## Proposed Dashboard 4: Log Processing & Operations

**Purpose**: Operational view of logging throughput and processing.

| Panel                 | Type      | Configuration |
|-----------------------|-----------|----------------|
| Logs per minute by service | Line chart | X: `@timestamp` (1m interval), Y: Count, Split: `service` |
| Logger distribution   | Pie chart | Top 10 by `logger_name` |
| Message length distribution | Histogram | Scripted/metric on `message.keyword` length (if enabled) |
| Requests with traces  | Stat      | Filter: `traceId: *` and `traceId != ""`, Count |

---

## Provisioning

### Automated Import (elk-init)

The elk-init container imports ndjson files from `elk/kibana-dashboards/` via Kibana's Saved Objects API. Files are imported in alphabetical order so `application-logs-dataview.ndjson` creates the data view before dashboards reference it.

```bash
# Manual import (when not using Docker)
curl -sS -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file=@elk/kibana-dashboards/application-logs-dataview.ndjson
curl -sS -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file=@elk/kibana-dashboards/log-overview.ndjson
```

### Manual Creation

1. Open **Kibana** → **Dashboards** → **Create dashboard**.
2. Add visualizations using **Lens** or **Aggregation based**.
3. For each panel, select data view **Application Logs** (application-logs*).
4. Apply KQL filters as needed: `service: "query-microservice"`, `level: "ERROR"`, etc.

### KQL Examples

```
service: "query-microservice"
level: "ERROR"
level: "WARN" or level: "ERROR"
traceId: "abc123def456"
logger_name: *PetService*
message: *timeout*
environment: "development"
host: "hostname"
```

---

## Data View

The data view **Application Logs** (pattern: `application-logs*`) is provisioned by elk-init. Ensure `@timestamp` is set as the time field for correct time filtering.
