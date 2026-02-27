#!/bin/bash

set -e

COMPOSE_MINIMAL="docker-compose-minimal.yml"
COMPOSE_FULL="docker-compose.yml"
SKIP_TESTS=""
MODE=""

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --skip-tests)
                SKIP_TESTS="-DskipTests"
                shift
            ;;
            minimal|--minimal|-m)
                MODE="minimal"
                shift
            ;;
            full|--full|-f|"")
                MODE="full"
                shift
            ;;
            *)
                echo "Unknown option: $1"
                exit 1
            ;;
        esac
    done
    [[ -z "$MODE" ]] && MODE="full"
}

start_minimal() {
    echo "Starting minimal infrastructure (Kafka, Zipkin, Prometheus)..."
    docker compose -f "$COMPOSE_MINIMAL" up
}

start_full() {
    echo "Packaging microservices..."
    (cd query-microservice && ./mvnw -q package ${SKIP_TESTS}) && \
    (cd inventory-microservice && ./mvnw -q package ${SKIP_TESTS}) && \
    echo "Success building sources! Starting full environment..." && \
    docker compose -f "$COMPOSE_FULL" build && \
    docker compose -f "$COMPOSE_FULL" up
}

parse_args "$@"

case "$MODE" in
    minimal)
        start_minimal
    ;;
    full)
        start_full
    ;;
    *)
        echo "Usage: $0 [minimal|--minimal|-m] [full|--full|-f] [--skip-tests]"
        echo "  (no args)  Build and start full stack, run tests"
        echo "  minimal    Start infrastructure only (Kafka + Zipkin + Prometheus)"
        echo "  full       Build and start full stack (same as no args)"
        echo "  --skip-tests  Skip tests when packaging microservices"
        exit 1
    ;;
esac