#!/bin/bash
# Create application-logs Kafka topic if not exists.
# Usage: ./init-topics.sh [bootstrap_server]
set -e
BOOTSTRAP="${1:-localhost:9092}"
TOPIC="application-logs"

echo "Waiting for Kafka at $BOOTSTRAP..."
for i in $(seq 1 30); do
  if kafka-topics --bootstrap-server "$BOOTSTRAP" --list &>/dev/null; then
    echo "Kafka ready."
    break
  fi
  sleep 2
  [[ $i -eq 30 ]] && { echo "Timeout waiting for Kafka."; exit 1; }
done

if kafka-topics --bootstrap-server "$BOOTSTRAP" --list 2>/dev/null | grep -q "^${TOPIC}$"; then
  echo "Topic $TOPIC already exists."
else
  echo "Creating topic $TOPIC..."
  kafka-topics --bootstrap-server "$BOOTSTRAP" --create --topic "$TOPIC" --partitions 3 --replication-factor 1
  echo "Topic $TOPIC created."
fi
