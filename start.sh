#!/bin/bash

set -e

COMPOSE_MINIMAL="docker-compose-minimal.yml"
COMPOSE_FULL="docker-compose.yml"

start_minimal() {
    echo "Starting minimal infrastructure (Kafka, Zipkin, Prometheus)..."
    docker compose -f "$COMPOSE_MINIMAL" up
}

start_full() {
    echo "Packaging microservices..."
    (cd query-microservice && ./mvnw -q package -DskipTests) && \
    (cd inventory-microservice && ./mvnw -q package -DskipTests) && \
    echo "Success building sources! Starting full environment..." && \
    docker compose -f "$COMPOSE_FULL" build && \
    docker compose -f "$COMPOSE_FULL" up
}

case "${1:-}" in
    minimal|--minimal|-m)
        start_minimal
    ;;
    "")
        start_full
    ;;
    *)
        echo "Usage: $0 [minimal|--minimal|-m]"
        echo "  (no args)  Build and start full stack (microservices + Kafka + Zipkin + Prometheus)"
        echo "  minimal   Start infrastructure only (Kafka + Zipkin + Prometheus), run microservices locally"
        exit 1
    ;;
esac