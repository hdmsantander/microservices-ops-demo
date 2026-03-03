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
    echo "Running tests and coverage for microservices..."
    (cd query-microservice && mvn -q verify) && \
    (cd inventory-microservice && mvn -q verify) && \
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
    (print_coverage_summary 2>/dev/null || true)
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