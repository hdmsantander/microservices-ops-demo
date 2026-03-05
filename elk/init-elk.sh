#!/bin/bash
# Initialize ELK: create application-logs topic and register Kafka Connect ES Sink.
# Run after the stack is up (e.g. docker compose up -d).
# Usage: ./init-elk.sh
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
ES_URL="${ES_URL:-http://localhost:9200}"

echo "=== ELK initialization ==="

# Create topic via Kafka container
KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka-demo}"
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${KAFKA_CONTAINER}$"; then
  echo "Creating application-logs topic via $KAFKA_CONTAINER..."
  docker exec "$KAFKA_CONTAINER" kafka-topics --bootstrap-server localhost:9092 --create --if-not-exists --topic application-logs --partitions 3 --replication-factor 1 2>/dev/null || true
  echo "Topic ready."
else
  echo "WARN: Kafka container $KAFKA_CONTAINER not running. Create topic manually:"
  echo "  kafka-topics --bootstrap-server $BOOTSTRAP --create --topic application-logs --partitions 3 --replication-factor 1"
fi

# Register connector
echo ""
"$SCRIPT_DIR/init-connector.sh" "$CONNECT_URL" "$ES_URL" "application-logs"
echo ""
echo "ELK initialization complete. Open Kibana at http://localhost:5601"
