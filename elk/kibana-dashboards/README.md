# Kibana Dashboards

Dashboards are provisioned from this folder during elk-init. Place `.ndjson` files here; they are imported when the stack starts (data view first, then dashboards).

## Bundled Dashboards

All dashboards include pre-configured Lens panels that display metrics and traces from the Query and Inventory microservices.

| Dashboard | Pre-configured panels |
|-----------|------------------------|
| **Log Overview** | Area: Log volume over time; Pie: Logs by service; Bar: Logs by level |
| **Logs by Service & Level** | Pie: Logs by service; Bar: Logs by level |
| **Error & Warning Monitoring** | Line: Errors over time; Line: Warnings over time |
| **Microservices Log Health** | Area: Errors over time; Area: Warnings over time; Area: Log volume over time by service |
| **Log Operations** | Line: Logs per minute by service |
| **Trace Correlation** | Area: Logs with traceId over time (use Discover to filter by traceId from Zipkin) |

## Data View

The `application-logs-dataview.ndjson` file creates the **Application Logs** data view (pattern: `application-logs*`, time field: `@timestamp`). It is imported first so dashboards can reference it.

## Adding Dashboards

1. Create dashboards in Kibana (http://localhost:5601) using **Stack Management** → **Saved Objects** → **Import**, or create manually.
2. Export: **Stack Management** → **Saved Objects** → select objects → **Export**.
3. Save `.ndjson` files into this directory.
4. Rebuild and restart: `docker compose build elk-init && docker compose up -d`.

## Trace Correlation

Copy a `traceId` from Zipkin (http://localhost:9411), then in Kibana **Discover** filter by `traceId: "YOUR_TRACE_ID"` to see logs across Query and Inventory services.

See [docs/KIBANA_DASHBOARDS_PROPOSAL.md](../../docs/KIBANA_DASHBOARDS_PROPOSAL.md) for the full proposal.
