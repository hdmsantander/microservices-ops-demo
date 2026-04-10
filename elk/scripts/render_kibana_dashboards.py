#!/usr/bin/env python3
"""
Regenerate Kibana dashboard NDJSON under elk/kibana-dashboards/ (except data view).
Run from repo root: python3 elk/scripts/render_kibana_dashboards.py
"""
from __future__ import annotations

import json
from pathlib import Path

CORE = "8.15.0"
DATA_VIEW_ID = "application-logs"
OPTIONS = {
    "useMargins": True,
    "syncColors": False,
    "syncCursor": True,
    "syncTooltips": True,
    "hidePanelTitles": False,
}


def ref(layer_key: str) -> dict:
    return {
        "type": "index-pattern",
        "id": DATA_VIEW_ID,
        "name": f"indexpattern-datasource-layer-{layer_key}",
    }


def col_count(cid: str) -> dict:
    return {
        "dataType": "number",
        "isBucketed": False,
        "label": "Count",
        "operationType": "count",
        "params": {},
        "scale": "ratio",
        "sourceField": "Records",
    }


def col_date_hist(cid: str, interval: str = "auto") -> dict:
    return {
        "dataType": "date",
        "isBucketed": True,
        "label": "@timestamp",
        "operationType": "date_histogram",
        "params": {"interval": interval},
        "scale": "interval",
        "sourceField": "@timestamp",
    }


def col_terms(
    cid: str,
    field: str,
    label: str,
    size: int = 10,
    order_by_metric: str | None = None,
) -> dict:
    params: dict = {"size": size}
    if order_by_metric:
        params["orderBy"] = {"columnId": order_by_metric, "type": "column"}
        params["orderDirection"] = "desc"
    return {
        "dataType": "string",
        "isBucketed": True,
        "label": label,
        "operationType": "terms",
        "params": params,
        "scale": "ordinal",
        "sourceField": field,
    }


def lens_layer_query(kql: str) -> dict:
    return {"language": "kuery", "query": kql}


def form_layer(layer_id: str, column_order: list[str], columns: dict[str, dict]) -> dict:
    return {
        "formBased": {
            "layers": {
                layer_id: {"columnOrder": column_order, "columns": columns},
            }
        }
    }


def panel_lens_metric(
    title: str,
    kql: str,
    layer_id: str,
    col_id: str,
    panel_index: str,
    grid: dict,
) -> dict:
    return {
        "version": CORE,
        "type": "lens",
        "gridData": grid,
        "panelIndex": panel_index,
        "embeddableConfig": {
            "attributes": {
                "title": title,
                "visualizationType": "lnsMetric",
                "type": "lens",
                "state": {
                    "visualization": {
                        "layerId": layer_id,
                        "layerType": "data",
                        "metricAccessor": col_id,
                    },
                    "query": lens_layer_query(kql),
                    "filters": [],
                    "datasourceStates": form_layer(
                        layer_id,
                        [col_id],
                        {col_id: col_count(col_id)},
                    ),
                },
            },
            "panelRefName": f"panel_{panel_index}",
        },
    }


def panel_lens_xy(
    title: str,
    kql: str,
    layer_id: str,
    ts_col: str,
    cnt_col: str,
    panel_index: str,
    grid: dict,
    *,
    series_type: str = "line",
    split_col: str | None = None,
    split_field: str = "service",
    split_label: str = "service",
    interval: str = "auto",
) -> dict:
    layer = {
        "accessors": [cnt_col],
        "layerId": layer_id,
        "position": "top",
        "seriesType": series_type,
        "showGridlines": False,
        "xAccessor": ts_col,
    }
    if split_col:
        layer["splitAccessor"] = split_col
    col_order = [ts_col, cnt_col]
    cols = {
        ts_col: col_date_hist(ts_col, interval),
        cnt_col: col_count(cnt_col),
    }
    if split_col:
        col_order.insert(1, split_col)
        cols[split_col] = col_terms(split_col, split_field, split_label, size=10)
    return {
        "version": CORE,
        "type": "lens",
        "gridData": grid,
        "panelIndex": panel_index,
        "embeddableConfig": {
            "attributes": {
                "title": title,
                "visualizationType": "lnsXY",
                "type": "lens",
                "state": {
                    "visualization": {
                        "layers": [layer],
                        "legend": {"isVisible": True, "position": "right"},
                        "preferredSeriesType": series_type,
                    },
                    "query": lens_layer_query(kql),
                    "filters": [],
                    "datasourceStates": form_layer(layer_id, col_order, cols),
                },
            },
            "panelRefName": f"panel_{panel_index}",
        },
    }


