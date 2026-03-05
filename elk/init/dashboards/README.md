# Kibana Dashboard Provisioning

Place exported Kibana saved objects (`.ndjson`) in this directory to have them imported automatically during ELK initialization.

## Bundled dashboards

All dashboards are minimal shells (empty panels). Open in Kibana and add visualizations via **Lens** using the `application-logs*` data view.

| Dashboard | File | Suggested panels |
|-----------|------|------------------|
| **Log Overview** | log-overview.ndjson | Log volume by service (Pie), by level (Bar), over time (Area), Error/WARN stats |
| **Logs by Service & Level** | logs-by-service-level.ndjson | Pie: Count by `service`; Bar: X `level` Y Count; Area: X `@timestamp` Y Count |
| **Error & Warning Monitoring** | error-warning-monitoring.ndjson | Line: Errors over time (filter `level: "ERROR"`); Line: Warnings; Bar: Errors by service; Table: Top error `logger_name` |
| **Trace Correlation** | trace-correlation.ndjson | Markdown: instructions; Discover embed: filter `traceId` from Zipkin |
| **Log Operations** | log-operations.ndjson | Line: Logs/min by service (1m bucket); Pie: Top 10 `logger_name`; Stat: Count with `traceId` |
| **Microservices Log Health** | microservices-health.ndjson | Stat: Error count; Stat: WARN count; Pie: by service; Area: volume over time |

## How to add more dashboards

1. Create dashboards in Kibana (http://localhost:5601) using **Stack Management** → **Saved Objects** → **Import** for reference, or create manually.
2. Export your dashboards: **Stack Management** → **Saved Objects** → select objects → **Export**.
3. Save the downloaded `.ndjson` file(s) into this directory.
4. Rebuild the elk-init image and restart: `docker compose build elk-init && docker compose up -d`.

The elk-init script will import any `*.ndjson` files from this directory after provisioning the data view.

## Reference

See [docs/KIBANA_DASHBOARDS_PROPOSAL.md](../../docs/KIBANA_DASHBOARDS_PROPOSAL.md) for proposed dashboard designs (Log Overview, Error Monitoring, Trace Correlation, Log Processing) based on the log schema.
