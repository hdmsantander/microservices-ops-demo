# Kibana Dashboards

Dashboards are provisioned from this folder during **elk-init**. Place `.ndjson` files here; they are imported when the stack starts. Imports run in **sorted path order** so `application-logs-dataview.ndjson` is applied before dashboards that reference the **Application Logs** data view.

## Regenerating dashboard JSON

Dashboard bodies (except the data view) are generated from `elk/scripts/render_kibana_dashboards.py` so panel definitions stay consistent and references to the `application-logs` index pattern stay aligned.

```bash
# From repository root
python3 elk/scripts/render_kibana_dashboards.py
```

Then rebuild the init image if you use Docker: `docker compose build elk-init`.

## Bundled dashboards

| Dashboard | Panels (summary) |
|-----------|------------------|
| **Log Overview** | Area: log volume over time; Pie: by `service`; Bar: by `level`; Lens metrics: ERROR count, WARN count (both respect the **dashboard time picker**) |
| **Logs by Service & Level** | Pie: by `service`; Bar: by `level` |
| **Error & Warning Monitoring** | Line: errors over time; Line: warnings over time; Bar: errors by `service`; Bar: top error `logger_name` |
| **Microservices Log Health** | Area: errors; Area: warnings; Area: log volume by `service` |
| **Log Operations** | Line: logs per minute by `service`; Pie: top `logger_name`; Metric: count of docs with `traceId` |
| **Trace Correlation** | Area: logs with `traceId` over time; description with Zipkin / Discover / Grafana links |
| **Query — Application Logs** | Scoped to `service: "query-microservice"`: volume, ERROR/WARN trends, top loggers |
| **Inventory — Application Logs** | Scoped to `service: "inventory-microservice"`: same pattern |
| **Log Severity by Service** | ERROR/WARN over time split by `service`; INFO count; ERROR with `stack_trace`; totals |

## Data view

The `application-logs-dataview.ndjson` file creates the **Application Logs** data view (pattern: `application-logs*`, time field: `@timestamp`). It is imported first (alphabetically) so dashboards can reference it.

## Adding or changing dashboards

1. Prefer editing **`elk/scripts/render_kibana_dashboards.py`** and re-running it, or export from Kibana (**Stack Management** → **Saved Objects** → **Export**).
2. Save `.ndjson` files into this directory.
3. Rebuild and restart: `docker compose build elk-init && docker compose up -d`.

## Pairing with Grafana / Prometheus

All Lens panels here are **counts and histograms over log documents** in `application-logs*`, not Prometheus queries. When a panel shows a spike in ERROR volume or log throughput, open Grafana (**Application observability**, **Pet Shop Overview**, or **Infrastructure**) for the **same time range** and compare HTTP status rates, timers, business counters, JVM, Kafka lag, and Elasticsearch index stats. See [docs/ELK_OPERATIONS.md](../../docs/ELK_OPERATIONS.md) (section *Kibana panels vs metrics in the Prometheus scrape stream*).

## Trace correlation

Copy a `traceId` from Zipkin (http://localhost:9411), then in Kibana **Discover** (data view **Application Logs**) filter with:

```text
traceId: "YOUR_TRACE_ID"
```

See [docs/KIBANA_DASHBOARDS_PROPOSAL.md](../../docs/KIBANA_DASHBOARDS_PROPOSAL.md) and [docs/ELK_OPERATIONS.md](../../docs/ELK_OPERATIONS.md) for the full proposal and ops guidance.
