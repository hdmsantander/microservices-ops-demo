# Kibana Dashboards

Dashboards can be created manually in Kibana or provisioned via ndjson import during elk-init.

## Automated Provisioning

Place `.ndjson` files in `elk/init/dashboards/` and rebuild elk-init. They will be imported when the stack starts. See [elk/init/dashboards/README.md](init/dashboards/README.md).

## Manual Creation

### Basic: Log Discovery

1. Open **Discover** (http://localhost:5601/app/discover)
2. Select data view **Application Logs** (application-logs*)
3. Use filters: `service: "query-microservice"` or `level: "ERROR"`
4. Save as **Log Discovery**

### Bundled dashboards (elk/init/dashboards/)

The following dashboards are auto-imported at startup. Add panels in Kibana using the suggestions:

| Dashboard | Panels to add |
|-----------|---------------|
| **Log Overview** | Pie: Count by `service`; Bar: X `level` Y Count; Area: X `@timestamp` Y Count |
| **Logs by Service & Level** | Same as above, focused on service/level breakdown |
| **Error & Warning Monitoring** | Line: Errors over time; Bar: Errors by service; Table: Top error loggers |
| **Trace Correlation** | Discover with `traceId` from Zipkin (http://localhost:9411) |
| **Log Operations** | Line: Logs/min by service; Pie: logger distribution |
| **Microservices Log Health** | Stats for Errors/WARNs; Pie by service; Area over time |

### Panel config reference

| Panel | Type | Configuration |
|-------|------|----------------|
| Logs by Service | Lens → Pie | Count by `service` (keyword) |
| Logs by Level | Lens → Bar | X: `level`, Y: Count |
| Error Rate Over Time | Lens → Area | X: `@timestamp`, Y: Count, Filter: `level: "ERROR"` |
| Trace Correlation | Discover | Filter: `traceId: "YOUR_TRACE_ID"` |

See [docs/KIBANA_DASHBOARDS_PROPOSAL.md](../../docs/KIBANA_DASHBOARDS_PROPOSAL.md) for full proposal.
