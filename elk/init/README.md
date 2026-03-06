# ELK Init (Dockerized)

This directory builds the **elk-init** container that runs as part of `docker compose up`. It:

1. Creates the `application-logs` Kafka topic
2. Registers the Kafka Connect Elasticsearch Sink connector
3. Imports Kibana dashboards from `elk/kibana-dashboards/` (data view + pre-configured dashboards)
4. Creates the Kibana data view via API if not already present (fallback)

The container uses `network_mode: host` to reach Kafka (9092), Connect (8084), and Kibana (5601). It runs once and exits; Query and Inventory depend on it completing successfully before they start.

**Build context**: `./elk` (so `kibana-dashboards/` can be copied). **Base image**: `confluentinc/cp-kafka` (provides `kafka-topics`); curl is added for REST API calls.
