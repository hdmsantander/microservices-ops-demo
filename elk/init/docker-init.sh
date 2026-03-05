#!/bin/bash
# Dockerized ELK init: create topic, register connector, provision Kibana.
# Runs with network_mode: host to reach Kafka (9092), Connect (8083), Kibana (5601).
set -e

KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
KIBANA_URL="${KIBANA_URL:-http://localhost:5601}"
ES_URL="${ES_URL:-http://localhost:9200}"
TOPIC="${TOPIC:-application-logs}"

# Prefer curl, fallback to wget
http() {
  if command -v curl &>/dev/null; then
    curl -sSf "$@"
  else
    wget -q -O- --no-check-certificate "$@"
  fi
}

echo "=== ELK initialization ==="

# 1. Create Kafka topic
echo "Creating topic $TOPIC..."
kafka-topics --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists \
  --topic "$TOPIC" --partitions 3 --replication-factor 1 2>/dev/null || true
echo "Topic ready."

# 2. Wait for Kafka Connect and register connector
echo ""
echo "Waiting for Kafka Connect at $CONNECT_URL..."
for i in $(seq 1 60); do
  if http "$CONNECT_URL/connectors" &>/dev/null; then
    echo "Kafka Connect ready."
    break
  fi
  sleep 2
  [[ $i -eq 60 ]] && { echo "Timeout waiting for Kafka Connect."; exit 1; }
done

echo "Checking for existing connector..."
if http "$CONNECT_URL/connectors/elasticsearch-application-logs" &>/dev/null; then
  echo "Connector elasticsearch-application-logs already exists."
else
  echo "Creating Elasticsearch Sink connector..."
  CONNECTOR_JSON="{\"name\":\"elasticsearch-application-logs\",\"config\":{\"connector.class\":\"io.confluent.connect.elasticsearch.ElasticsearchSinkConnector\",\"connection.url\":\"$ES_URL\",\"tasks.max\":\"1\",\"topics\":\"$TOPIC\",\"key.ignore\":\"true\",\"schema.ignore\":\"true\",\"behavior.on.null.values\":\"ignore\"}}"
  if command -v curl &>/dev/null; then
    curl -sS -X POST -H "Content-Type: application/json" --data "$CONNECTOR_JSON" "$CONNECT_URL/connectors"
  else
    wget -q -O- --post-data="$CONNECTOR_JSON" --header="Content-Type: application/json" "$CONNECT_URL/connectors"
  fi
  echo ""
  echo "Connector registered."
fi

# 3. Wait for Kibana and provision data view
echo ""
echo "Waiting for Kibana at $KIBANA_URL..."
for i in $(seq 1 60); do
  if http "$KIBANA_URL/api/status" &>/dev/null; then
    echo "Kibana ready."
    break
  fi
  sleep 2
  [[ $i -eq 60 ]] && { echo "Timeout waiting for Kibana."; exit 1; }
done

echo "Creating data view application-logs*..."
EXISTING=$(http "$KIBANA_URL/api/data_views" 2>/dev/null | grep -o '"title":"application-logs\*"' || true)
if [[ -n "$EXISTING" ]]; then
  echo "Data view already exists."
else
  if command -v curl &>/dev/null; then
    curl -sS -X POST -H "kbn-xsrf: true" -H "Content-Type: application/json" \
      --data '{"data_view":{"title":"application-logs*","timeFieldName":"@timestamp","name":"Application Logs"}}' \
      "$KIBANA_URL/api/data_views/data_view" | grep -q '"id"' && echo "Data view created." || echo "Data view creation skipped."
  fi
fi

# 4. Import Kibana dashboards (optional ndjson files)
if [[ -d /elk-init/dashboards ]] && compgen -G /elk-init/dashboards/*.ndjson >/dev/null 2>&1; then
  echo ""
  echo "Importing Kibana dashboards..."
  for f in /elk-init/dashboards/*.ndjson; do
    [[ -f "$f" ]] || continue
    if command -v curl &>/dev/null; then
      curl -sS -X POST "$KIBANA_URL/api/saved_objects/_import?overwrite=true" \
        -H "kbn-xsrf: true" \
        --form "file=@$f" | grep -q '"success"' && echo "  Imported $(basename "$f")" || echo "  Import skipped for $(basename "$f")"
    fi
  done
fi

echo ""
echo "ELK initialization complete. Kibana: $KIBANA_URL/app/discover"
