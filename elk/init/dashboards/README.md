# Kibana Dashboard Provisioning

Place exported Kibana saved objects (`.ndjson`) in this directory to have them imported automatically during ELK initialization.

## Bundled dashboards

- **log-overview.ndjson** – A minimal Log Overview dashboard (empty panels). Open in Kibana and add visualizations via Lens using the `application-logs*` data view.

## How to add more dashboards

1. Create dashboards in Kibana (http://localhost:5601) using **Stack Management** → **Saved Objects** → **Import** for reference, or create manually.
2. Export your dashboards: **Stack Management** → **Saved Objects** → select objects → **Export**.
3. Save the downloaded `.ndjson` file(s) into this directory.
4. Rebuild the elk-init image and restart: `docker compose build elk-init && docker compose up -d`.

The elk-init script will import any `*.ndjson` files from this directory after provisioning the data view.

## Reference

See [docs/KIBANA_DASHBOARDS_PROPOSAL.md](../../docs/KIBANA_DASHBOARDS_PROPOSAL.md) for proposed dashboard designs (Log Overview, Error Monitoring, Trace Correlation, Log Processing) based on the log schema.
