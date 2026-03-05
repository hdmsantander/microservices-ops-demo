#!/usr/bin/env bash
# Wrapper for Confluent Kafka Connect that forces REST API to bind to 8084.
# CONNECT_LISTENERS/CONNECT_REST_PORT are ignored by some cp-kafka-connect versions;
# we patch the generated properties file to avoid port 8083 (used by landoop).
set -e

. /etc/confluent/docker/bash-config
. /etc/confluent/docker/mesos-setup.sh 2>/dev/null || true
. /etc/confluent/docker/apply-mesos-overrides 2>/dev/null || true

echo "===> User"
id

echo "===> Configuring ..."
/etc/confluent/docker/configure

# Force REST API to bind to 8084 (avoids collision with landoop Connect on 8083)
for candidate in /etc/kafka-connect/kafka-connect.properties /etc/kafka/connect-distributed.properties; do
  [ -f "$candidate" ] && PROP_FILE="$candidate" && break
done
PROP_FILE="${PROP_FILE:-/etc/kafka-connect/kafka-connect.properties}"
if [ -f "$PROP_FILE" ]; then
  if grep -q '^listeners=' "$PROP_FILE" 2>/dev/null; then
    sed -i 's|^listeners=.*|listeners=http://0.0.0.0:8084|' "$PROP_FILE"
  else
    echo 'listeners=http://0.0.0.0:8084' >> "$PROP_FILE"
  fi
  if grep -q '^rest\.advertised\.listener=' "$PROP_FILE" 2>/dev/null; then
    sed -i 's|^rest\.advertised\.listener=.*|rest.advertised.listener=http://localhost:8084|' "$PROP_FILE"
  else
    echo 'rest.advertised.listener=http://localhost:8084' >> "$PROP_FILE"
  fi
  echo "===> Patched listeners to 8084"
fi

echo "===> Running preflight checks ... "
/etc/confluent/docker/ensure

echo "===> Launching ... "
exec /etc/confluent/docker/launch
