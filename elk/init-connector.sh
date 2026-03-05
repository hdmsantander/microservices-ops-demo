#!/bin/bash
# Register Kafka Connect Elasticsearch Sink for application-logs topic.
# Run after Kafka Connect and Elasticsearch are up.
# Usage: ./init-connector.sh [connect_url] [es_url]
set -e
CONNECT_URL="${1:-http://localhost:8084}"
ES_URL="${2:-http://localhost:9200}"
TOPIC="${3:-application-logs}"

echo "Waiting for Kafka Connect at $CONNECT_URL..."
for i in $(seq 1 30); do
  if curl -sSf "$CONNECT_URL/connectors" &>/dev/null; then
    echo "Kafka Connect ready."
    break
  fi
  sleep 2
  [[ $i -eq 30 ]] && { echo "Timeout waiting for Kafka Connect."; exit 1; }
done

echo "Checking for existing connector..."
if curl -sSf "$CONNECT_URL/connectors/elasticsearch-application-logs" &>/dev/null; then
  echo "Connector elasticsearch-application-logs already exists."
  exit 0
fi

echo "Creating Elasticsearch Sink connector..."
curl -sS -X POST -H "Content-Type: application/json" \
  --data "{
    \"name\": \"elasticsearch-application-logs\",
    \"config\": {
      \"connector.class\": \"io.confluent.connect.elasticsearch.ElasticsearchSinkConnector\",
      \"connection.url\": \"$ES_URL\",
      \"tasks.max\": \"1\",
      \"topics\": \"$TOPIC\",
      \"key.ignore\": \"true\",
      \"schema.ignore\": \"true\",
      \"behavior.on.null.values\": \"ignore\"
    }
  }" \
  "$CONNECT_URL/connectors"

echo ""
echo "Connector registered."
