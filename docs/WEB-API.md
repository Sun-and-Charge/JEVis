# Web API (JSON) for the web app

All endpoints are below `/JEWebService/v1`, need a Bearer access token (see `/auth/login`) and answer JSON with
`Cache-Control: no-store`. Rights are those of the logged-in user: objects without read right are never returned.

## Navigation (W1)

`GET /web/navigation` – organisations and buildings the user may read, each with its analyses and dashboards.
See `ResourceWebNavigation`.

## Analyses (W2)

### `GET /web/analyses/{id}` – configuration

```json
{
  "id": 1001,
  "name": "Electricity overview",
  "names": { "de": "Stromübersicht" },
  "chartsPerScreen": 2,
  "horizontalPies": 3,
  "horizontalTables": 3,
  "autoSize": true,
  "defaults": { "timeFrame": "TODAY", "aggregation": "NONE", "manipulation": "NONE" },
  "charts": [
    {
      "id": 0,
      "name": "Consumption",
      "type": "LINE",
      "minFractionDigits": 2,
      "maxFractionDigits": 2,
      "height": -1,
      "orientation": "HORIZONTAL",
      "fixYAxisToZero": false,
      "xAxisTitle": null,
      "yAxisTitle": null,
      "groupingInterval": 30.0,
      "colorMapping": "GREEN_YELLOW_RED",
      "rows": [
        {
          "key": "0-2001-Value",
          "objectId": 2001,
          "attribute": "Value",
          "name": "Meter A",
          "color": "#00538a",
          "axis": 0,
          "chartType": "DEFAULT",
          "unit": "kWh",
          "decimalDigits": -1,
          "calculation": false,
          "bubbleType": "NONE",
          "readable": true
        }
      ]
    }
  ]
}
```

- `type` of a chart and `chartType` of a row: `AREA, LOGICAL, LINE, BAR, COLUMN, BUBBLE, SCATTER, PIE, TABLE,
  HEAT_MAP, DEFAULT, TABLE_V, STACKED_AREA, STACKED_COLUMN, TABLE_H` (as `ChartType` of the Control Center). A row
  with `DEFAULT` uses the type of its chart.
- `key` identifies a row within the analysis (used in the data answer).
- `bubbleType`: role of the row in a bubble chart, `X`, `Y`, `SIZE` or `NONE`. Without a row marked `X` the first
  row is the x row.
- `orientation`: for table charts, `HORIZONTAL` or `VERTICAL` (as in the Control Center).
- Numbers that are not finite (NaN, infinity) are written as `null`.
- An analysis that does not exist, is no analysis or may not be read by the user answers `404`.
- `readable: false`: the user may not read the data object of the row; the row has no name, unit or data
  (`name` is `null`), only the key.
- Both formats of stored analyses are read: the analysis file (attribute "Analysis File") and the older JSON in
  the attributes "Data Model" and "Charts".

### `POST /web/analyses/{id}/data` – data for a time frame

Request:

```json
{
  "timeFrame": "LAST_7_DAYS",
  "from": null,
  "to": null,
  "aggregation": "DAILY",
  "manipulation": "NONE",
  "timeZone": "Europe/Berlin"
}
```

- `timeFrame`: `TODAY, YESTERDAY, LAST_7_DAYS, THIS_WEEK, LAST_WEEK, LAST_30_DAYS, THIS_MONTH, LAST_MONTH,
  THIS_YEAR, LAST_YEAR, THE_YEAR_BEFORE_LAST` or `CUSTOM` with `from`/`to` (ISO 8601). Resolved like the
  Control Center (`AnalysisTimeFrame`), incl. the workday start/end of the analysis or user.
- `aggregation`: `AggregationPeriod` (`NONE, MINUTELY, QUARTER_HOURLY, HOURLY, DAILY, WEEKLY, MONTHLY, QUARTERLY,
  YEARLY, ...`), `manipulation`: `ManipulationMode` (`NONE, AVERAGE, SUM, MIN, MAX, MEDIAN, RUNNING_MEAN, ...`).
- `timeZone`: IANA time zone of the browser; used for the time frame and the aggregation (the Control Center uses
  the time zone of the computer). Unknown zones fall back to the time zone of the server.
- Unknown time frames fall back to `TODAY`, unknown aggregations and manipulations to `NONE`. `CUSTOM` without a
  valid `from`/`to` (or `to` before `from`) answers `400`.
- Calculated rows are aggregated in the time zone of the server (as the calculation service does).

Answer:

```json
{
  "from": "2026-08-31T00:00:00.000+02:00",
  "to": "2026-09-06T23:59:59.000+02:00",
  "charts": [
    {
      "id": 0,
      "rows": [
        {
          "key": "0-2001-Value",
          "status": "OK",
          "unit": "kWh",
          "samples": [[1788127200000, 120.5, null], [1788213600000, 80.25, "note"]],
          "stats": { "min": 80.25, "max": 120.5, "avg": 100.375, "sum": 200.75, "count": 2 }
        }
      ]
    }
  ]
}
```

- `from`/`to`: the resolved time frame; `to` is inclusive (the last full second of the workday, or now for
  time frames that include today).
- `status`: `OK`, `NO_DATA` (no samples in the time frame), `NO_PERMISSION`, `ERROR` (then `message`).
- `samples`: `[epoch milliseconds, value, note]`; `value` is a number, or a string for text data rows.
- `pieValue`: only for rows of pie charts, the value of the slice as in the Control Center (`PieChart`: samples
  without aggregation, summed for quantities, scaled to the sum unit for e.g. kW, else averaged). These rows have no
  `samples` (a year of 15-minute values would be several MB).
- `stats`: over the samples of the row, numbers only; `sum` only for quantities (like the Control Center's
  table below the chart), otherwise `null`. For calculated rows `avg` is the calculation over the whole time frame
  (like the Control Center), so it can lie outside min/max.
- Values are already converted to the unit of the row (unit prefix of the analysis), aggregated, manipulated
  and day-filtered exactly like in the Control Center (`ChartDataRow.getSamples`). Calculated rows are computed
  on the server (`CalcJobFactory`).
