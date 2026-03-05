# Elasticsearch and Kibana for Log Analytics

## Overview

The stack includes **Elasticsearch**, **Kibana**, and **Kafka Connect** with an Elasticsearch Sink connector to ingest application logs from the `application-logs` Kafka topic. This enables centralized log search, dashboards, and trace correlation across Query and Inventory microservices.

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full Log Flow diagram.

```
[Query / Inventory]  --kafka-logging-->  [Kafka: application-logs]
       (enriched: traceId, spanId,           |
        service, env, host, stack_trace)     v
                                    [Kafka Connect + ES Sink]
                                                   |
                                                   v
                                    [Elasticsearch] <--> [Kibana]
```

- **Log flow**: Microservices emit enriched JSON logs to Kafka when the `kafka-logging` profile is active (enabled by default in Docker Compose).
- **Enrichment**: LogstashLayout adds traceId, spanId, service, environment, host; errors include stack_trace. Correlate with Zipkin via `traceId`.
- **Ingestion**: Kafka Connect Elasticsearch Sink connector consumes from `application-logs` and writes to Elasticsearch indices.
- **Analysis**: Kibana provides Discover for ad-hoc search, Dashboards for visualizations, and trace-based correlation via `traceId`/`spanId`.

## Ports and URLs

| Service | Port | URL |
|---------|------|-----|
| Elasticsearch | 9200 | http://localhost:9200 |
| Kibana | 5601 | http://localhost:5601 |
| Kafka Connect REST | 8084 | http://localhost:8084 (8084 avoids conflict with landoop Connect on 8083) |

## Startup

The full stack (`./start.sh` or `./start.sh full`) includes Elasticsearch, Kibana, Kafka Connect, and the **elk-init** service. Startup is single-threaded and blocking:

1. Infrastructure (Kafka, Elasticsearch, Kibana, Kafka Connect) starts and becomes healthy.
2. **elk-init** runs: creates `application-logs` topic, registers the Elasticsearch Sink connector, provisions the Kibana data view, then exits.
3. Query and Inventory microservices start only after elk-init completes successfully.
4. `docker compose up` blocks on the main thread, streaming logs from all services.

The elk-init logic is Dockerized (`elk/init/`); no background scripts or manual init are required.

### Manual initialization (non-Docker or recovery)

If running without Docker or if elk-init failed:

```bash
./elk/init-elk.sh          # Create topic, register connector (requires docker exec for topic)
./elk/provision-kibana.sh  # Create Kibana data view
```

## Log schema

Each log document in Elasticsearch has (enriched for traceability):

| Field | Type | Description |
|-------|------|--------------|
| `@timestamp` | date | Log timestamp (ISO8601) |
| `level` | keyword | INFO, WARN, ERROR |
| `logger_name` | keyword | Logger name |
| `message` | text | Log message |
| `thread_name` | keyword | Thread name |
| `traceId` | keyword | Micrometer trace ID – correlate with Zipkin |
| `spanId` | keyword | Micrometer span ID |
| `parentSpanId` | keyword | Parent span (when available) |
| `service` | keyword | query-microservice or inventory-microservice |
| `environment` | keyword | Spring profile (e.g. development) |
| `host` | keyword | Hostname |
| `stack_trace` | text | Exception stack (errors only) |

## Kibana dashboards

### Proposed dashboards

See [docs/KIBANA_DASHBOARDS_PROPOSAL.md](KIBANA_DASHBOARDS_PROPOSAL.md) for detailed proposals:

- **Log Overview** – volume by service/level, errors/warnings
- **Error & Warning Monitoring** – anomalies and top error loggers
- **Trace Correlation** – correlate logs across services via `traceId`
- **Log Processing** – throughput, logger distribution

### Automated provisioning

Place exported `.ndjson` files in `elk/init/dashboards/` and rebuild elk-init. They will be imported on stack startup.

### Basic: Log discovery

1. Open **Discover**: http://localhost:5601/app/discover
2. Select data view **Application Logs** (application-logs*)
3. Use KQL filters:
   - `service: "query-microservice"`
   - `level: "ERROR"`
   - `traceId: "YOUR_TRACE_ID"` (from Zipkin)

### Manual creation

See [elk/kibana-dashboards/README.md](../elk/kibana-dashboards/README.md) for step-by-step instructions to create:

- **Logs by Service** (pie chart)
- **Logs by Level** (bar chart)
- **Error rate over time** (line chart)
- **Trace correlation** (filter by traceId)

## Configuration

| Component | Config | Default |
|-----------|--------|---------|
| Kafka topic | `application-logs` | 3 partitions, replication 1 |
| Connector | `elasticsearch-application-logs` | Kafka Connect REST API |
| ES index | Created by connector | `application-logs` |
| Data view | Kibana | `application-logs*`, time field `@timestamp` |

## Troubleshooting

| Issue | Cause | Fix |
|-------|-------|-----|
| No logs in Kibana | Connector not running or topic empty | Run `./elk/init-elk.sh`; ensure `kafka-logging` profile is active |
| Connector failed | Elasticsearch not ready | Check Elasticsearch health: `curl http://localhost:9200/_cluster/health` |
| Data view missing | Provision not run | Run `./elk/provision-kibana.sh` |
| No documents in Discover | No logs produced yet | Trigger app activity (e.g. call `/v1/pets`); wait for connector to ingest |
| "legacy OpenSSL providers enabled" in Kibana logs | Known Kibana/Node.js behavior | Safe to ignore; Elastic is addressing in future releases. See [elastic.co/guide](https://www.elastic.co/guide/en/kibana/8.17/production.html#openssl-legacy-provider) |
| "Failed to bind to 0.0.0.0:8083" / "Address already in use" | Kafka Connect default port 8083 collides with landoop or another service | We use `CONNECT_LISTENERS=http://0.0.0.0:8084` so Connect binds to 8084. Ensure 8084 is in `start.sh` port check |
| Kibana healthcheck fails / never green | Kibana 8 uses `level` (e.g. "available") not `state` ("green") in `/api/status` | Healthcheck accepts both. Ensure `SERVER_HOST=0.0.0.0`; `start_period` 90s for migrations |

## Integration with observability stack

- **Prometheus + Grafana**: Metrics (counters, gauges, latency)
- **Zipkin**: Distributed traces
- **Elasticsearch + Kibana**: Logs with trace correlation (`traceId` links logs to Zipkin traces)
