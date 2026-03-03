#!/bin/bash

set -e

COMPOSE_MINIMAL="docker-compose-minimal.yml"
COMPOSE_FULL="docker-compose.yml"
SKIP_TESTS=""
MODE="full"
TESTS_ONLY=""

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --skip-tests)
                SKIP_TESTS="-DskipTests"
                shift
                ;;
            --tests-only)
                TESTS_ONLY="1"
                shift
                ;;
            minimal|--minimal|-m)
                MODE="minimal"
                shift
                ;;
            full|--full|-f)
                MODE="full"
                shift
                ;;
            *)
                echo "Unknown option: $1"
                echo "Usage: $0 [minimal|--minimal|-m] [full|--full|-f] [--skip-tests] [--tests-only]"
                echo "  (no args)  Build and start full stack, run tests"
                echo "  minimal   Start infrastructure only (Kafka + Zipkin + Prometheus)"
                echo "  full      Build and start full stack (same as no args)"
                echo "  --skip-tests  Skip tests when packaging microservices (full stack only)"
                echo "  --tests-only  Run tests only for microservices (no Docker)"
                exit 1
                ;;
        esac
    done
}

start_minimal() {
    echo "Starting minimal infrastructure (Kafka, Zipkin, Prometheus)..."
    docker compose -f "$COMPOSE_MINIMAL" up
}

run_tests_only() {
    echo "Running tests for microservices..."
    (cd query-microservice && mvn -q test) && \
    (cd inventory-microservice && mvn -q test) && \
    echo "All tests passed!"
}

start_full() {
    echo "Packaging microservices..."
    (cd query-microservice && mvn -q package ${SKIP_TESTS}) && \
    (cd inventory-microservice && mvn -q package ${SKIP_TESTS}) && \
    echo "Success building sources! Starting full environment..." && \
    docker compose -f "$COMPOSE_FULL" build && \
    docker compose -f "$COMPOSE_FULL" up
}

parse_args "$@"

if [[ -n "$TESTS_ONLY" ]]; then
    run_tests_only
    exit 0
fi

case "$MODE" in
    minimal)
        start_minimal
        ;;
    full)
        start_full
        ;;
    *)
        echo "Usage: $0 [minimal|--minimal|-m] [full|--full|-f] [--skip-tests] [--tests-only]"
        echo "  (no args)  Build and start full stack, run tests"
        echo "  minimal   Start infrastructure only (Kafka + Zipkin + Prometheus)"
        echo "  full      Build and start full stack (same as no args)"
        echo "  --skip-tests  Skip tests when packaging microservices (full stack only)"
        echo "  --tests-only  Run tests only for microservices (no Docker)"
        exit 1
        ;;
esac