def panel_lens_pie(
    title: str,
    kql: str,
    layer_id: str,
    grp_col: str,
    cnt_col: str,
    field: str,
    label: str,
    panel_index: str,
    grid: dict,
) -> dict:
    return {
        "version": CORE,
        "type": "lens",
        "gridData": grid,
        "panelIndex": panel_index,
        "embeddableConfig": {
            "attributes": {
                "title": title,
                "visualizationType": "lnsPie",
                "type": "lens",
                "state": {
                    "visualization": {
                        "shape": "pie",
                        "layers": [
                            {
                                "categoryDisplay": "default",
                                "layerId": layer_id,
                                "layerType": "data",
                                "legendDisplay": "default",
                                "metrics": [cnt_col],
                                "numberDisplay": "percent",
                                "primaryGroups": [grp_col],
                            }
                        ],
                    },
                    "query": lens_layer_query(kql),
                    "filters": [],
                    "datasourceStates": form_layer(
                        layer_id,
                        [grp_col, cnt_col],
                        {
                            grp_col: col_terms(grp_col, field, label, order_by_metric=cnt_col),
                            cnt_col: col_count(cnt_col),
                        },
                    ),
                },
            },
            "panelRefName": f"panel_{panel_index}",
        },
    }


def panel_lens_bar(
    title: str,
    kql: str,
    layer_id: str,
    x_col: str,
    cnt_col: str,
    field: str,
    label: str,
    panel_index: str,
    grid: dict,
    *,
    size: int = 10,
) -> dict:
    return {
        "version": CORE,
        "type": "lens",
        "gridData": grid,
        "panelIndex": panel_index,
        "embeddableConfig": {
            "attributes": {
                "title": title,
                "visualizationType": "lnsBarVertical",
                "type": "lens",
                "state": {
                    "visualization": {
                        "layers": [
                            {
                                "accessors": [cnt_col],
                                "layerId": layer_id,
                                "position": "top",
                                "seriesType": "bar",
                                "showGridlines": False,
                                "xAccessor": x_col,
                            }
                        ],
                        "legend": {"isVisible": True, "position": "right"},
                        "preferredSeriesType": "bar",
                    },
                    "query": lens_layer_query(kql),
                    "filters": [],
                    "datasourceStates": form_layer(
                        layer_id,
                        [x_col, cnt_col],
                        {
                            x_col: col_terms(x_col, field, label, size=size, order_by_metric=cnt_col),
                            cnt_col: col_count(cnt_col),
                        },
                    ),
                },
            },
            "panelRefName": f"panel_{panel_index}",
        },
    }


def dashboard_doc(
    *,
    dash_id: str,
    title: str,
    description: str,
    panels: list[dict],
    layer_keys: list[str],
) -> dict:
    refs = [ref(k) for k in layer_keys]
    return {
        "attributes": {
            "description": description,
            "kibanaSavedObjectMeta": {"searchSourceJSON": "{}"},
            "optionsJSON": json.dumps(OPTIONS),
            "panelsJSON": json.dumps(panels),
            "timeRestore": False,
            "title": title,
            "version": 1,
        },
        "coreMigrationVersion": CORE,
        "created_at": "2024-01-01T00:00:00.000Z",
        "id": dash_id,
        "references": refs,
        "type": "dashboard",
        "typeMigrationVersion": CORE,
        "updated_at": "2024-01-01T00:00:00.000Z",
        "version": "WzEsMV0=",
    }


