#!/usr/bin/env python3
"""
Static validation for metrics / logs provisioning artifacts (no Docker required).

Run from repo root:
  python3 scripts/validate_observability_assets.py

Checks:
  - elk/scripts/render_kibana_dashboards.py compiles
  - Grafana dashboard JSON files parse
  - prometheus.yml is readable (best-effort YAML parse if PyYAML available)
  - Kibana NDJSON: one JSON object per non-empty line, data view file sorts first,
    dashboard objects reference the application-logs index pattern
"""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path


def root() -> Path:
    return Path(__file__).resolve().parents[1]


def err(msg: str) -> None:
    print(f"ERROR: {msg}", file=sys.stderr)


def warn(msg: str) -> None:
    print(f"WARN: {msg}", file=sys.stderr)


def check_py_compile(path: Path) -> bool:
    try:
        subprocess.run(
            [sys.executable, "-m", "py_compile", str(path)],
            check=True,
            capture_output=True,
            text=True,
        )
        return True
    except subprocess.CalledProcessError as e:
        err(f"py_compile failed for {path}: {e.stderr or e.stdout}")
        return False


def check_grafana_dashboards(d_dir: Path) -> bool:
    ok = True
    for p in sorted(d_dir.glob("*.json")):
        try:
            json.loads(p.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            err(f"Invalid JSON: {p}: {e}")
            ok = False
    if not ok:
        return False
    files = list(d_dir.glob("*.json"))
    if not files:
        err(f"No Grafana JSON dashboards in {d_dir}")
        return False
    print(f"OK: {len(files)} Grafana dashboard JSON file(s)")
    return True


def check_prometheus_config(p: Path) -> bool:
    if not p.is_file():
        err(f"Missing {p}")
        return False
    text = p.read_text(encoding="utf-8")
    if "scrape_configs:" not in text:
        err(f"{p} does not look like a Prometheus config (no scrape_configs)")
        return False
    try:
        import yaml  # type: ignore

        yaml.safe_load(text)
    except ImportError:
        warn("PyYAML not installed; skipped YAML parse for prometheus.yml (text checks only)")
    except Exception as e:
        err(f"prometheus.yml YAML parse failed: {e}")
        return False
    print("OK: prometheus.yml present (and parsed if PyYAML available)")
    return True


def load_ndjson_lines(path: Path) -> list[dict]:
    docs = []
    for i, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line:
            continue
        try:
            docs.append(json.loads(line))
        except json.JSONDecodeError as e:
            raise ValueError(f"{path}:{i}: {e}") from e
    return docs


def check_kibana_ndjson(d_dir: Path) -> bool:
    paths = sorted(d_dir.glob("*.ndjson"))
    if not paths:
        err(f"No .ndjson files in {d_dir}")
        return False

    first = paths[0].name
    if first != "application-logs-dataview.ndjson":
        err(
            "First file when sorting NDJSON must be application-logs-dataview.ndjson "
            f"so elk-init imports the data view before dashboards (got {first} first)."
        )
        return False

    dataview_path = d_dir / "application-logs-dataview.ndjson"
    if not dataview_path.is_file():
        err("Missing application-logs-dataview.ndjson")
        return False

    try:
        dv_docs = load_ndjson_lines(dataview_path)
    except ValueError as e:
        err(str(e))
        return False
    if not dv_docs:
        err("application-logs-dataview.ndjson is empty")
        return False
    if dv_docs[0].get("type") != "index-pattern":
        err("application-logs-dataview.ndjson must contain an index-pattern object")
        return False
    if dv_docs[0].get("id") != "application-logs":
        err('Data view object id should be "application-logs" for dashboard references')
        return False

    ok = True
    for p in paths:
        try:
            docs = load_ndjson_lines(p)
        except ValueError as e:
            err(str(e))
            ok = False
            continue
        if not docs:
            err(f"{p.name}: no JSON documents")
            ok = False
            continue
        for doc in docs:
            t = doc.get("type")
            if t == "dashboard":
                refs = doc.get("references") or []
                has_logs = any(
                    r.get("type") == "index-pattern" and r.get("id") == "application-logs"
                    for r in refs
                )
                if not has_logs:
                    err(
                        f"{p.name}: dashboard {doc.get('id', '?')!r} missing "
                        "reference to index-pattern id application-logs"
                    )
                    ok = False

    if ok:
        print(f"OK: {len(paths)} Kibana NDJSON file(s), data view ordering and references")
    return ok


def main() -> int:
    r = root()
    failures = 0

    print("== render_kibana_dashboards.py ==")
    if not check_py_compile(r / "elk" / "scripts" / "render_kibana_dashboards.py"):
        failures += 1

    print("== Grafana dashboards ==")
    if not check_grafana_dashboards(r / "grafana" / "provisioning" / "dashboards" / "default"):
        failures += 1

    print("== prometheus.yml ==")
    if not check_prometheus_config(r / "prometheus" / "prometheus.yml"):
        failures += 1

    print("== Kibana NDJSON ==")
    if not check_kibana_ndjson(r / "elk" / "kibana-dashboards"):
        failures += 1

    if failures:
        print(f"\n{failures} check group(s) failed.", file=sys.stderr)
        return 1
    print("\nAll observability asset checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
