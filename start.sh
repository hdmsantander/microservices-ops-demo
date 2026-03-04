#!/bin/bash

set -e

COMPOSE_MINIMAL="docker-compose-minimal.yml"
COMPOSE_FULL="docker-compose.yml"
SKIP_TESTS=""
MODE="full"
TESTS_ONLY=""

# Ports required by Docker stack (Redis:6379, redis_exporter:9121, Kafka:9092, Prometheus:9412, Zipkin:9411, Grafana:3000, Admin:8089, inventory:8085/9090, query:8086)
PORTS_MINIMAL="6379 9092 9411 9412 9121"
PORTS_FULL="6379 8085 8086 8089 9090 9092 9411 9412 9121 3000"

# Maven command: use wrapper (./mvnw) only in cloud env or when --mvnw; default mvn
USE_MVNW="${USE_MVNW:-}"
[[ -n "${CURSOR_RUNTIME:-}" || -n "${CI:-}" || -n "${GITHUB_ACTIONS:-}" || -n "${GITPOD_WORKSPACE_URL:-}" ]] && USE_MVNW=1
get_mvn() {
    local dir="${1:-.}"
    if [[ -n "$USE_MVNW" ]] && [[ -x "$dir/mvnw" ]]; then
        echo "./mvnw"
    else
        echo "mvn"
    fi
}

is_port_in_use() {
    local port=$1
    if command -v ss &>/dev/null; then
        ss -tlnp 2>/dev/null | grep -qE ":${port}[^0-9]"
    elif command -v nc &>/dev/null; then
        nc -z 127.0.0.1 "$port" 2>/dev/null
    else
        (echo >/dev/tcp/127.0.0.1/"$port") 2>/dev/null
    fi
}

check_ports_available() {
    local ports="$1"
    local in_use=""
    for p in $ports; do
        if is_port_in_use "$p"; then
            in_use="${in_use}${in_use:+ }${p}"
        fi
    done
    if [[ -n "$in_use" ]]; then
        echo "ERROR: Port(s) already in use: $in_use"
        echo "Stop any running services (e.g. docker compose down, previous run) before starting."
        exit 1
    fi
}

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
            --mvnw)
                USE_MVNW=1
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
                echo "Usage: $0 [minimal|--minimal|-m] [full|--full|-f] [--skip-tests] [--tests-only] [--mvnw]"
                echo "  (no args)  Build and start full stack, run tests"
                echo "  minimal   Start infrastructure only (Kafka + Zipkin + Prometheus)"
                echo "  full      Build and start full stack (same as no args)"
                echo "  --skip-tests  Skip tests when packaging microservices (full stack only)"
                echo "  --tests-only  Run tests only for microservices (no Docker)"
                echo "  --mvnw     Use Maven wrapper (./mvnw) instead of system mvn (auto in cloud: CI, CURSOR_RUNTIME)"
                exit 1
                ;;
        esac
    done
}

start_minimal() {
    echo "Checking required ports are available..."
    check_ports_available "$PORTS_MINIMAL"
    echo "Starting minimal infrastructure (Redis, Kafka, Zipkin, Prometheus, redis_exporter)..."
    docker compose -f "$COMPOSE_MINIMAL" up
}

run_tests_only() {
    local mvn_q="$(get_mvn query-microservice)"
    local mvn_i="$(get_mvn inventory-microservice)"
    echo "Running tests and coverage for microservices..."
    (cd query-microservice && $mvn_q -q verify) && \
    (cd inventory-microservice && $mvn_i -q verify) && \
    echo "" && \
    echo "All tests passed!" && \
    echo "" && \
    echo "Test summary:" && \
    (print_test_summary 2>/dev/null || true) && \
    echo "" && \
    echo "Coverage reports (HTML):" && \
    echo "  query-microservice:      query-microservice/target/site/jacoco/index.html" && \
    echo "  inventory-microservice: inventory-microservice/target/site/jacoco/index.html" && \
    echo "" && \
    (print_coverage_summary 2>/dev/null || true) && \
    echo "" && \
    echo "Verifying ports are free after tests (no stray processes)..." && \
    check_ports_available "$PORTS_FULL" && echo "Ports OK."
}

print_test_summary() {
    for dir in query-microservice inventory-microservice; do
        report_dir="${dir}/target/surefire-reports"
        if [[ ! -d "$report_dir" ]]; then
            continue
        fi
        reports=("${report_dir}"/TEST-*.xml)
        if [[ ! -e "${reports[0]}" ]]; then
            continue
        fi
        awk -v d="${dir}" '
            /<testsuite/ {
                t = f = e = s = 0
                if (match($0, /tests="[0-9]+/)) t = substr($0, RSTART+7, RLENGTH-7)+0
                if (match($0, /failures="[0-9]+/)) f = substr($0, RSTART+10, RLENGTH-10)+0
                if (match($0, /errors="[0-9]+/)) e = substr($0, RSTART+8, RLENGTH-8)+0
                if (match($0, /skipped="[0-9]+/)) s = substr($0, RSTART+9, RLENGTH-9)+0
                tests += t
                failures += f
                errors += e
                skipped += s
            }
            END {
                if (NR > 0) printf "  %s: Tests run: %d, Failures: %d, Errors: %d, Skipped: %d\n", d, tests, failures, errors, skipped
            }
        ' "${reports[@]}" 2>/dev/null
    done
}

print_coverage_summary() {
    for dir in query-microservice inventory-microservice; do
        csv="${dir}/target/site/jacoco/jacoco.csv"
        if [[ -f "$csv" ]]; then
            sum=$(awk -F',' 'NR>1 {im+=$4; ic+=$5} END {print im+0, ic+0}' "$csv")
            missed=$(echo "$sum" | cut -d' ' -f1)
            covered=$(echo "$sum" | cut -d' ' -f2)
            total=$((missed + covered))
            if [[ $total -gt 0 ]]; then
                pct=$(( (covered * 100) / total ))
                echo "  ${dir}: ${covered}/${total} instructions (${pct}%)"
            fi
        fi
    done
}

start_full() {
    local mvn_q="$(get_mvn query-microservice)"
    local mvn_i="$(get_mvn inventory-microservice)"
    local mvn_a="$(get_mvn admin-server)"
    local mvn_api="$(get_mvn inventory-microservice)"
    echo "Packaging microservices..."
    (cd inventory-grpc-api && $mvn_i -q -f pom.xml install -DskipTests) && \
    (cd query-microservice && $mvn_q -q package ${SKIP_TESTS}) && \
    (cd inventory-microservice && $mvn_i -q package ${SKIP_TESTS}) && \
    (cd admin-server && $mvn_a -q package -DskipTests) && \
    echo "Success building sources! Checking required ports before Docker..." && \
    check_ports_available "$PORTS_FULL" && \
    echo "Starting full environment..." && \
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
        echo "Usage: $0 [minimal|--minimal|-m] [full|--full|-f] [--skip-tests] [--tests-only] [--mvnw]"
        echo "  (no args)  Build and start full stack, run tests"
        echo "  minimal   Start infrastructure only (Kafka + Zipkin + Prometheus)"
        echo "  full      Build and start full stack (same as no args)"
        echo "  --skip-tests  Skip tests when packaging microservices (full stack only)"
        echo "  --tests-only  Run tests only for microservices (no Docker)"
        exit 1
        ;;
esac