def write_ndjson(path: Path, doc: dict) -> None:
    path.write_text(json.dumps(doc, separators=(",", ":")) + "\n", encoding="utf-8")


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    out_dir = root / "elk" / "kibana-dashboards"

    # --- Log Overview ---
    panels_lo = [
        panel_lens_xy(
            "Log volume over time",
            "",
            "layer1",
            "col-ts",
            "col-count",
            "1",
            {"x": 0, "y": 0, "w": 12, "h": 14, "i": "1"},
            series_type="area",
        ),
        panel_lens_pie(
            "Log volume by service",
            "",
            "layer2",
            "col-service",
            "col-count2",
            "service",
            "service",
            "2",
            {"x": 12, "y": 0, "w": 6, "h": 14, "i": "2"},
        ),
        panel_lens_bar(
            "Log volume by level",
            "",
            "layer3",
            "col-level",
            "col-count3",
            "level",
            "level",
            "3",
            {"x": 18, "y": 0, "w": 6, "h": 14, "i": "3"},
        ),
        panel_lens_metric(
            "ERROR count (time range)",
            "level: ERROR",
            "layer4",
            "col-err",
            "4",
            {"x": 0, "y": 14, "w": 6, "h": 10, "i": "4"},
        ),
        panel_lens_metric(
            "WARN count (time range)",
            "level: WARN",
            "layer5",
            "col-warn",
            "5",
            {"x": 6, "y": 14, "w": 6, "h": 10, "i": "5"},
        ),
    ]
    write_ndjson(
        out_dir / "log-overview.ndjson",
        dashboard_doc(
            dash_id="log-overview-demo",
            title="Log Overview",
            description=(
                "Log volume and health across Query and Inventory. "
                "ERROR/WARN stats use the dashboard time picker (set e.g. Last 24 hours in Kibana)."
            ),
            panels=panels_lo,
            layer_keys=["layer1", "layer2", "layer3", "layer4", "layer5"],
        ),
    )

    # --- Error & Warning Monitoring ---
    panels_ew = [
        panel_lens_xy(
            "Errors over time",
            "level: ERROR",
            "layer1",
            "col-ts",
            "col-count",
            "1",
            {"x": 0, "y": 0, "w": 12, "h": 14, "i": "1"},
            series_type="line",
        ),
        panel_lens_xy(
            "Warnings over time",
            "level: WARN",
            "layer2",
            "col-ts2",
            "col-count2",
            "2",
            {"x": 12, "y": 0, "w": 12, "h": 14, "i": "2"},
            series_type="line",
        ),
        panel_lens_bar(
            "Errors by service",
            "level: ERROR",
            "layer3",
            "col-svc",
            "col-c3",
            "service",
            "service",
            "3",
            {"x": 0, "y": 14, "w": 12, "h": 14, "i": "3"},
            size=10,
        ),
        panel_lens_bar(
            "Top error loggers",
            "level: ERROR",
            "layer4",
            "col-log",
            "col-c4",
            "logger_name",
            "logger_name",
            "4",
            {"x": 12, "y": 14, "w": 12, "h": 14, "i": "4"},
            size=15,
        ),
    ]
    write_ndjson(
        out_dir / "error-warning-monitoring.ndjson",
        dashboard_doc(
            dash_id="error-warning-monitoring-demo",
            title="Error & Warning Monitoring",
            description=(
                "Errors and warnings from Query and Inventory. "
                "For raw ERROR documents with full message text, use Discover with KQL: level: ERROR."
            ),
            panels=panels_ew,
            layer_keys=["layer1", "layer2", "layer3", "layer4"],
        ),
    )

    # --- Trace Correlation ---
    panels_tc = [
        panel_lens_xy(
            "Logs with trace IDs over time",
            "traceId: *",
            "layer1",
            "col-ts",
            "col-count",
            "1",
            {"x": 0, "y": 0, "w": 24, "h": 12, "i": "1"},
            series_type="area",
        ),
    ]
    write_ndjson(
        out_dir / "trace-correlation.ndjson",
        dashboard_doc(
            dash_id="trace-correlation-demo",
            title="Trace Correlation",
            description=(
                "Correlate logs with Zipkin. Steps: (1) Open Zipkin at http://localhost:9411 and copy a traceId. "
                "(2) In Kibana Discover, select data view Application Logs and filter: traceId: \"YOUR_ID\". "
                "(3) Pair with Grafana (http://localhost:3000) and Pet Shop Overview for metrics context."
            ),
            panels=panels_tc,
            layer_keys=["layer1"],
        ),
    )

    # --- Log Operations ---
    panels_ops = [
        panel_lens_xy(
            "Logs per minute by service",
            "",
            "layer1",
            "col-ts",
            "col-count",
            "1",
            {"x": 0, "y": 0, "w": 14, "h": 14, "i": "1"},
            series_type="line",
            split_col="col-service",
            interval="1m",
        ),
        panel_lens_pie(
            "Logger distribution (top 10)",
            "",
            "layer2",
            "col-log",
            "col-c2",
            "logger_name",
            "logger_name",
            "2",
            {"x": 14, "y": 0, "w": 10, "h": 14, "i": "2"},
        ),
        panel_lens_metric(
            "Log lines with traceId (time range)",
            "traceId: *",
            "layer3",
            "col-tr",
            "3",
            {"x": 0, "y": 14, "w": 8, "h": 10, "i": "3"},
        ),
    ]
    write_ndjson(
        out_dir / "log-operations.ndjson",
        dashboard_doc(
            dash_id="log-operations-demo",
            title="Log Operations",
            description=(
                "Throughput and logger mix. Message-length histograms need a mapped keyword subfield on "
                "message; use Discover for ad-hoc message analysis until then."
            ),
            panels=panels_ops,
            layer_keys=["layer1", "layer2", "layer3"],
        ),
    )

    # --- Microservices Log Health (unchanged structure, minor description) ---
    panels_mh = [
        panel_lens_xy(
            "Errors over time",
            "level: ERROR",
            "layer1",
            "col-ts",
            "col-count",
            "1",
            {"x": 0, "y": 0, "w": 8, "h": 12, "i": "1"},
            series_type="area",
        ),
        panel_lens_xy(
            "Warnings over time",
            "level: WARN",
            "layer2",
            "col-ts2",
            "col-count2",
            "2",
            {"x": 8, "y": 0, "w": 8, "h": 12, "i": "2"},
            series_type="area",
        ),
        panel_lens_xy(
            "Log volume over time by service",
            "",
            "layer3",
            "col-ts3",
            "col-count3",
            "3",
            {"x": 0, "y": 12, "w": 24, "h": 14, "i": "3"},
            series_type="area",
            split_col="col-service",
        ),
    ]

    write_ndjson(
        out_dir / "microservices-health.ndjson",
        dashboard_doc(
            dash_id="microservices-health-demo",
            title="Microservices Log Health",
            description=(
                "Combined ERROR/WARN trends and log volume by service for Query and Inventory."
            ),
            panels=panels_mh,
            layer_keys=["layer1", "layer2", "layer3"],
        ),
    )

    # --- Logs by Service & Level (unchanged panels) ---
    panels_lsl = [
        panel_lens_pie(
            "Logs by service",
            "",
            "layer1",
            "col-service",
            "col-count",
            "service",
            "service",
            "1",
            {"x": 0, "y": 0, "w": 8, "h": 15, "i": "1"},
        ),
        panel_lens_bar(
            "Logs by level",
            "",
            "layer2",
            "col-level",
            "col-count2",
            "level",
            "level",
            "2",
            {"x": 8, "y": 0, "w": 8, "h": 15, "i": "2"},
        ),
    ]
    write_ndjson(
        out_dir / "logs-by-service-level.ndjson",
        dashboard_doc(
            dash_id="logs-by-service-level-demo",
            title="Logs by Service & Level",
            description="Log volume by service (Query vs Inventory) and by level.",
            panels=panels_lsl,
            layer_keys=["layer1", "layer2"],
        ),
    )

    def service_dashboard(svc: str, slug: str, title: str, dash_id: str, focus: str) -> None:
        kql = f'service: "{svc}"'
        panels_sv = [
            panel_lens_xy(
                "Log volume over time",
                kql,
                "layer1",
                "col-ts",
                "col-c1",
                "1",
                {"x": 0, "y": 0, "w": 24, "h": 12, "i": "1"},
                series_type="area",
            ),
            panel_lens_xy(
                "Errors over time",
                f'{kql} and level: ERROR',
                "layer2",
                "col-ts2",
                "col-c2",
                "2",
                {"x": 0, "y": 12, "w": 12, "h": 12, "i": "2"},
                series_type="line",
            ),
            panel_lens_xy(
                "Warnings over time",
                f'{kql} and level: WARN',
                "layer3",
                "col-ts3",
                "col-c3",
                "3",
                {"x": 12, "y": 12, "w": 12, "h": 12, "i": "3"},
                series_type="line",
            ),
            panel_lens_bar(
                "Top loggers",
                kql,
                "layer4",
                "col-log",
                "col-c4",
                "logger_name",
                "logger_name",
                "4",
                {"x": 0, "y": 24, "w": 24, "h": 12, "i": "4"},
                size=15,
            ),
        ]
        write_ndjson(
            out_dir / f"{slug}.ndjson",
            dashboard_doc(
                dash_id=dash_id,
                title=title,
                description=(
                    f"Service-scoped log health for {svc}. {focus} "
                    "Use Grafana Pet Shop Overview for request rates and latencies."
                ),
                panels=panels_sv,
                layer_keys=["layer1", "layer2", "layer3", "layer4"],
            ),
        )

    service_dashboard(
        "query-microservice",
        "service-query-logs",
        "Query — Application Logs",
        "service-query-logs-demo",
        focus="PetStore HTTP, Redis reservations, adoption flow, gRPC client to Inventory.",
    )
    service_dashboard(
        "inventory-microservice",
        "service-inventory-logs",
        "Inventory — Application Logs",
        "service-inventory-logs-demo",
        focus="Order sync scheduler, gRPC server, PetStore HTTP, Kafka producers.",
    )

    # --- Log severity by service (errors/warnings over time + pipeline-oriented counts) ---
    panels_sev = [
        panel_lens_xy(
            "Errors over time by service",
            "level: ERROR",
            "layer1",
            "col-ts",
            "col-count",
            "1",
            {"x": 0, "y": 0, "w": 12, "h": 14, "i": "1"},
            series_type="line",
            split_col="col-service",
        ),
        panel_lens_xy(
            "Warnings over time by service",
            "level: WARN",
            "layer2",
            "col-ts2",
            "col-count2",
            "2",
            {"x": 12, "y": 0, "w": 12, "h": 14, "i": "2"},
            series_type="line",
            split_col="col-svc2",
        ),
        panel_lens_metric(
            "INFO log lines (time range)",
            "level: INFO",
            "layer3",
            "col-info",
            "3",
            {"x": 0, "y": 14, "w": 6, "h": 10, "i": "3"},
        ),
        panel_lens_metric(
            "ERROR with stack_trace (time range)",
            "level: ERROR and stack_trace: *",
            "layer4",
            "col-st",
            "4",
            {"x": 6, "y": 14, "w": 6, "h": 10, "i": "4"},
        ),
        panel_lens_metric(
            "ERROR count (time range)",
            "level: ERROR",
            "layer5",
            "col-err",
            "5",
            {"x": 12, "y": 14, "w": 6, "h": 10, "i": "5"},
        ),
        panel_lens_metric(
            "WARN count (time range)",
            "level: WARN",
            "layer6",
            "col-warn",
            "6",
            {"x": 18, "y": 14, "w": 6, "h": 10, "i": "6"},
        ),
    ]
    write_ndjson(
        out_dir / "log-severity-by-service.ndjson",
        dashboard_doc(
            dash_id="log-severity-by-service-demo",
            title="Log Severity by Service",
            description=(
                "ERROR and WARN trends split by Query vs Inventory; INFO throughput and "
                "exceptions with stack_trace. Pair with Grafana dashboards Application observability "
                "and Pet Shop Overview for HTTP 5xx and reservation timers."
            ),
            panels=panels_sev,
            layer_keys=["layer1", "layer2", "layer3", "layer4", "layer5", "layer6"],
        ),
    )

    print(f"Wrote dashboards to {out_dir}")


if __name__ == "__main__":
    main()
