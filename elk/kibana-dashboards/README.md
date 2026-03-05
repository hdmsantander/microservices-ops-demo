# Kibana Dashboards

Dashboards can be created manually in Kibana or imported if ndjson files are provided.

## Basic Dashboard (Log Discovery)

1. Open **Discover** (http://localhost:5601/app/discover)
2. Select data view **Application Logs** (application-logs*)
3. Use filters: `service: "query-microservice"` or `level: "ERROR"`
4. Save as **Log Discovery** or use Discover's built-in search

## Advanced Dashboard

Create a new Dashboard and add:

### Panel 1: Logs by Service (Pie)

- Visualization: **Lens**
- Add aggregation: **Count** by **service** (keyword)
- Chart type: **Pie**

### Panel 2: Logs by Level (Bar)

- Visualization: **Lens**
- X-axis: **level** (keyword)
- Y-axis: **Count**

### Panel 3: Error Rate Over Time

- Visualization: **Lens**
- X-axis: **@timestamp** (Date Histogram)
- Y-axis: **Count**
- Filter: `level: "ERROR"` or `level: "WARN"`

### Panel 4: Trace Correlation

- Use Discover with filter: `traceId: "YOUR_TRACE_ID"`
- Correlate logs across Query and Inventory by traceId

Save as **Advanced Log Analytics**.
