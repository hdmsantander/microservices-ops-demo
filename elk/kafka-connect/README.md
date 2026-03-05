# Kafka Connect (Elasticsearch Sink)

Custom Kafka Connect image with the **Confluent Elasticsearch Sink Connector** pre-installed. Sinks data from Kafka topics (e.g. `application-logs`) into Elasticsearch.

## Base Image

- **Image**: `confluentinc/cp-kafka-connect:7.6.0`
- **Connector**: [confluentinc/kafka-connect-elasticsearch](https://www.confluent.io/hub/confluentinc/kafka-connect-elasticsearch) (version 14.1.2)

## Build

From project root:

```bash
docker compose -f docker-compose.yml build kafka-connect
```

Or from this directory:

```bash
docker build -t microservices-ops-demo-kafka-connect .
```

## Connector Configuration

The `elk-init` container registers the connector automatically. Manual registration:

```bash
curl -X POST http://localhost:8084/connectors -H "Content-Type: application/json" -d '{
  "name": "elasticsearch-application-logs",
  "config": {
    "connector.class": "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector",
    "connection.url": "http://localhost:9200",
    "tasks.max": "1",
    "topics": "application-logs",
    "key.ignore": "true",
    "schema.ignore": "true",
    "behavior.on.null.values": "ignore"
  }
}'
```

## Updating the Connector Version

Check available versions on [Confluent Hub](https://www.confluent.io/hub/confluentinc/kafka-connect-elasticsearch). Update the version in the Dockerfile:

```dockerfile
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-elasticsearch:<version>
```

Rebuild the image after changing the version.
