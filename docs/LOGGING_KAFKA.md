# Log Distribution to Kafka

## Overview

The Query and Inventory microservices support sending application logs to Apache Kafka for centralized log aggregation. This follows microservices best practices for distributed logging.

## Features

- **Structured JSON logs**: Console and Kafka use Logstash layout for proper JSON encoding. Kafka messages include `@timestamp`, `level`, `logger_name`, `message`, `thread_name`, `traceId`, `spanId`, `parentSpanId`, `service`, `environment`, `host`, and `stack_trace` (for errors).
- **Trace correlation**: `traceId` and `spanId` from Micrometer Tracing (Brave) are automatically populated in MDC and included in every log for request correlation across services. Copy a `traceId` from Zipkin to filter all related logs in Kibana.
- **Service and environment**: Each log includes `service` (e.g. query-microservice), `environment` (e.g. development), and `host` for multi-instance and multi-environment filtering.
- **Exception stack traces**: Errors include full `stack_trace` for debugging.
- **Async delivery**: Kafka appender uses `AsynchronousDeliveryStrategy` plus `AsyncAppender` to avoid blocking application threads.
- **Host keying**: Logs are keyed by host for ordered partitioning when desired.

## Enabling Kafka Logging

Kafka logging is **enabled by default** when running the full stack via Docker Compose (`SPRING_PROFILES_ACTIVE=development,kafka-logging`). For local runs, enable by including the `kafka-logging` profile:

```bash
# Environment variable
SPRING_PROFILES_ACTIVE=development,kafka-logging

# Or in application.yml
spring:
  profiles:
    include: kafka-logging
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `logging.kafka.bootstrap-servers` | `localhost:9092` | Kafka broker(s) |
| `logging.kafka.topic` | `application-logs` | Target topic |

The topic `application-logs` should exist. Kafka (landoop/fast-data-dev) will auto-create it when the first message is produced, or create it explicitly:

```bash
kafka-topics --bootstrap-server localhost:9092 --create --topic application-logs --partitions 3 --replication-factor 1
```

## Consumer / Aggregation

Logs are ingested into **Elasticsearch** via Kafka Connect for Kibana dashboards. See [ELK_LOGGING.md](ELK_LOGGING.md). To consume directly:

```bash
kafka-console-consumer --bootstrap-server localhost:9092 --topic application-logs
```

Example log message (enriched for traceability):

```json
{
  "@timestamp": "2025-03-05T00:00:00.000Z",
  "level": "INFO",
  "logger_name": "m.h.o.query.service.PetService",
  "message": "Adopted pet 1",
  "thread_name": "http-nio-8086-exec-1",
  "traceId": "abc123def456",
  "spanId": "789xyz",
  "service": "query-microservice",
  "environment": "development",
  "host": "hostname"
}
```

For errors, `stack_trace` is also included. Use `traceId` in Kibana to correlate logs across Query and Inventory with the same request.

## Tests

The `kafka-logging` profile is **not** active during tests. Tests use the default `test` profile and do not send logs to Kafka.
