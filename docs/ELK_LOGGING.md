# Elasticsearch and Kibana for Log Analytics

## Overview

The stack includes **Elasticsearch**, **Kibana**, and **Kafka Connect** with an Elasticsearch Sink connector to ingest application logs from the `application-logs` Kafka topic. This enables centralized log search, dashboards, and trace correlation across Query and Inventory microservices.

## Architecture

```
[Query / Inventory]  --kafka-logging-->  [Kafka: application-logs]
                                                   |
                                                   v
                                    [Kafka Connect + ES Sink]
                                                   |
                                                   v
                                    [Elasticsearch] <--> [Kibana]
```

- **Log flow**: Microservices emit structured JSON logs to Kafka when the `kafka-logging` profile is active (enabled by default in Docker Compose).
- **Ingestion**: Kafka Connect Elasticsearch Sink connector consumes from `application-logs` and writes to Elasticsearch indices.
- **Analysis**: Kibana provides Discover for ad-hoc search, Dashboards for visualizations, and trace-based correlation via `traceId`/`spanId`.

## Ports and URLs

| Service | Port | URL |
|---------|------|-----|
| Elasticsearch | 9200 | http://localhost:9200 |
| Kibana | 5601 | http://localhost:5601 |
| Kafka Connect REST | 8083 | http://localhost:8083 |

## Startup

The full stack (`./start.sh` or `./start.sh full`) includes Elasticsearch, Kibana, and Kafka Connect. After startup:

1. **Topic**: `application-logs` is created automatically (or via `./elk/init-elk.sh`).
2. **Connector**: The Elasticsearch Sink connector is registered via `./elk/init-elk.sh` (runs automatically after ~45s, or manually if needed).
3. **Data view**: Kibana data view `application-logs*` is created by `./elk/provision-kibana.sh`.

### Manual initialization

If ELK init did not run during startup:

```bash
./elk/init-elk.sh          # Create topic, register connector
./elk/provision-kibana.sh  # Create Kibana data view (run after first logs are ingested)
```

## Log schema

Each log document in Elasticsearch has:

| Field | Type | Description |
|-------|------|--------------|
| `@timestamp` | date | Log timestamp (ISO8601) |
| `level` | keyword | INFO, WARN, ERROR |
| `logger` | keyword | Logger name |
| `message` | text | Log message |
| `traceId` | keyword | Micrometer trace ID (correlation) |
| `spanId` | keyword | Micrometer span ID |
| `service` | keyword | query-microservice or inventory-microservice |

## Kibana dashboards

### Basic: Log discovery

1. Open **Discover**: http://localhost:5601/app/discover
2. Select data view **Application Logs** (application-logs*)
3. Use KQL filters:
   - `service: "query-microservice"`
   - `level: "ERROR"`
   - `traceId: "YOUR_TRACE_ID"` (from Zipkin)

### Advanced: Create dashboards

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

## Integration with observability stack

- **Prometheus + Grafana**: Metrics (counters, gauges, latency)
- **Zipkin**: Distributed traces
- **Elasticsearch + Kibana**: Logs with trace correlation (`traceId` links logs to Zipkin traces)
