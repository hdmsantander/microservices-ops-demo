#!/bin/bash
# Provision Kibana: create data view and import dashboards.
# Run after Kibana and Elasticsearch are up and connector has ingested data.
# Usage: ./provision-kibana.sh [kibana_url]
set -e
KIBANA_URL="${1:-http://localhost:5601}"
DASHBOARDS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/kibana-dashboards" && pwd)"

echo "Waiting for Kibana at $KIBANA_URL..."
for i in $(seq 1 60); do
  if curl -sSf "$KIBANA_URL/api/status" &>/dev/null; then
    echo "Kibana ready."
    break
  fi
  sleep 2
  [[ $i -eq 60 ]] && { echo "Timeout waiting for Kibana."; exit 1; }
done

# Create data view for application-logs (via API or import)
echo "Creating data view for application-logs*..."
EXISTING=$(curl -sS "$KIBANA_URL/api/data_views" 2>/dev/null | grep -o '"title":"application-logs\*"' || true)
if [[ -n "$EXISTING" ]]; then
  echo "Data view application-logs* already exists."
else
  # Try API first (Kibana 8)
  if curl -sS -X POST -H "kbn-xsrf: true" -H "Content-Type: application/json" \
    --data '{"data_view":{"title":"application-logs*","timeFieldName":"@timestamp","name":"Application Logs"}}' \
    "$KIBANA_URL/api/data_views/data_view" 2>/dev/null | grep -q '"id"'; then
    echo "Data view created via API."
  else
    echo "Data view creation skipped (create manually in Kibana Stack Management > Data Views)."
  fi
fi

# Import dashboards if ndjson exists
if [[ -d "$DASHBOARDS_DIR" ]]; then
  for f in "$DASHBOARDS_DIR"/*.ndjson; do
    [[ -f "$f" ]] || continue
    echo "Importing $(basename "$f")..."
    curl -sS -X POST -H "kbn-xsrf: true" \
      -F "file=@$f" \
      "$KIBANA_URL/api/saved_objects/_import?overwrite=true" | grep -q '"success"' && echo "  Imported." || echo "  Skipped or failed."
  done
fi

echo ""
echo "Kibana provisioning complete. Open Discover: $KIBANA_URL/app/discover"
