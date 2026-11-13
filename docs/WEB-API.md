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

## Dashboards (W3)

View only. Widgets that write values in the Control Center (Value Editor, Toggle Switch, Slider, PlusMinus) are
delivered with their latest value and shown read-only.

### `GET /web/dashboards/{id}` – configuration

```json
{
  "id": 1002,
  "name": "Overview",
  "names": { "de": "Übersicht" },
  "width": 1863,
  "height": 886,
  "zoom": "FIT_WIDTH",
  "background": { "color": "#f4f4f4", "image": true, "mode": "DEFAULT" },
  "updateRate": 900,
  "timeFrame": "P1D",
  "timeFrames": [
    { "id": "P1D", "custom": false },
    { "id": "2005", "name": "Last 4 weeks", "custom": true }
  ],
  "widgets": [
    {
      "uuid": "7",
      "type": "Value",
      "x": 25, "y": 50, "width": 200, "height": 120, "layer": 0,
      "title": "Electricity", "tooltip": "",
      "style": {
        "background": "#ffffff", "fontColor": "#1a2a45", "fontSize": 13, "fontWeight": "BOLD", "italic": false,
        "underline": false, "titlePosition": "TOP_LEFT", "borderSize": 1, "borderColor": "#d0d4da",
        "cornerRadius": 4, "shadow": false
      },
      "decimals": 2, "decimalsAuto": false, "showValue": true,
      "timeFrame": null,
      "rows": [ { "key": "0-2001-Value", "objectId": 2001, "attribute": "Value", "name": "Meter A", "color": "#00538a",
                  "unit": "kWh", "decimalDigits": -1, "calculation": false, "readable": true } ],
      "chart": null,
      "config": { }
    }
  ]
}
```

- `zoom`: `FIT_WIDTH`, `FIT_HEIGHT`, `FIT_SCREEN` (stored as 98, 97, 99) or a number (factor, e.g. 1.0).
- `background.mode`: `DEFAULT` (top left, native size), `REPEAT`, `STRETCH`; the image comes from
  `GET /web/dashboards/{id}/background` (only if `image` is true).
- `timeFrame`: time frame of the dashboard ("Data Period"); `timeFrames`: the time frames a user can choose
  (`P1D, P1W, P1M, P1Y, P3Y, P5Y, P10Y, PT24H, P7D, P30D, P365D, PALL, CUSTOM` and the visible custom periods of
  the server sorted by name, `id` = object id, `name` = object name; `name` only for custom periods). A missing
  stored time frame becomes `P1D`; an unknown one is replaced by the data request (see below).
- Colors are CSS colors (`#rrggbb` or `rgba(r,g,b,a)`); Control Center colors `0xRRGGBBAA` are converted.
- `titlePosition`: JavaFX `Pos` names (`TOP_LEFT, TOP_CENTER, TOP_RIGHT, CENTER_LEFT, CENTER, CENTER_RIGHT,
  BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT`); legacy `left`/`right` become `CENTER_LEFT`/`CENTER_RIGHT` (as in the
  Control Center's `WidgetPojo`). `fontWeight`: JavaFX `FontWeight` names (`THIN` ... `NORMAL`, `BOLD` ... `BLACK`).
- `uuid` and all references to other widgets (`limit.source`, `percent.source`, `source` of `Time Frame`) are
  strings; "no source" is `null`. Widgets of old dashboards without uuid get the next free one.
- Widgets are sorted by `layer` (then their order in the file); a higher layer is drawn on top.
- `timeFrame` of a widget: its own fixed time frame (`forcedInterval` of the data handler, e.g. `LASTVALUE`,
  `P1M`), otherwise `null` (the dashboard's).
- `rows`: data rows of the widget, same fields and rules as the rows of an analysis (unreadable rows have
  `readable: false` and no name or unit). Except for `Chart` widgets, `decimalDigits` is the effective number of
  decimal places like `Widget.getDecimals` of the Control Center: the row's own setting if set, else (with
  `decimalsAuto`) those of the data object (`ValueFormat`: data object or its measuring instrument, default 2),
  else the widget's `decimals`; `-1` only for unreadable rows.
- `chart`: only for `Chart` widgets: one chart like `charts[0]` of `GET /web/analyses/{id}` (type, axes, rows).
- `type` and `config` (type specific, normalised):

| `type` | `config` |
|---|---|
| `Title` | `{}` (text = `title`) |
| `Value` | `{ "limit": { "source": uuid\|null, "upper": {"enabled", "value", "color", "offset"}, "lower": {…} }\|null, "percent": { "source": uuid, "diff": bool, "minFractionDigits", "maxFractionDigits" }\|null, "dimIncompletePeriod": bool }` |
| `String Value` | `{}` |
| `Table`, `Pie`, `Chart` | `{}` |
| `Gauge` | `{ "minimum", "maximum", "inPercent", "showTitle", "showUnit", "showValue", "sections": [{"start","end","color"}] }` |
| `LinearGauge` | `{ "minimum", "maximum", "inPercent", "color", "borderColor", "majorTickStep", "showTitle", "showUnit", "showValue" }` |
| `Battery` | `{ "minimum", "maximum", "color", "showValue" }` |
| `Shape` | `{ "shape": "RECTANGLE"\|"ELLIPSE", "minColor", "maxColor", "minValue", "maxValue" }` |
| `Arrow` | `{ "orientation": "LEFT_RIGHT"\|"RIGHT_LEFT"\|"TOP_BOTTOM"\|"BOTTOM_TOP", "shape": "ARROW"\|"LINE" }` |
| `NetGraph` | `{ "minimum": 0, "maximum", "inPercent", "rows": [{"key", "minimum", "maximum"}] }` (`maximum` = the stored global maximum, default 35; the Control Center stores min/max per data row, used with `inPercent`) |
| `Sankey` | `{ "showValue", "showPercent", "nodes": [{"key", "children": [key]}] }` (keys of `rows`) |
| `Time Frame` | `{ "source": uuid\|null, "start": "PERIOD_FROM"\|"NONE", "end": "PERIOD_UNTIL"\|"LAST_TS"\|"NONE", "format": pattern, "count": bool }` |
| `Alarm Count` | `{ "objectId": id\|null }` |
| `Image` | `{ "objectId": id\|null, "link": link\|null }` (image from `GET /web/objects/{objectId}/image`; `link` has the same shape as for the link widgets) |
| `Analyse-Link`, `Dashboard-Link`, `Report-Link` | `{ "link": { "kind": "ANALYSIS"\|"DASHBOARD"\|"REPORT", "id": id, "timeFrame": id\|null, "readable": bool } }` |
| `Slider` | `{ "minimum", "maximum" }` (as far as set), shown read-only |
| `PlusMinus` | `{ "increment" }` (as far as set), shown read-only |
| `Value Editor`, `Toggle Switch` | `{}`, shown read-only |

Unknown widget types are delivered with their `type` and an empty `config` and are shown as an empty frame.

### `POST /web/dashboards/{id}/data` – values for a time frame

Request:

```json
{ "timeFrame": "P1W", "date": "2026-11-06T12:00:00+01:00", "from": null, "to": null, "timeZone": "Europe/Berlin" }
```

- `timeFrame`: one of `timeFrames` (custom periods by id), also `LASTVALUE`; `CUSTOM` needs `from`/`to` (else
  `400`). Unknown time frames fall back to the dashboard's, then to `P1D`; the answer's `timeFrame` is the one used.
- `date`: reference time of the period (the period containing it, or ending at it for the rolling ones);
  `null` = now; dates in the future count as now; an invalid date answers `400`. The answer contains
  `previous`/`next` reference times for the arrows of the time frame bar: the date moved by the length of the time
  frame (`P3Y`: 3 years, `PT24H`: 24 hours, custom periods: the length of their interval); `next` is `null` if the
  current period already contains now (a later `next` is moved back to now). Both are `null` for `CUSTOM` (the front
  end moves the period by its own length), `PALL`, `LASTVALUE` and custom periods ending "now".
- `timeZone`: as for analyses.

Answer:

```json
{
  "timeFrame": "P1W",
  "from": "2026-11-02T00:00:00.000+01:00", "to": "2026-11-08T23:59:59.999+01:00",
  "previous": "2026-10-30T12:00:00.000+01:00", "next": null,
  "widgets": {
    "7": {
      "from": "2026-11-02T00:00:00.000+01:00", "to": "2026-11-08T23:59:59.999+01:00",
      "rows": [ { "key": "0-2001-Value", "status": "OK", "message": null, "unit": "kWh", "value": 1234.5,
                  "lastTimestamp": "…", "count": 672 } ]
    },
    "12": { "from": "…", "to": "…", "rows": [ { "key": "…", "status": "OK", "unit": "kW", "samples": [[…]], "stats": {…},
                                              "lastTimestamp": "…", "count": 168 } ] },
    "15": { "from": "…", "to": "…", "alarmCount": 3 },
    "16": { "from": "…", "to": "…", "rows": [ { "key": "…", "status": "OK", "text": "open", "lastTimestamp": "…", "count": 3 } ] }
  }
}
```

- Only widgets with data are listed (no `Title`, `Image`, `Arrow`, link and unknown widgets, no widgets without
  rows). Per widget: the resolved interval (`from`/`to`, its own fixed time frame if it has one; a fixed time frame
  of zero length, `LASTVALUE` or `PALL`, keeps the dashboard interval like the Control Center) and per row `status`
  (`OK, NO_DATA, NO_PERMISSION, ERROR`, as for analyses) and `message`.
- Rows are evaluated like `DataModelDataHandler`: with the aggregation/manipulation stored with the row (older
  dashboard files), no aggregation if the automatic aggregation of the interval (see below) is finer than the period
  of the row, rows aggregated daily or coarser are read for whole days; `LASTVALUE` reads each row at its last
  sample, `PALL` from its first to its last sample (also when `PALL` is the dashboard's time frame, where the Control
  Center would load nothing), and with a fixed `LASTVALUE`/`PALL` only the newest sample is kept.
- `value` (all data widgets except `Chart` and `String Value`): one value for the interval, like the Control
  Center's widgets (absolute mode of `ChartDataRow`: sum for quantities, the calculation over the interval for
  calculated rows, the manipulation result otherwise); `LASTVALUE` = the newest sample. `lastTimestamp`: time of
  the newest sample in the interval (`Time Frame` widgets: the latest sample of the attribute, as the Control
  Center shows it). `count`: number of samples in the interval (before they are reduced to one value).
- `Chart` widgets get `samples`/`stats` like the rows of an analysis, with the Control Center's automatic
  aggregation (rows without own aggregation: under 6 days none, under 27 days hourly, under 364 days daily,
  otherwise weekly; not for heat maps and not for rows with a rate unit such as kW), plus `lastTimestamp` and
  `count`.
- `String Value`: `text` = newest sample in the interval (for a numeric row its absolute value as text).
- Input widgets (`Value Editor`, `Toggle Switch`, `Slider`, `PlusMinus`): `value` (or `text` if not numeric) and
  `lastTimestamp` of the latest sample of the attribute, independent of the time frame (the Control Center's
  `forceLastValue`), not converted; `unit` = display unit of the attribute; no `count`.
- `Alarm Count`: `alarmCount` = number of alarms in the dashboard interval (JSON alarm logs of the enabled alarm
  configurations at or below `objectId`, all if `null`); no `rows`.
- Percent and limit colors (references to other widgets) are computed in the browser from these values.

### `GET /web/dashboards/{id}/background`, `GET /web/objects/{id}/image`

The background image of the dashboard (attribute "Background") and the image of an Image widget (latest sample of
the attribute "File", otherwise of the first file attribute holding an image), with its content type; 404 if there
is none, it is no image (PNG, JPEG, GIF, BMP, WebP, SVG, recognised by content) or the user may not read the object.
`Cache-Control: private, max-age=300`, `X-Content-Type-Options: nosniff`; SVG additionally with a restrictive
`Content-Security-Policy`.

### Start dashboard

`GET /web/navigation` additionally answers `"startDashboard": id|null`: the user's attribute "Start dashboard"
if the user may read it (for a Dashboard Collection: the first readable dashboard of its attribute "Dashboards").

### Time frames of dashboards

As the Control Center's `TimeFrameFactory`, in the time zone of the request: `P1D` the day (no workday), `P1W`
Monday to Sunday, `P1M`, `P1Y`, `P3Y`/`P5Y`/`P10Y` the last 3/5/10 full years (the Control Center's `P10Y` covers
only 5 years, fixed here), all except `P1D` with the workday of the dashboard's building (a workday over midnight
starts the day before); `PT24H`, `P7D`, `P30D`, `P365D` end at the reference date; custom periods are resolved by
`DateHelper` (for a widget with "fixed time frame": for now instead of the reference date).

## Appointments and maintenance (W4.1)

View only. Entries are objects of class "Appointment" / "Maintenance Message" (JSON in their attribute "Data",
see `org.jevis.commons.appointment.Appointment` and `org.jevis.commons.maintenance.MaintenanceEntry`) in
"Appointment Directory" / "Maintenance Directory" folders. Only entries the user may read are returned.

### `GET /web/appointments`

```json
{
  "today": "2026-11-06",
  "appointments": [
    {
      "id": 3001, "name": "Calibration main meter",
      "site": { "id": 1001, "name": "Plant", "names": {} },
      "directory": { "id": 3000, "name": "Appointments" },
      "title": "Calibration main meter", "description": "…",
      "due": "2026-11-02T09:00", "nextDue": "2027-11-02T09:00",
      "recurrence": "YEARLY", "anchorDay": 2, "reminderDays": 3, "reminderDay": "2026-10-30",
      "status": "OPEN", "overdue": true, "dueSoon": false, "lastDone": null,
      "linkedObject": { "id": 2001, "name": "Main meter", "readable": true },
      "recipients": { "count": 2, "names": ["Energy team"] },
      "lastMail": "2026-10-31T00:33:51",
      "created": "2026-10-31T00:31:49", "createdBy": "Admin"
    }
  ]
}
```

- Times are local date-times of the server (`yyyy-MM-ddTHH:mm`) as stored in the entries; `today` is the server's
  date used for `overdue`/`dueSoon` (`AppointmentLogic.isOverdue`/`isDueSoon`).
- `site`: nearest readable organisation or building above the entry (as in the navigation), `null` if none.
- `directory`: the folder the entry lies in, `null` if the user may not read it.
- Order: by `due` (entries without due date last), then title.
- `recurrence`: `NONE, MONTHLY, QUARTERLY, YEARLY` (`Appointment.Recurrence`);
  `nextDue`: the due date after this one for recurring entries (`AppointmentLogic.nextDue`), else `null`.
- `linkedObject`: `null` if none; without read right only `{ "id", "readable": false }`.
- `recipients.names`: names of the recipients (users/groups) the user may read; `count` = all recipients.
- `lastMail`: from the attribute "Mail State" (`MailState`), `null` if none.
- Entries whose JSON cannot be read are listed with `title` = object name and `status: null` (all other entry
  fields `null`, `overdue`/`dueSoon`/`active` `false`; also for maintenance).

### `GET /web/maintenance`

```json
{
  "now": "2026-11-06T10:15",
  "entries": [
    {
      "id": 3101, "name": "Server update",
      "site": null, "directory": { "id": 3100, "name": "Maintenance" },
      "type": "MAINTENANCE", "title": "Server update", "description": "…", "impact": "…",
      "start": "2026-11-06T18:00", "end": "2026-11-06T18:30", "durationMinutes": 30,
      "status": "PLANNED", "active": true,
      "recipients": { "count": 1, "names": [] },
      "informRequested": "2026-10-31T00:32:52", "doneAt": null, "lastMail": "…",
      "created": "…", "createdBy": "Admin"
    }
  ]
}
```

- `type`: `MAINTENANCE` or `KNOWN_ISSUE`; `status`: `PLANNED, IN_PROGRESS, DONE`; `end` = `start` + duration
  (`null` without duration); `active` = status `PLANNED` or `IN_PROGRESS` (shown as banner in the web app).
- `site`, `directory`, `recipients`, `lastMail` as for appointments. Order: by `start` descending (newest first,
  entries without start last), then title.

### Navigation

`GET /web/navigation` additionally answers `"modules": { "appointments": n, "maintenance": n }` (number of readable
entries) so the menu only shows modules with entries.

## Action plans, nonconformities, legal register (W4.2)

View only. A plan object ("Action Plan v2", "NonconformityPlan", "Index of Legal Provisions") holds the plan
settings; its items ("Action", "Nonconformity", "Obligation") keep their content as JSON in the file attribute
"Data" (latest sample, written by the Control Center with Gson; keys with spaces, legacy spellings like
`Desciption`, `EnpI`, `No`/`Nr`). Items marked `"Deleted": true` are not returned. Only plans and items the
user may read are returned. Dates are ISO 8601 with offset as stored (`null` if not set).

### `GET /web/actionplans`

```json
{
  "today": "2026-11-06",
  "plans": [
    {
      "id": 4001, "name": "Action plan", "site": { "id": 1001, "name": "Plant", "names": {} },
      "prefix": "AP", "statuses": ["Offen", "Erledigt"], "fields": ["Produktion"], "seus": ["Lighting"],
      "media": [ { "id": "Strom", "name": "Strom", "co2": 0.366 } ],
      "actions": [
        {
          "id": 4101, "no": 3, "number": "AP 003",
          "title": "…", "description": "…", "note": "…", "fromUser": "…", "responsible": "…", "distributor": "…",
          "createDate": "…", "plannedDate": "…", "doneDate": "…",
          "status": "Offen", "field": "Produktion", "medium": "Strom", "mediumName": "Strom", "seu": "",
          "correction": "…", "followAction": "…", "alternativeMeasures": "…",
          "affectedProcessNote": "…", "energyFlowNote": "…", "evaluationNote": "…",
          "npv": { "interestRate": 10.0, "investment": 1000.0, "saving": 300.0, "runningCost": 0.0, "inflation": 3.1,
                   "years": 5, "duration": 3, "result": 123.4, "profitabilityIndex": 1.12, "resultOverX": 1.0,
                   "profitabilityIndexOverX": 1.0, "sumDeposits": 1500.0, "sumPayouts": 1000.0, "sumNet": 500.0,
                   "yearRows": [ { "year": 1, "deposit": 309.3, "cost": 0.0, "net": 309.3, "discounted": 281.2 } ] },
          "consumption": { "before": 100.0, "after": 80.0, "diff": 20.0, "unit": "kWh", "objectId": 2001,
                           "beforeFrom": "…", "beforeUntil": "…", "afterFrom": "…", "afterUntil": "…" },
          "enpi": { "…": "same fields as consumption" },
          "checkList": { "processDocument": false, "…": "booleans as stored, plus newEnPI (text)" },
          "daysSinceDone": 120, "savingsSinceDone": 6.58, "attachments": 1
        }
      ]
    }
  ]
}
```

- `number` = `prefix` + no (3 digits) like the Control Center; `mediumName` = name of the medium id in `media`
  (old plans with a `;` list: name = id, CO₂ factor 0.4).
- `npv`: the stored inputs; `yearRows` and all results are computed from them like `NPVData.updateResults`
  (`deposit = saving·(1+inflation)^y`, `cost = runningCost·(1+inflation)^y`, `net`, `discounted = net/(1+rate)^y`;
  `result = -investment + Σ discounted`, `…OverX` over the first `duration` years, `sumDeposits` = Σ deposit,
  `sumPayouts` = investment + Σ cost, `sumNet`). The results stored in the file are not used: the Control Center only
  recomputes them when an input is edited, so they can be outdated. (The stored keys "Sum Investment"/"Sum Payout"
  hold the deposits/payouts.)
- `consumption`/`enpi`: stored values (not recomputed); `unit` from the data object (attribute `Unit` or the display
  unit of `Value`), `null` if not readable; `null` if not set.
- `daysSinceDone` (from `doneDate` to `today`, `null` without done date), `savingsSinceDone` =
  `daysSinceDone · consumption.diff / 365` like the Control Center. The figures above the list (sums, gross,
  per medium, CO₂ = `co2 · kWh / 1000`, savings since a date, count per status) are computed in the browser from
  the filtered actions.
- `attachments`: number of readable attachments (see below).

- `statuses`: the plan's "Custom Status" list plus every status used by its actions (the Control Center also adds
  its translated "done" status; the server does not know the language). `mediumName` falls back to the stored
  medium value if the id is not in `media`. Numbers that are NaN/Infinity are `null`; `npv`, `consumption`,
  `enpi` are `null` if not stored.
- `checkList` keys: `processDocument, workInstruction, testInstruction, drawing, other, additionalAction,
  affectsOthers, consumptionDocumented, targetReached, documentCorrectionText, documentCorrection, additionalMeter`
  (booleans) and `newEnPI` (text).
- Items whose "Data" file is missing or unreadable are left out (as in the Control Center), as are plans, items and
  files in the recycle bin.

### `GET /web/nonconformities`

```json
{
  "plans": [
    {
      "id": 4201, "name": "Nonconformities", "site": null, "prefix": "NC",
      "media": ["Strom", "Gas"], "fields": ["…"], "seus": ["…"],
      "items": [
        {
          "id": 4301, "nr": 1, "number": "NC 001", "title": "…", "creator": "…", "responsible": "…",
          "description": "…", "cause": "…", "immediateMeasures": "…", "correctiveActions": "…", "action": "…",
          "medium": "Strom", "fields": ["…"], "seu": "…",
          "createDate": "…", "plannedDate": "…", "doneDate": null, "open": true, "overdue": false,
          "checkList": { "immediateActionRequired": true, "…": "booleans as stored" },
          "warnings": ["IMMEDIATE_MEASURES_MISSING"], "attachments": 0
        }
      ]
    }
  ]
}
```

- `open` = no done date; `overdue` = open and planned date before today.
- `warnings` (checks of the Control Center): `IMMEDIATE_MEASURES_MISSING` (immediate action required but no
  measures), `DONE_WITHOUT_ACTION`, `DONE_IN_FUTURE`.

- `number`: prefix + number with 3 digits (the Control Center shows the number without padding).
- `checkList` keys: `immediateActionRequired, effectOnOngoingProcesses, routinelyAffected, employeeTrained,
  documentsChangesNeeded, processInstructions, workInstructions, testInstructions, design, model, miscellaneous,
  metrics`. `warnings` lists every check that applies (the Control Center shows the first one).

### `GET /web/legal`

```json
{
  "plans": [
    {
      "id": 4401, "name": "Legal register", "site": null, "categories": ["…"], "scopes": ["…"],
      "obligations": [
        {
          "id": 4501, "uuid": "…", "nr": 72, "title": "…", "designation": "…", "description": "…",
          "importance": "…", "link": "https://…", "category": "…", "scope": "…", "relevant": true,
          "issueDate": "…", "currentVersionDate": "…", "examinationDate": "…", "attachments": 0
        }
      ]
    }
  ]
}
```

- `link` only if it is an `http(s)` URL, otherwise `null`.

### Attachments

- `GET /web/objects/{id}/attachments` → `[{ "id": 4601, "name": "offer.pdf", "size": 12345, "date": "…" }]`: the
  "File" child objects of a readable item (latest sample of their attribute "File").
- Only for items of class Action, Nonconformity or Obligation (others: 404); `size` is `null` if the stored file is
  missing; `date` is the sample time (ISO 8601). Tag values of old plans may be `;` lists (e.g. `field`), the web
  app matches each part.
- `GET /web/files/{id}` → the file (content type from the name, `Content-Disposition: attachment`,
  `X-Content-Type-Options: nosniff`, `Cache-Control: private, no-store`); 404 if missing or not readable.

### Navigation

`modules` additionally holds `actionPlans`, `nonconformities` and `legal` (number of readable plans).

## Meters, base data, equipment (W4.3)

View only. Tables over the native attributes of the objects, like the Control Center's plugins "Meter Plugin",
"Base Data Plugin" and "Equipment Plugin". Only objects the user may read are returned.

### `GET /web/registers/{kind}?lang=de`

`kind`: `meters`, `basedata`, `equipment`. `lang`: language of class and column names (from `var/i18n`, fallback
English, then the technical name).

```json
{
  "kind": "meters",
  "today": "2026-11-11",
  "groups": [
    {
      "key": "5001", "name": "Plant", "site": { "id": 1001, "name": "Plant", "names": {} },
      "columns": [
        { "key": "Serial Number", "name": "Seriennummer", "type": "TEXT", "unit": null, "position": 12, "visible": true }
      ],
      "rows": [
        {
          "id": 5101, "name": "Main meter", "className": "Electricity Measurement Instrument",
          "classLabel": "Stromzähler", "path": "Plant / Meters", "medium": "ELECTRICITY",
          "values": { "Serial Number": "A-123", "Installation Date": "2015-10-01", "Picture": { "file": "meter.jpg", "image": true } },
          "target": { "id": 2001, "attribute": "Value", "name": "Main meter data", "readable": true },
          "last": { "value": 12345.6, "unit": "kWh", "timestamp": "2026-11-10T23:45:00.000+01:00", "decimals": 1 },
          "verification": { "due": "2026-12-31", "overdue": false }
        }
      ]
    }
  ]
}
```

- `groups` (tabs as in the Control Center):
  - meters: one per "Measurement Directory" (`name` = its building, else the directory name);
  - basedata: one per parent folder of the "Base Data" objects (`name` = folder name, with `site`);
  - equipment: one per equipment class ("Building Equipment" and subclasses; `name` = translated class name).
- `columns`: meters: union of the attribute types of the meters in the group; equipment: the types of the class;
  basedata: none (fixed columns name, path, last value, timestamp). Sorted by GUI position. `type`: `TEXT, NUMBER,
  INTEGER, BOOLEAN, DATE, DATETIME, FILE, TARGET, TEXTAREA`; `unit` for numbers with unit; `visible`: shown by
  default (meters: the Control Center's default list; equipment: all).
- `values`: latest sample per attribute key, typed (number, boolean, string, date `yyyy-MM-dd`); file attributes as
  `{ "file": name, "image": bool }` (download below; `image` from the file name); targets as `{ "id", "attribute",
  "name", "readable" }`; passwords never; missing or empty values are absent (the key is left out). `values` holds
  every attribute of the row's class (all columns of the group, not only the visible ones), e.g. `Type`, `Location`.
  Dates are given as `yyyy-MM-dd` (also for `DATETIME`); values that cannot be converted are given as text.
- meters:
  - `medium`: `ELECTRICITY, GAS, WATER, HEAT, AIR, COMPRESSED_AIR, NITROGEN` (the classes "Electricity/Gas/Water/
    Heat/Air/Compressed-Air/Nitrogen Measurement Instrument" and their subclasses, like the Control Center's
    `MediumColumn`), `null` for all other meter classes;
  - `target`: the "Online ID" (`id:Attribute`, first target), `null` if not set; name only if readable;
  - `last`: latest sample of the target (value, display unit, timestamp, decimals = "Decimal Places" of the meter,
    else of the target), `null` if none or not readable;
  - `verification`: always an object for meters (absent for base data and equipment); `due`: attribute
    "Verification Date" (next due date), `null` if not set; `overdue` = due before `today` (`false` without date).
  - Groups: also directories without meters; rows sorted by name; meters below meters and in subfolders are part of
    the group of the directory (only through objects the user may read).
- basedata: `last` = `{ "value", "unit", "timestamp", "period" }`: latest sample of "Value" with display unit;
  `period` = attribute "Period" of the object (ISO period, e.g. `P1M` → format the timestamp as month like the
  Control Center), `null` if not set; `last` is `null` without sample.
- equipment: `site` of the groups is `null` (a class spans several sites; see `path` of the rows).

### `GET /web/registers/objects/{id}/history?limit=12`

The last samples (newest first) of a base data object's "Value", or of a meter's target (its "Online ID"; attribute
`Value` if the Online ID names an object only). `{ "unit": "kWh", "samples": [ { "timestamp": "…", "value": 12345.6,
"note": "Meter Tausch" } ] }`. `limit`: default 12, at most 100. A meter without Online ID answers `{ "unit": null,
"samples": [] }`; 404 if the object (or the meter's target) is not readable or is no meter/base data object.

### `GET /web/registers/objects/{id}/files/{attribute}`

The latest file of a file attribute (picture, datasheet, verification documentation) of a readable object.
`{attribute}` is the attribute name, URL-encoded (`Verification%20Documentation`), matched without case; only file
attributes of readable meters, base data and equipment objects (others: 404). `?inline=true` only for PNG, JPEG,
GIF, WebP and BMP detected from the bytes (content type image/*, `Content-Disposition: inline`); everything else
(also SVG) as attachment; nosniff, `Cache-Control: private, no-store`.

### `GET /web/registers/meters/{id}/qr.svg`

The meter's QR code as SVG, same text as the Control Center's labels (`MeterQrCode.payload`: meter id, Online ID
target, server). The server address is taken from the request (`X-Forwarded-Proto`/`X-Forwarded-Host` behind a
proxy, else the request URL), e.g. `https://sunandcharge.app:443`. The target is only part of the code if the
user may read it. `Cache-Control: private, max-age=300`; 404 if the meter is not readable.

### Navigation

`modules` additionally holds `meters`, `basedata`, `equipment` (number of readable objects of the meter classes,
"Base Data", equipment classes; not in the recycle bin).

## Notes (W4.4)

View only, like the Control Center's "Notes Plugin". A note is one timestamp of a "Data Notes" object (a child of a
data row): sample of `Value` (text) plus the samples of `Tag` and `User` at the same timestamp. Only notes of
"Data Notes" objects the user may read (and that are not in the recycle bin) are returned.

### `GET /web/notes?from=2026-08-11&to=2026-11-11&timeZone=Europe/Berlin&lang=de`

- `from`/`to`: days (`yyyy-MM-dd`, both inclusive) in `timeZone` (IANA, fallback: time zone of the server).
  Default: the last 3 months up to today. `to` before `from` or more than 3 years answers `400`.
- `lang`: language of the tag names (fallback English).

```json
{
  "from": "2026-08-11T00:00:00.000+02:00",
  "to": "2026-11-11T23:59:59.999+01:00",
  "tags": [ { "id": "4", "name": "Ereignis" } ],
  "notes": [
    {
      "id": "23685-1793700900000",
      "timestamp": "2026-11-03T09:15:00.000+01:00",
      "text": "Lastspitze durch Anlauf Kompressor 2",
      "tags": ["4"],
      "user": "Demo",
      "notesObject": 23685,
      "row": { "id": 23672, "name": "Strom - Gesamt", "className": "Data", "path": "Daten / Strom", "readable": true },
      "site": { "id": 23053, "name": "Musterwerk" }
    }
  ],
  "truncated": false
}
```

- `notes`: newest first, at most 5000 (`truncated: true` if there are more in the time frame).
- `id`: `<notesObject>-<epoch milliseconds>`, unique per note.
- `tags`: the tag ids of the note. The `Tag` sample holds a `;`-separated list of tag ids (`0`–`13`) or, in old
  notes, tag names in any language; both are mapped to the ids like the Control Center's `NoteTag.parseTags`.
  Unknown texts are kept as their own id (the text itself) and listed in `tags` of the answer with `name` = text.
- top-level `tags`: all tags used in the answer plus the 14 standard tags (`0` none, `1` audit, `2` task,
  `3` report, `4` event, `5` reminder, `6` error, `7` question, `8` service, `9` characteristic, `10` comment,
  `11` measure, `12` disturbance, `13` maintenance, as the Control Center's `NoteTag`),
  names translated (`plugin.notes.tags.*`).
- `user`: sample of `User` at the timestamp, `null` if none. `text`: `""` if the `Value` sample is empty.
- `row`: the parent of the "Data Notes" object (the data row); `name`, `className` and `path` only if readable
  (`readable: false` otherwise, then the note is still returned). `path`: names below the building (not included,
  see `site`) down to the parent of the row, separated by ` / `, up to the first parent the user may not read;
  `null` if empty.
- `site`: the next readable "Building" (or subclass) above the row (`{ "id", "name" }`), `null` if there is none.

### `GET /web/rows/{id}/samples?from=…&to=…`

Raw samples of the `Value` attribute of a readable data row (any class with a `Value` attribute: Data, Clean Data,
Math Data, ...), for the curve around a note. `from`/`to`: ISO 8601 date-times, at most 31 days (plus one hour for the switch to winter time) apart, else `400`.

```json
{ "unit": "kWh", "samples": [[1793696400000, 12.5], [1793697300000, 13.1]], "truncated": false }
```

- `samples`: `[epoch milliseconds, value]`, oldest first, values in the display unit (like the analysis), at most
  5000 (`truncated`). Text rows: value as string. 404 if the row is not readable or has no `Value` attribute.

### Notes in analyses

`POST /web/analyses/{id}/data`: every row (not the pie rows) additionally has `notes`, the notes within the
resolved time frame of the "Data Notes" objects below the row's object and — if the row is a Clean Data row — below
its raw data parent, if the user may read them: `[{ "timestamp": epoch milliseconds, "text": "…", "tags": ["4"],
"user": "Demo" }]` (oldest first, at most 200 per row; `[]` if none). The web app shows them as markers on the
curve (tooltip with text, tags, user).

### Navigation

`modules` additionally holds `notes`: number of readable "Data Notes" objects (not in the recycle bin).

## Alarms (W4.5)

View only, like the Control Center's "Alarm Plugin" (`plugin/alarms/AlarmPlugin`, `AlarmRow`); alarms are
confirmed in the Control Center. JEAlarm writes the alarms it finds as JSON files (`JsonAlarm[]`) into the attribute
`Log File` of an "Alarm Configuration"; one file sample per run. An alarm is one entry of such a file. Only
configurations the user may read, that are enabled (`Enabled`) and not in the recycle bin count.

### `GET /web/alarms?from=2026-11-04&to=2026-11-11&state=open&timeZone=Europe/Berlin&lang=de`

- `from`/`to`: days (`yyyy-MM-dd`, both inclusive) in `timeZone`. Default: the last 7 days up to today. `to` before
  `from` or more than 1 year answers `400`, as does an unknown `state`. Like the Control Center, the time frame selects the **log file samples**
  (when JEAlarm logged the alarms); every alarm of a selected file is returned, also if its own timestamp is older.
- `state`: `open` (default; configurations whose latest `Alarm Checked` is not true), `checked` or `all`. Confirmation
  is per configuration (latest sample of `Alarm Checked`), as in the Control Center.
- `lang`: language of the type and log value names (fallback English).

```json
{
  "from": "2026-11-04T00:00:00.000+01:00",
  "to": "2026-11-11T23:59:59.999+01:00",
  "state": "open",
  "alarms": [
    {
      "id": "24202-1793700900000-0",
      "timestamp": "2026-11-10T14:15:00.000+01:00",
      "logged": "2026-11-10T23:43:00.000+01:00",
      "configuration": { "id": 24202, "name": "Strom - Gesamt - Bereinigte Daten", "checked": false, "checkedBy": null, "checkedAt": null },
      "row": { "id": 23673, "name": "Strom - Gesamt - Bereinigte Daten", "className": "Clean Data", "path": "Daten / Strom / Strom - Gesamt", "readable": true },
      "site": { "id": 23053, "name": "Musterwerk" },
      "attribute": "Value",
      "isValue": 812.5,
      "operator": ">",
      "shouldBeValue": 700.0,
      "tolerance": 5.0,
      "unit": "kWh",
      "type": "L1",
      "typeName": "Grenzwert Stufe 1",
      "logValue": 1,
      "logValueName": "Normaler Alarm"
    }
  ],
  "truncated": false
}
```

- `alarms`: newest `timestamp` first, at most 5000 (`truncated: true` if there are more).
- `id`: `<configuration>-<epoch milliseconds of the alarm>-<index in the file>`, unique per alarm.
- `logged`: timestamp of the log file sample.
- `configuration.checkedBy`/`checkedAt`: note and timestamp of the latest `Alarm Checked` sample if it is true
  (the Control Center writes the note "Checked by <account>"; `checkedBy` is then only `<account>`, other notes
  unchanged, `null` without note), else `null`.
- `row`: the object of the alarm (`JsonAlarm.object`), same shape and rules as the notes' `row` (`name`,
  `className`, `path` only if readable). `site`: next readable building above the row, else above the
  configuration, else `null`.
- `isValue`, `shouldBeValue`, `tolerance`: as logged (`null` if missing). `unit`: display unit of the alarm's
  attribute (as `ResourceWebRegisters.unitLabel`), `""` if none or the row is not readable.
- `operator`: as logged, normalised like the Control Center's operator column: `>`, `≥` (also for `>=` and `""`),
  `=`, `≠` (`!=`), `<`, `≤` (`<=`); other texts unchanged.
- `type`: `AlarmType` (`L1, L2, D1, D2, DYNAMIC, STATIC, GAP, NONE`); `typeName` translated like
  `Alarm.getTranslatedTypeName` (`plugin.alarm.table.translation.*`).
- `logValue`: `0` system, `1` normal, `2` standby, `4` silent; `logValueName` translated like `AlarmPlugin.getAlarm`
  (`plugin.alarm.table.alarm.*`).
- Unlike the Control Center, an alarm is listed even if its row no longer has a sample at the alarm's timestamp.

### Row history

The web app shows the curve of the alarm's row one day before and after the alarm with `GET /web/rows/{id}/samples`
(see Notes), with the limit (`shouldBeValue`) as a line if present.

### Navigation

`modules` additionally holds `alarms`: number of readable, enabled "Alarm Configuration" objects, and
`alarmsOpen`: number of alarms in the log file samples of the last 7 days of those configurations that are not
confirmed (the menu badge).

## Reports (W4.6)

View only, like the Control Center's "Report Plugin" (`plugin/reports/ReportPlugin`): the reports' generated files
can be viewed and downloaded; reports are configured and run in the Control Center / by JEReport. A report is an
object of class "Report" or a subclass ("Periodic Report") that the user may read and that is not in the recycle
bin. JEReport writes one file sample per run into `Last Report PDF` (PDF) and `Last Report` (XLSX); an XLSX belongs
to the PDF written within one minute of it (as `ReportPlugin.loadReport`).

### `GET /web/reports`

```json
{
  "reports": [
    {
      "id": 23801,
      "name": "Monatsbericht Energie",
      "className": "Periodic Report",
      "path": "Berichte",
      "site": { "id": 23053, "name": "Musterwerk" },
      "enabled": true,
      "schedule": "MONTHLY",
      "latest": { "timestamp": "2026-11-01T06:00:12.000+01:00", "pdf": true, "xlsx": true, "name": "Monatsbericht Energie_202610.pdf" },
      "count": 24
    }
  ]
}
```

- `reports`: sorted by site name (reports without site last), then path (without path first), then name
  (case-insensitive, locale-aware), like the Control Center. Time stamps are in the server's time zone.
- `path`: names below the building/organisation down to the parent of the report (same rules as the notes'
  `row.path`), `null` if empty. `site`: next readable building above the report, else `null`.
- `enabled`: latest `Enabled` sample is true. `schedule`: latest `Schedule` sample as stored (`null` if none or not a
  periodic report).
- `latest`: newest PDF output (`timestamp` of its sample, `name` = file name, `xlsx` = an XLSX within one minute
  exists), `null` if the report has no PDF output yet (the Control Center shows such reports disabled).
- `count`: number of PDF outputs.

### `GET /web/reports/{id}/files`

All outputs of a readable report, newest first:

```json
{
  "id": 23801,
  "name": "Monatsbericht Energie",
  "files": [
    { "timestamp": "2026-11-01T06:00:12.000+01:00", "name": "Monatsbericht Energie_202610.pdf", "xlsx": "2026-11-01T06:00:11.000+01:00" }
  ]
}
```

- `timestamp`: of the PDF sample; `xlsx`: timestamp of the matching XLSX sample (`null` if none). 404 if the report
  is not readable or not a report.

### `GET /web/reports/{id}/file?format=pdf|xlsx&ts=<timestamp>`

The bytes of one output. `ts`: the PDF `timestamp` (format `pdf`) or the `xlsx` timestamp (format `xlsx`) from the
list, ISO 8601; without `ts` the newest; without `format` `pdf`. Content type `application/pdf` or
`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition` with the stored file name
(`inline` for PDF when `?inline=true`, else `attachment`), nosniff, `Cache-Control: private, no-store`, and for
attachments `Content-Security-Policy: default-src 'none'; sandbox` (not for inline PDFs, the browsers' PDF viewers
do not run in a sandbox). 404 if the
report is not readable or the output does not exist; 400 for an unknown `format` or an invalid `ts`.

### Dashboards

Report links of dashboards (`Report-Link` widgets, `link.kind` `REPORT`; image widgets cannot link reports) now lead to the report in the web app
(`/app/reports?id=<id>`) when the report is readable.

### Navigation

`modules` additionally holds `reports`: number of readable reports (not in the recycle bin).

## Contracts and energy invoices (W4.7)

View only, like the Control Center's "Accounting Plugin" (`plugin/accounting/AccountingPlugin`, tabs "Enter data"
and "Configuration"; class files in `var/classes/AccountingPlugin`). The cost calculation (tab "View",
TRC templates) follows in W4.8. Objects are found by class across the tree like the Control Center, only those the
user may read and not in the recycle bin.

### `GET /web/contracts?lang=de`

```json
{
  "groups": [
    {
      "kind": "suppliers",
      "objects": [
        {
          "id": 23901, "name": "Strom - Energieversorger Nord", "className": "Electricity Supply Contractor",
          "classLabel": "Stromlieferant", "path": "Energieverträge / Energielieferung",
          "site": { "id": 23053, "name": "Musterwerk" },
          "contractor": { "id": 23910, "name": "Energieversorger Nord", "readable": true },
          "attributes": [
            { "name": "Energy Price HT", "label": "Arbeitspreis HT", "type": "number", "value": 0.28, "unit": "€/kWh",
              "timestamp": "2026-01-01T00:00:00.000+01:00", "history": true },
            { "name": "Demand Charge", "label": "Leistungspreis", "type": "ranging",
              "value": [ { "from": 0, "to": 100, "value": 12.5 } ], "unit": "€/kW", "timestamp": "…", "history": true }
          ]
        }
      ]
    }
  ],
  "configurations": [
    { "id": 23950, "name": "Strom Musterwerk", "path": "Energieverträge / Abrechnung", "site": { "id": 23053, "name": "Musterwerk" },
      "contractNumber": "V-1001", "contractType": "ELECTRICITY", "marketLocation": "DE…", "contractDate": "2025-12-01",
      "firstRate": "2026-01-01", "periodOfNotice": "2027-09-30", "start": "2026-01-01", "end": "2027-12-31",
      "energyType": "ELECTRICITY" }
  ]
}
```

- `groups[].kind`, in this order: `suppliers` ("Energy Supplier" and subclasses), `meteringOperators` ("Energy
  Metering Point Operator" and subclasses), `gridOperators` ("Energy Grid Operator" and subclasses), `contractors`
  ("Energy Contractor" and subclasses), `dues` ("Governmental Dues"). Empty groups are left out. Objects sorted by
  site, path, name like the reports.
- `classLabel`: translated class name (`lang`, fallback English), `label`: translated attribute name, both from the
  class i18n (`var/i18n/AccountingPlugin`, as the Control Center shows them; fallback the name).
- `attributes`: all attributes of the class in the class's order, except `Contractor` (given as `contractor`, an
  object target "id" or "id:attribute" as `TargetHelper`; `null` if empty) and file attributes. `type`: `number`
  (Double/Long, `value` latest sample, `unit` display unit as `ResourceWebRegisters.unitLabel`), `ranging`
  (Ranging Value JSON parsed with `RangingValues`, `value` = list of `{from, to, value}`), `text` (everything else,
  latest value as string). `value`/`timestamp` `null` if no sample. `history`: more than one sample exists.
- `contractor`: `{ "id", "name", "readable" }` (`name` only if readable and not in the recycle bin), also `null` for
  classes without `Contractor`. `ranging`: `[]` if the JSON cannot be parsed. `unit` is `null` for `text`.
  Passwords are left out like files.
- `configurations`: readable "Accounting Configuration" objects, sorted like the objects; the contract fields come from
  the JSON file of `Template File` (a file attribute; `SelectionTemplate` / `AccountingTemplateHandler.toJsonNode`):
  `contractNumber`, `contractType`, `marketLocationNumber` → `marketLocation`, `contractDate`, `firstRate`,
  `periodOfNotice`, `contractStart` → `start`, `contractEnd` → `end`. The Control Center stores the dates (also
  `periodOfNotice`, a date picker there) as ISO date times; they are given as `yyyy-MM-dd`, texts that are no date
  unchanged. `contractType` holds the energy type (`ContractType`: `ELECTRICITY`, `GAS`, `COMMUNITY_HEATING`; the
  Control Center labels it "energy type"); `energyType` is the same value if it is one of these, else `null`.
  Missing/empty keys, a missing or unparseable file: `null`. The calculation inputs/outputs of the JSON are not
  returned.

### `GET /web/contracts/objects/{id}/history?attribute=Energy%20Price%20HT&limit=100`

Price history of one number or ranging attribute of a readable object of the classes above, newest first:
`{ "unit": "€/kWh", "type": "number", "samples": [ { "timestamp": "…", "value": 0.28 } ] }` (ranging: `value` =
list of `{from, to, value}`). `limit` 1–500, default 100 (`≤ 0` → 100, more than 500 → 500). 404 if the object is not readable / not such a class or
the attribute is unknown or not number/ranging.

### `GET /web/invoices?from=2025-11-01&to=2026-11-30&timeZone=Europe/Berlin`

Energy invoices (class "Energy Invoice", see `docs/ENERGY-INVOICES.md`) the user may read, whose `Period Start`
(else `Invoice Date`) lies in the time frame. `from`/`to` days; default: the last 24 months up to the end of the
current month (on 2026-11-11: `2024-12-01` to `2026-11-30`; only `to` given: the 24 months up to `to`); `to`
before `from` or more than 10 years → `400`.

```json
{
  "invoices": [
    {
      "id": 24601, "number": "EN-202610-S", "date": "2026-11-03", "format": "ZUGFeRD 2.3 EN16931",
      "seller": "Energieversorger Nord", "buyer": "Sun and Charge Demo",
      "periodStart": "2026-10-01", "periodEnd": "2026-10-31", "medium": "ELECTRICITY", "meterPoint": "DE0…01",
      "consumption": 41250.0, "consumptionUnit": "kWh", "net": 11550.0, "tax": 2194.5, "gross": 13744.5, "currency": "EUR",
      "credit": false, "file": "EN-202610-S.pdf",
      "path": "Energieverträge / Energierechnungen / Strom", "site": { "id": 23053, "name": "Musterwerk" }
    }
  ],
  "truncated": false
}
```

- Newest period first (same day: higher id first), at most 2000 (`truncated`). Invoices without `Period Start` and
  `Invoice Date` are not listed. Dates `yyyy-MM-dd`. Values are the latest samples of the attributes; `consumptionUnit` the display
  unit of `Consumption`; `credit`: gross amount < 0 (credit notes are stored negative). `file`: stored file name of
  `Invoice File`, `null` if none.

### `GET /web/invoices/{id}`

One invoice as above plus `sellerVatId` and `items`: the parsed `Line Items` JSON as stored (`[]` if empty or not
parseable or no array). 404 if not readable.

### `GET /web/invoices/{id}/file`

The original PDF/XML of `Invoice File` (content type from the bytes: `application/pdf` or `application/xml`, `application/octet-stream` for anything
else; without stored file name the object name plus `.pdf`/`.xml`;
`?inline=true` only for PDF), same headers as the reports' file endpoint. 404 if not readable or no file.

### Navigation

`modules` additionally holds `contracts` (number of readable objects of the groups above plus configurations) and
`invoices` (number of readable "Energy Invoice" objects).

## Cost and result calculation (W4.8)

View only, like the Control Center's "Accounting Plugin" tab "View" and the "Template Result Calculation" (TRC)
plugin (`plugin/dtrc/OutputView`, `TemplateHandler`, `TemplateInput`, `RCTemplate`). A **template** ("Result
Calculation Template", JSON in `Template File`: `templateInputs`, `templateFormulas`, `templateOutputs`,
`intervalSelectorConfiguration`) defines inputs, formulas and an output grid. An **accounting configuration**
("Accounting Configuration", JSON in `Template File`: `templateSelection` = template id, `selectedInputs` = the
inputs bound to objects, contract fields) applies a template to concrete objects.

**Shared calculator:** the evaluation moves from the Control Center into JECommons (`org.jevis.commons.trc`, Java 8,
no JavaFX): models (`RCTemplate`, `TemplateInput`, `TemplateFormula`, `TemplateOutput`, `SelectionTemplate`, …) and
a `ResultCalculator` that resolves the time frame, aggregates the inputs, orders dependent formulas and evaluates
them with `CalcExpressions`. The Control Center's `OutputView` uses the same calculator, so both show the same numbers.
**Fixed against the old Control Center:** per time step all inputs of a formula (and the results of the formulas it
uses) are substituted (the old code kept only the last one, so the time steps failed and it fell back to the values of
the first time step), and variables are substituted as whole names (a variable `a` no longer replaces part of `ab`).
Time steps at which an input has no value are skipped; if no time step can be evaluated or the result is `0`, the
formula is evaluated once with the first value of every input (the old fallback). Formulas without output that other
formulas use are calculated (the old code used `0`); a formula that cannot be evaluated (e.g. division by zero) has
no result (`null`, the other formulas use `0`) instead of a missing output. `YEARLY_VALUE` is prorated to the whole days
of the time frame (November = 30 days; the old code counted from the first to the last day, i.e. one day less), and a
time restriction with a fixed time frame and reducing time frame "none" (`NONE`, offered by the restriction dialogs)
runs from the start of the fixed time frame to the end of the requested one, e.g. the peak of the year up to the end
of the month (the old code had this case but never reached it and ignored the restriction). Unchanged on purpose:
`YEARLY_VALUE` only uses a sample inside the calendar year of the start of the time frame (no sample in that year =
no value and a warning, also in the old Control Center), so yearly amounts need one sample per year. Everything else (NaN/infinite → 0, quantity units summed,
other units averaged over the time steps, `YEARLY_VALUE` spread over the time steps, `NON_PERIODIC` carried forward,
`RANGING_VALUE`, time restrictions with fixed/reducing time frame, text inputs and the `Name` attribute) behaves as
before.

### `GET /web/costs`

```json
{
  "configurations": [
    { "id": 23950, "name": "Strom Musterwerk", "path": "Energieverträge / Abrechnung", "site": { "id": 23053, "name": "Musterwerk" },
      "template": { "id": 23960, "name": "Stromkosten", "readable": true } }
  ],
  "templates": [
    { "id": 23960, "name": "Stromkosten", "path": "Berechnungen / Vorlagen", "site": { "id": 23053, "name": "Musterwerk" } }
  ]
}
```

Readable objects not in the recycle bin, sorted like the reports. A configuration whose template is missing or not
readable has `template.readable: false` (still listed, the result answers `409`).

### `GET /web/costs/{id}?lang=de`

`{id}` is a configuration or a template.

```json
{
  "id": 23950, "kind": "configuration", "name": "Strom Musterwerk", "templateName": "Stromkosten",
  "timeFrames": [ { "id": "P1M", "name": "Monat" }, { "id": "P1Y", "name": "Jahr" } ],
  "defaultTimeFrame": "P1M",
  "contract": { "contractNumber": "V-1001", "marketLocation": "DE…", "energyType": "ELECTRICITY", "start": "2026-01-01", "end": "2027-12-31" },
  "inputs": [
    { "id": "i1", "variableName": "verbrauch", "label": "Verbrauch", "objectClass": "Clean Data",
      "selectable": false, "selected": { "id": 23684, "name": "Bereinigte Daten", "readable": true }, "options": [] }
  ],
  "grid": { "rows": 12, "columns": 3 }
}
```

- `timeFrames`: the time frames enabled in the template's `intervalSelectorConfiguration`, in the Control Center's
  order (`TimeFrameFactory.getReduced`), names translated; `defaultTimeFrame` the first.
- `contract`: only for configurations (same fields as `/web/contracts` `configurations`), else `null`.
- `inputs`: the object selectors of the Control Center's input pane: one per input with `group: false` (sorted by
  variable name), then one per `objectClass` for the other inputs (they share one object; `id`/`variableName` of the
  first input, `label` = the variable names joined by `, `). For a configuration they are bound (`selectable: false`,
  `selected` = the configured object; not readable: `{ id, name: null, readable: false }`; an input without object
  uses the object of its group or the first option, like the Control Center). For a template the Control Center lets
  the user pick an object of `objectClass` (exactly that class, filtered by the input's `filter`: every word in the
  name, for clean data "data row \ clean data"): `selectable: true`, `options` = readable objects of that class
  (`{ id, name }`, sorted by name, max 500), `selected` = the first option (the Control Center selects the first
  object; templates store no object) or `null`.

### `POST /web/costs/{id}/result`

```json
{ "timeFrame": "P1M", "from": null, "to": null, "timeZone": "Europe/Berlin", "selections": { "i1": 23684 } }
```

- `timeFrame`: one of `timeFrames`, resolved at "now" in `timeZone` like the Control Center's interval selector
  (the current month etc.); or `CUSTOM` with ISO `from`/`to` (max 10 years, else `400`; also if `CUSTOM` is not
  enabled in the template). `P10Y` covers 10 years (the Control Center's `TimeFrameFactory` 5, see the dashboards).
- `date` (optional, ISO): reference date of the period for the time frames other than `CUSTOM` (e.g. a day in
  September for "Monat" = September); missing or in the future = now. The answer has `previous`/`next`: reference
  dates of the neighbouring periods (as the dashboards' arrows; `next` is `null` when the current period is shown,
  both `null` for `CUSTOM`). The web app shows them as arrows next to the period and keeps `date` in the URL (`d`).
- `selections`: only for templates; input id (the `id` of an `inputs` entry) → object id (must be one of the
  `options`, else `400`; it applies to all inputs of the entry). Missing selections use `selected`. For
  configurations `selections` may be missing, `null` or empty and is ignored.
- `400` answers have a plain text message (also: missing/unknown `timeFrame`, unknown `timeZone`, invalid JSON).

```json
{
  "from": "2026-11-01T00:00:00.000+01:00", "to": "2026-11-30T23:59:59.999+01:00",
  "rows": 12, "columns": 3,
  "cells": [
    { "row": 0, "column": 0, "rowSpan": 1, "columnSpan": 2, "kind": "value", "name": "Arbeitspreis", "nameBold": false,
      "value": 1234.56, "text": null, "unit": "€", "resultBold": true, "tooltip": null,
      "analysis": { "objectId": 23684, "attribute": "Value", "from": "2026-11-01T00:00:00.000+01:00", "to": "2026-11-30T23:59:59.999+01:00" } },
    { "row": 1, "column": 0, "rowSpan": 1, "columnSpan": 3, "kind": "separator", "orientation": "horizontal" }
  ],
  "warnings": [ "Formel \"Netz\": Eingabe \"leistung\" hat keine Werte im Zeitraum" ]
}
```

- `cells`: one per template output. `kind`: `value` (formula result as number; `null` if it could not be
  calculated, the Control Center shows "-"), `text` (text formula: `text` set), `label` (output without formula:
  `name` and formatting, `value`/`text` `null`), `separator` (`orientation` `horizontal` if `columnSpan > 1`, `vertical` if `rowSpan > 1`).
- `analysis`: only if the output has "show analysis link", its formula has exactly one input (the Control
  Center's analysis button) and its object is readable; `from`/`to` is the time frame of the formula (with its time
  restriction); the web app links to an ad-hoc chart of that row for that time frame.
  Outputs with a manual data link (`link`/`target`) get no editing in the web.
- `warnings`: formulas that failed (expression error) or depend on themselves, inputs without object, not readable or
  without values; translated (German/English by the first `Accept-Language`, fallback English).
- Rights: the configuration/template and the bound objects must be readable; an input whose object is not readable
  counts as having no values (warning), it is never read. `404` if `{id}` is not readable, `409` if a configuration's
  template is missing or unreadable.

### Navigation

`modules` additionally holds `costs`: number of readable accounting configurations plus result calculation
templates.

## Editing, stage E1: alarms, notes, manual values, appointments and maintenance

The first write endpoints of the web app. Data is written exactly as the Control Center writes it, so both stay
interchangeable.

**Common rules**

- Rights are checked on the server for every write with the logged-in user's rights, with the same rules as the
  generic sample/object resources (`ResourceSample`, `ResourceObject`, `UserRightManagerForWS`, including the
  execute exceptions for "Data Notes", "User Data" and "Alarm Configuration"). `403` with a plain-text reason if not
  allowed, `404` if the object is not readable or in the recycle bin.
- Every write is logged with `SQLDataSource.logUserAction` (`CREATE_SAMPLE`, `DELETE_SAMPLE`, `CREATE_OBJECT`,
  `UPDATE_OBJECT`) with details in the existing format plus the web action, e.g. `24202:Alarm Checked|1|web:alarm-confirm`.
- Read answers carry what the user may do (`canConfirm`, `canWrite`, `canEnter`, …) so the web app shows buttons only
  where they work; the server checks again.
- Concurrency: edits of existing entries send the `version` they were loaded with (timestamp of the latest sample of
  the edited attribute, ISO). If the stored version differs, `409` with the current entry as JSON body; nothing is
  written.
- Bodies are JSON; `400` with a plain-text message for invalid input. `version` is required wherever it is listed:
  missing or null → `400` `version missing` (no legacy call without version). No cookies are used (Bearer token), so no CSRF
  token is needed.
- The read-only notices in the views ("Nur Ansicht …") are replaced by the edit controls where the user has rights.

### Alarms

- `GET /web/alarms`: each `configuration` additionally has `canConfirm` (write or execute right on the
  configuration).
- `POST /web/alarms/configurations/{id}/confirm` with `{ "checked": true|false }` → `{ "checked": true, "checkedBy":
  "<account>", "checkedAt": "…" }`. Writes a Boolean sample of `Alarm Checked` at now with the note
  `Checked by <account>` / `Unchecked by <account>` (as `AlarmConfiguration.setChecked`). `404` if not an enabled,
  readable "Alarm Configuration".
- `POST /web/alarms/confirm-all` with `{ "ids": [24202, …] }` (the configurations currently shown) →
  `{ "confirmed": [ids], "refused": [ids] }`. Each id is confirmed if allowed; refused ones are listed (no `403` for
  the whole request).

### Notes

- `GET /web/notes` and the analysis `notes`: each note additionally has `canEdit` (write or execute right on the
  notes object), `canDelete` (execute or delete right, the rule of the generic sample delete) and `version`; analysis
  notes also get `id` (`<notesObject>-<epoch ms>`) and `notesObject` (same format as in the notes list). Each row
  (`row` in notes) has `canAddNote`. Rows in the notes list answer additionally carry `notesTarget`: the id (number)
  of the row the notes are stored below (see below), null if notes cannot be stored for this row.
- Analysis data (`POST /web/analyses/{id}/data`): every row additionally has `rowClass` (class of the row object, null
  if not readable), `canAddNote` and `canEnter` (raw "Data"/"Base Data" row outside the recycle bin with write right,
  see manual values); pie rows too.
- **Where a note is stored** (like the analysis note dialog `DataPointNoteDialog`): the target row is the row itself if
  it is a raw "Data" (or other non-clean) row, or the raw parent if the row is "Clean Data" (or subclass). The notes
  object is the first readable "Data Notes" child of the target row; if there is none, a "Data Notes" object named
  `<target row name> Notes` is created below the target row (needs create right; execute suffices for Data Notes;
  additionally write or execute right on the target row, because the new object gets its owners). "First" = lowest id.
- `POST /web/rows/{rowId}/notes` with `{ "timestamp": "…", "text": "…", "tags": ["4", "13"] }` → the note in the notes
  list format. Writes samples at `timestamp` (to the second; milliseconds dropped) to `Value` (text), `Tag` (ids
  joined with `;`, e.g. `4;13`) and `User` (account name). `409` if a note already exists at that timestamp on that
  notes object. If the user may write the target row and a `Value` sample exists at the timestamp, `,userNotes` is
  appended to its note (as the Control Center does), once. Timestamps without offset (all note endpoints) are read in
  the time zone of the query parameter `timeZone` (default: server), answers are printed in it.
- `PUT /web/notes/{noteId}` (`noteId` = `<notesObject>-<epoch ms>` as in the list) with
  `{ "text": "…", "tags": [...], "version": "…" }` → updated note. Overwrites `Value` and `Tag` at the same timestamp,
  `User` becomes the editing account; the note field of the `Value` sample is kept. `version` = the list field
  `version`: the first 16 hex digits of SHA-256 over text, tags and user of the note (the samples table keeps the
  insert time when a sample is overwritten, so a timestamp cannot detect a concurrent edit). `tags` may be left out
  (unchanged). Order of checks: `404`, `403`, `400`, `409`.
- `DELETE /web/notes/{noteId}?version=…` → `204`. Deletes the samples of `Value`, `Tag` and `User` at that timestamp
  and removes the `,userNotes` marker from the row sample if present.
- Tags: ids `0`–`13` of `NoteTag`; unknown or custom tag texts in `tags` are rejected with `400` (custom tags from old
  data are kept when the tags are not changed). `tags: []` (or no `tags` on POST) = no tag (empty `Tag` sample).

### Manual values

- `GET /web/rows/{id}/entry` → what the entry dialog needs:
  `{ "id", "name", "unit", "canEnter", "canDelete", "counter": true|false, "period": "PT15M"|"P1D"|"P1M"|…, "mode":
  "SPECIFIC_DATETIME"|"DAY"|"MONTH"|"YEAR", "timeZone": "Europe/Berlin", "suggestedTimestamp": "…" | null, "last": { "timestamp", "value" } | null,
  "limits": { "min": …, "max": … } | null }`. Only for raw "Data" and "Base Data" rows (`404` otherwise).
  `canEnter` = write right on the row, `canDelete` = delete right (generic sample delete). `timeZone` = time zone of
  the site (`WorkDays`). `mode` from the row's `Period` like `DataTypeBox` (P1D/P1W → DAY, P1M → MONTH,
  P1Y → YEAR, else SPECIFIC_DATETIME; counters always DAY). `suggestedTimestamp` = next period after the last sample
  (`PeriodHelper.getNextPeriod`, null without samples; without `Period` → SPECIFIC_DATETIME). `counter` = "Conversion to Differential" of the Clean Data child (or the row itself
  for Base Data). `limits` = first limit step of the Clean Data child.
- `POST /web/rows/{id}/values` with `{ "timestamp": "…", "value": 123.4, "confirm": false }` → `201`
  `{ "timestamp", "value", "warnings": [] }` (`?lang=` or `Accept-Language` for the texts). The timestamp (ISO date or
  date-time; without offset in the site's time zone; for DAY/MONTH/YEAR only the date in that zone counts, so
  `suggestedTimestamp` can be sent back as is) is aligned by `mode` (YEAR → Jan 1 00:00, MONTH → day 1,
  DAY → 00:00 in the row's/site's time zone, with the custom-workday shift of `EnterDataDialog`). Writes a Double
  sample to `Value` with the note `Manuelle Dateneingabe <now>` (the Control Center's `menu.file.import.manual` text in
  the user's language + timestamp). Then deletes the dependent calculation outputs from that timestamp on
  (`CommonMethods.cleanDependentObjects`, computed on the read-only `LocalDataSource`), so JECalc recalculates; only
  rows the user may delete samples of are reset (others are skipped and logged, like the Control Center whose delete
  the server refuses).
- Plausibility like `EnterDataDialog`: a counter value lower than the last reading, or a value (counter: difference to
  the reading one period earlier) outside the limits, answers `409` with `{ "timestamp", "value", "warnings": ["…"] }`
  unless `confirm` is
  true. An existing sample at the aligned timestamp also gives a warning (overwrite needs `confirm`).
- Registers (`GET /web/registers/meters` and `/basedata`): each row additionally has `canEnter` (meters: for the data
  row of the meter's "Online ID" if it points to `Value`; the entry/value endpoints are then called with that row id
  `target.id`). `GET /web/registers/objects/{id}/history` additionally has `rowId` (the row of the samples) and per
  sample `manual` (note starts with the manual-entry text in any language) and `canDelete` (`manual`, attribute `Value`
  of a raw row and delete right on it).
- `DELETE /web/rows/{id}/values?timestamp=…` → `204`; only samples whose note starts with the manual-entry text (any
  language) may be deleted this way, else `403`; needs delete right on the row (generic rule). The dependent rows are
  reset from that timestamp as after an entry. `404` if there is no sample at exactly that time.

### Appointments and maintenance

- `GET /web/appointments` / `GET /web/maintenance`: each entry additionally has `canWrite` and `version` (timestamp of
  the latest `Data` sample).
- `POST /web/appointments/{id}/done` with `{ "version": "…" }` → the updated entry. Uses
  `AppointmentLogic.complete(entry, now)` (non-recurring: status DONE; recurring: next due date, status stays OPEN,
  `lastDone` set), writes the JSON as a new `Data` sample at now and renames the object to the title (as
  `EntryStore.write`/`rename`). Needs write right (`403`); `400` if the entry is already DONE or has no valid data.
  `version` must be sent (`400`), `409` with the current entry if it differs.
- `POST /web/maintenance/{id}/status` with `{ "status": "PLANNED"|"IN_PROGRESS"|"DONE", "version": "…" }` → the
  updated entry. Uses `MaintenanceLogic.changeStatus(entry, status, now)` (`doneAt` set or cleared). Mails follow from
  JENotifier as today (the "finished" mail only if an inform was sent or requested).

## Editing, stage E2: action plans, nonconformities, legal register

Create, edit and (soft-)delete items of the three ISO modules and manage their attachments. Data is written as the
Control Center writes it (`ActionData`, `NonconformityData`, `ObligationData` in `JEVisControlCenter/.../plugin/
{action,nonconformities,legal}/data`), so both stay interchangeable. The common rules of E1 apply (server-side
rights per write, `logUserAction` with `web:<action>`, `version` required on changes of existing entries → `409` with
the current entry on conflict, `400 version missing`, plain-text `400`/`403` messages).

**Storage (unchanged):** each item is an object ("Action", "Nonconformity", "Obligation") with one FILE attribute
`Data`; every save writes a new file sample at now named `DataModel_v2_<yyyyMMddHHmm>.json` with the item JSON. The
server patches the **latest stored JSON as a tree** (Jackson `ObjectNode`): only the fields sent are changed, every
other key (unknown keys, `Attachment`, NPV `Years`, legacy spellings such as `Nr`/`No`, `Desciption`) stays as stored.
Dates are sent as `yyyy-MM-dd` (`null` or `""` clears the date; anything else → `400`) and written as
`yyyy-MM-dd'T'HH:mm:ss.SSSXXX` at 00:00 in the site's time zone (date pickers); NaN/Infinity stay
unquoted; nonconformities and obligations keep writing `null` for empty dates, actions omit empty keys (as the CC).
If two saves fall into the same second, the second waits until the next second (no overwritten sample).

**Read answers** (`GET /web/actionplans`, `/web/nonconformities`, `/web/legal`) additionally carry per plan
`canCreate` (create right for the item class below the plan's item directory, or below the plan if the directory is
missing — the server creates the directory then, as the CC does) and per item `version` (timestamp of the latest
`Data` sample, ISO), `canWrite`, `canDelete` (write **and** delete right) and `canAttach` (create right for "File"
below the item). `GET /web/objects/{id}/attachments` additionally has `canDelete` per attachment (delete right on the
"File" object).
`GET /web/actionplans`: if a plan's `statuses` contain none of the translations of `plugin.action.status.done`, the
done status is appended in the request language (`?lang=`, else `Accept-Language`, else German like the CC's default
statuses); the same for `plugin.action.status.open`.

**Validation answers:** `400` for invalid field values (unknown or read-only keys, wrong types, values outside the
plan's lists, empty title, nonconformity requirement check) is JSON `{ "messages": ["…", …] }` with
`Content-Type: application/json`, all problems of the request at once. Other `400`s (invalid JSON body,
`version missing`, invalid version) stay plain text as in E1.

### Editable fields (request keys = the stored JSON keys)

- **Action:** `Title`, `Description`, `Note`, `Responsible`, `Distributor`, `Planned Date`, `Done Date`,
  `Status Tags` (one of the plan's statuses), `Field Tags` (list → stored `;`-joined), `Medium Tags` (a medium id of the
  plan), `SEU Tags` (list → `;`-joined), `Correction`, `Follow Action`, `Alternative Measures`, `Affected Process Note`,
  `Energy Flow Note`, `Evaluation Note`, `Check List` (object of booleans, stored keys, merged into the stored object:
  keys not sent, unknown keys and `New EnPI` stay). Not editable on the web: `No`, `Create Date`, `From User`, `NPV`,
  `Consumption`, `EnPI` (calculated by the Control Center), `Check List`.`New EnPI`.
  If `Status Tags` is set to a "done" status (the translations of `plugin.action.status.done` in any language, e.g.
  "Erledigt", "Done") and `Done Date` is empty, `Done Date` becomes today (as `GeneralTab`). The object is renamed to
  `"<prefix> %03d - <title cut at 50>"` (as `ActionData.commit`).
- **Nonconformity:** `Title`, `Responsible Person`, `Description`, `Cause`, `Immediate Measures`, `Corrective Actions`,
  `Action`, `Medium` (a medium name of the plan), `SEU`, `Field Tags` (list, stored as JSON array), `Planned Date`,
  `Done Date`, `Check List`. Not editable: `Nr`, `Creator`, `Create Date`. Saving runs the Control Center's
  requirement check (`NonconformityItem.warnings` / `NonconformityData.checkForRequirements`): violations answer
  `400` with the messages. Renamed to `<prefix><nr>` as the CC does.
- **Obligation:** `title`, `Designation`, `Description`, `importance for the company`, `link`, `category` (one of the
  plan's categories), `scope` (one of the plan's scopes), `Relevance 50001` (boolean), `issue date`,
  `current version date`, `Date of Examination`. Not editable: `Nr`, `uuid`. Renamed to the nr as the CC does.
- Values outside the plan's lists (status, medium, field, SEU, category, scope) answer `400`; old stored values that
  are no longer in the list are kept when not changed and may be sent back unchanged. Allowed statuses are the plan's
  statuses plus the "done" translations. Empty values are always allowed (except the title).
- `Title` (`title` for obligations) is required on create and cannot be cleared (`400`).

### Endpoints (`{kind}` = `actions` | `nonconformities` | `obligations`)

- `POST /web/{kind}/plans/{planId}/items` with the editable fields, flat (`{ "Title": … }`) or as
  `{ "fields": { … } }` → `201` the new item (list format). Fields not sent get the CC's empty defaults; an action's
  status defaults to the translation of `plugin.action.status.open` in the request language. Creates the
  item directory if missing, creates the object named by the number, assigns **`Nr`/`No` = highest number of all items
  of the plan + 1**, counting deleted items and items the user cannot read (the server reads them with its own
  rights for this count only; nothing else of them is returned), sets `Create Date` = today (actions,
  nonconformities), `From User`/`Creator` = the user's first and last name (account name if empty), `uuid`
  (obligations), writes the JSON, renames. Needs create right. A failure after creating the object deletes it (and a
  directory created by the request) again. Objects in the recycle bin are not counted (their parent link is gone).
- `PUT /web/{kind}/items/{id}` with `{ "version": "…", "fields": { … } }` → the updated item. Needs write right.
- `DELETE /web/{kind}/items/{id}?version=…` → `204`. Soft delete as the CC: writes the JSON with `"Deleted": true`.
  Needs write **and delete** right (stricter than the CC, which only needs write). The item disappears from the lists.
  `409` with the current item as for PUT. Order of checks for PUT/DELETE: `404`, `403`, `400 version missing`, field
  validation `400`, `409`, requirement check `400`.
- Attachments (all kinds): `POST /web/items/{id}/attachments` (multipart `file`, max 25 MB, any type; the stored
  file name is sanitised) creates a "File" child named like the file and writes the `File` sample → `201`
  `{ id, name, size, canDelete }`; needs create right for "File" below the item; `413` (plain text) if larger, `400`
  without file. The name loses path parts, control/format characters and `"<>:*?|;`, leading dots, `..`, and is cut
  to 200 characters (extension kept). Items marked deleted answer `404`. `DELETE /web/attachments/{fileId}` → `204`,
  moves the "File" object to the recycle bin (as the CC's `FileTableView`); needs delete right on it and the parent must
  be an action, nonconformity or obligation (`404` otherwise). Download stays `GET /web/files/{id}`.

## Editing, stage E3: analyses

Create, change, copy and delete analyses with everything the Control Center's analysis dialog edits
(`NewSelectionDialog`: tabs `CommonSettingTab`, `ChartTab`, `DayFilterTab`, row table `SelectionTable/*`). The common
rules of E1 apply (server-side rights per write, `logUserAction` with `web:<action>`, `version` required on changes →
`409` with the current analysis, `400 version missing`, plain-text `400`/`403`).

**Storage (as `AnalysisHandler.saveDataModel`):** a new file sample at now in the attribute "Analysis File" of the
"Analysis" object, named `<local name in the request language>_<yyyyMMddHHmmss>.json`, content as
`AnalysisHandler.toJsonNode(dataModel)` (pretty printed, `"type": "AnalysisHandler"`). The JSON is written by a new
headless `AnalysisWriter` in `org.jevis.commons.chart` (counterpart of `AnalysisReader`, Java 8 compatible). Keys of
the stored JSON the writer does not know are kept: the writer starts from the latest stored JSON and patches it —
top level as a whole; charts matched by `chartId`; rows matched by `id` + `attributeString` within their chart. New
charts/rows get the CC's keys only. Not written, as in the CC: time frame and per-row
`aggregationPeriod`/`manipulationMode` (they come from the toolbar, OpenJEVis #2358; stored ones of old files are
kept untouched). Analyses still in the legacy format ("Data Model"/"Charts") are written in the current format on
their first save (as the CC does); the legacy attributes are not changed. If two saves fall into the same second,
the second waits until the next second.

**Rights (as `SaveAnalysisDialog`):** create = create right for "Analysis" below an "Analyses Directory"; change =
write right on the analysis; delete = delete right; rename = write right. Names are unique within a directory
(`409` `{ "nameTaken": true, "id": <existing id> }` on create/rename/copy with a name that exists; the web app then
offers to overwrite, which is a `PUT` on the existing analysis).

### Reading

`GET /web/analyses/{id}` (W2) additionally carries everything needed for editing (all additive):

- top level: `version` (timestamp of the latest "Analysis File" sample, or of the legacy "Data Model" sample, ISO;
  `"none"` if the analysis has never been saved), `canWrite`, `canDelete`, `directoryId` (parent "Analyses
  Directory", `null` if the parent is something else), `forcedInterval`, `dayFilter`
  `{ "weekdays": "…", "holidays": false, "dates": "…" }` (stored strings as in the file), `limitLines`
  `[{ "value", "label", "color", "axis", "fill" }]`;
- per chart: `fractionDigitsAuto`, `filterEnabled`, `coloringEnabled`, `showColumnSums`, `showRowSums`, `dayStart`,
  `dayEnd` (`HH:mm:ss` or `null`), `filterTexts` (object);
- per row: `rowClass` (already there), `unitPrefix`, `unitFormula`, `unitLabel` (stored unit; `unit` stays the
  display label), `calculationId`, `interval` `{ "enabled": false, "start": null, "end": null }`, `css`.

Limit lines and table options are now also applied by the web view (as in the CC): limit lines as horizontal lines
on their axis, table sums/colouring/filter in table charts. The day filter is already applied by the data endpoint.

### Writing

The **definition** sent is the GET answer's shape without the read-only keys (`id`, `names`, `defaults`, `version`,
`can*`, `directoryId`, per row `readable`, `unit`, `key`, `rowClass`, `calculationId`):
`{ "chartsPerScreen", "horizontalPies", "horizontalTables", "autoSize", "forcedInterval", "dayFilter", "limitLines",
"charts": [{ "id", "name", "type", "minFractionDigits", "maxFractionDigits", "fractionDigitsAuto", "height",
"orientation", "fixYAxisToZero", "xAxisTitle", "yAxisTitle", "groupingInterval", "colorMapping", "filterEnabled",
"coloringEnabled", "showColumnSums", "showRowSums", "dayStart", "dayEnd", "filterTexts", "rows": [{ "objectId",
"attribute", "name", "color", "axis", "chartType", "unitPrefix", "unitFormula", "unitLabel", "decimalDigits",
"calculation", "bubbleType", "interval", "css" }] }] }`. Missing keys get the CC defaults. Charts are stored in the
order sent.

Validation (`400` JSON `{ "messages": [...] }` with all problems, as in E2): chart ids unique and ≥ 0; enum values
(`type`, `chartType`, `orientation`, `colorMapping`, `bubbleType`, limit line `fill`) from the lists of W2 / the CC
enums; `axis` 0 or 1; colours `#rrggbb` or `#rrggbbaa`; `chartsPerScreen`, `horizontalPies`, `horizontalTables`
1–20; fraction digits 0–20; `dayStart`/`dayEnd` `HH:mm:ss`; every row object must be readable by the user and of a
selectable class (`Data`, `Clean Data`, `Base Data`, `Math Data`, `Forecast Data`, `String Data`, as
`TreeSelectionDialog`) with the attribute existing; rows the user may not read that are **already stored** may be
sent back by `objectId` + `attribute` and keep their stored settings, so saving does not drop them.
`calculation: true` sets `calculationId` on the server (`ChartTools.getCalculationId`: the calculation whose output
is the row); `400` if there is none. Unit fields must be one of `GET /web/rows/{id}/units` (or the stored unit).

- `POST /web/analyses?lang=de` with `{ "directoryId", "name", "definition" }` → `201` the new analysis (GET format).
  Creates the "Analysis" object below the directory (create right), writes the file. A failure after creating the
  object deletes it again. Name: trimmed, 1–100 characters, no control characters.
- `PUT /web/analyses/{id}?lang=de` with `{ "version", "definition", "name"? }` → the analysis (GET format). `name`
  renames the object (write right; unique in the directory).
- `POST /web/analyses/{id}/copy?lang=de` with `{ "directoryId", "name", "definition"? }` → `201` ("Speichern unter":
  the sent definition, or the stored one). Needs read right on the source and create right in the target directory.
- `DELETE /web/analyses/{id}?version=…` → `204`; moves the object to the recycle bin (as deleting in the CC). Needs
  delete right.
- Logged actions: `web:analysis-create`, `web:analysis-save`, `web:analysis-copy`, `web:analysis-delete`.

**Details settled in the implementation**

- `PUT`, `copy` with a definition and `preview` with an `id`: a key missing in a chart or row keeps the stored value
  (charts matched by `id`, rows by `objectId` + `attribute`); new charts/rows get the CC dialog defaults (row
  `chartType` `DEFAULT`, colour `#add8e6`, unit = display unit of the attribute, interval today 00:00 – now).
- A row `name` sent back equal to the object name keeps a stored empty name (`""`, the CC then shows the object name).
- Colours: `GET` answers `#rrggbb`; rows are stored as `0xrrggbbaa` (JavaFX `Color.toString`, alpha kept when only
  the RGB is unchanged), limit lines as `#RRGGBB` (as `UserLimitLine`). Unchanged values keep their stored spelling.
- `dayStart`/`dayEnd`: `HH:mm:ss` in the answer, `HH:mm` or `HH:mm:ss` accepted, stored as the CC's `HH:mm:ss.SSS`.
- File name: path and control characters of the local name become `_`.
- `PUT` with only `name` (no `definition`) renames without writing a new file; the version stays.
- `preview/data` also accepts `id` (stored unreadable rows may be sent back; workday of that analysis) and
  `directoryId` (workday for new analyses).
- `rows/tree` without `parent`/`q`: readable organisations and buildings without a readable organisation/building
  above them. `q` shorter than 2 characters and an invalid `parent` → `400`, an unreadable `parent` → `404`.
- Order of checks for `PUT`: `404`, `403`, `400 version missing`, validation `400`, `409` version, `409 nameTaken`.
- The CC picker's limits (max. 10 rows in a pie, one row in a heat map) are not enforced.
- Web app: "Neue Analyse" with a taken name offers "Vorhandene Analyse öffnen" instead of overwriting (an empty new
  analysis would replace a filled one); "Speichern unter" and rename offer overwrite.

### Helpers for the editor

- `GET /web/analyses/directories` → `[{ "id", "name", "path": "Org / Building / Analysen", "canCreate": true }]`:
  all "Analyses Directory" objects the user may read; `canCreate` = create right for "Analysis" there.
- `GET /web/rows/tree?parent=<id>&q=<text>&lang=de` → row picker. Without `parent` and `q`: the organisations the
  user may read. With `parent`: its readable children `[{ "id", "name", "class", "hasChildren", "selectable",
  "unit", "attributes": ["Value"] }]` — directories and buildings with `hasChildren`; `selectable` for the classes
  above (their children, e.g. the Clean Data below a Data row, are children in the tree); `unit` the display unit of
  `Value`. `q` (≥ 2 characters) searches by name below `parent` (or everywhere the user may read), max 200 hits,
  each with `path`. Names in the request language (local names), like the navigation.
- `GET /web/rows/{id}/units?attribute=Value` → `[{ "prefix", "formula", "label" }]`: the units offered for the row,
  as the CC's unit box (`ChartUnitBox` / `ChartUnits`), first the row's own unit. `404` if not readable.
- `POST /web/analyses/preview/data` with `{ "definition", …the W2 data request… }` → the W2 data answer for an
  unsaved definition (`key` of rows = `<chartId>-<objectId>-<attribute>` as in W2). Rows the user may not read answer
  `NO_PERMISSION` as in W2. Same validation as saving (`400`). Used for the live preview while editing.

### Web app

- Analysis view: "Bearbeiten" (shown with `canWrite`; with only a directory with `canCreate` the panel opens with
  "Speichern unter" only) switches to edit mode: a side panel with the tabs **Allgemein** (charts per screen, auto
  size, pies/tables per row, forced interval, limit lines, day filter), **Diagramme** (add/remove/rename/order charts
  and their settings), **Datenreihen** (rows per chart: picker, name, colour, unit, axis, chart type, bubble type,
  decimals, interval, calculation; apply a unit/colour to all rows of a chart as the CC's "for all" button). The
  chart area previews the changes via `preview/data` (debounced). Buttons: "Speichern", "Speichern unter",
  "Verwerfen", "Löschen".
- "Neue Analyse" in the navigation (when any directory has `canCreate`): name + directory, opens the new analysis in
  edit mode. After create/rename/delete the navigation reloads.
- Leaving edit mode or the page with unsaved changes asks first. Conflicts (`409`) offer reload or "Speichern unter".

## Editing, stage E4: dashboards

Create, change, copy and delete dashboards with everything the Control Center's dashboard editor edits (layout,
dashboard settings, common widget settings, data rows, the settings of all 23 widget types) and use the input
widgets. The common rules of E1 apply (server-side rights per write, `logUserAction` with `web:<action>`, `version`
required on changes → `409` with the current state, `400 version missing`, plain-text `400`/`403`; validation
`400` as JSON `{ "messages": [...] }` with all problems, as in E2/E3).

**Source editing.** The web editor works on the dashboard JSON **as the Control Center stores it** ("source"), not
on the normalised view format of W3 (the W3 reader drops keys). The server checks the source, stores it and turns
it into the W3 view format for the preview. So every key of every widget survives a web save, and what the web
writes is what the CC reads. Format (CC `ConfigManager.toJson`, `Widget.createDefaultNode`, the widgets' `toNode`):

- top level: `version` ("1.2"), `"Dash Board Color"`, `backgroundMode` (`default|repeat|stretch`), `"Snap to Grid"`,
  `"Show Grid"`, `"X Axis Grid Interval"`, `"Y Axis Grid Interval"`, `"Update Rate"`, `"Zoom Factor"` (number or
  97/98/99), `width`, `height`, `"Data Period"` (`TimeFrameFactory` id or the id of a custom period), `"Widget"`
  (array);
- widget: `uuid` (int), `WidgetType`, `title`, `tooltip`, `"Background Color"`, `"Font Color"`, `fontSize`,
  `fontWeight`, `fontPosture`, `fontUnderlined`, `"Title Position"`, `borderSize`, `borderColor`, `cornerRadius`,
  `shadow`, `layer`, `xPos`, `yPos`, `width`, `height`, `decimals`, `decimalsAuto`, `showValue`, `fixedTimeframe`,
  `forcedTimeFrame`, `dataHandler` (analysis JSON as `AnalysisHandler.toJsonNode(model, true)`, i.e. rows **with**
  `aggregationPeriod`/`manipulationMode`; `forcedInterval` = the widget's own time frame), plus the type block
  (`limit`, `percent`, `dimIncompletePeriod`, `gaugeDesign`, `linearGaugeDesign`, `BatteryDesign`, `shapeDesign`,
  `NetGraph`, `Sankey`, `"Time Frame"`, `minMax`, `arrow`, `image`, `analysisLinker`, `dashboardLinker`,
  `reportLinker`, `alarmCount`);
- colours as JavaFX `0xRRGGBBAA`; the server also accepts `#rrggbb`/`#rrggbbaa` and stores `0xRRGGBBAA`.
- Widget types (exact spelling, the 23 registered in `Widgets.java`): `Chart`, `Pie`, `Table`, `Value`,
  `String Value`, `Value Editor`, `Toggle Switch`, `Slider`, `PlusMinus`, `Gauge`, `LinearGauge`, `Battery`,
  `Shape`, `NetGraph`, `Sankey`, `Time Frame`, `Title`, `Arrow`, `Image`, `Analyse-Link`, `Dashboard-Link`,
  `Report-Link`, `Alarm Count` (check the ids against `Widgets.java`/the widgets' `WIDGET_ID`). Others → `400`
  (the CC drops unknown types on its next save).

**Storage (as `ConfigManager.saveDashboard`):** a new file sample at now in the attribute "Data Model File" of the
"Dashboard Analysis" object, named `<name with only A–Z a–z 0–9>_<yyyyMMddHHmm>.json`, pretty printed UTF-8. If two
saves fall into the same second, the second waits until the next second. Legacy widgets with a `SimpleDataHandler`
are delivered by `/source` converted to the `AnalysisHandler` format (as the W3 reader does; the CC reads both and
writes the new one on its own next save).

**Rights (as the CC's `SaveUnderDialog` / toolbar):** create = create right for "Dashboard Analysis" below a
"Dashboard Directory"; change and rename = write right on the dashboard; delete = delete right. Names unique within
the directory (`409 { "nameTaken": true, "id": … }`, overwrite offered by the web app = `PUT` on that dashboard).
**Delete moves the dashboard to the recycle bin** — in the web and, from E4 on, also in the Control Center
(`DashboardControl` delete → the CC's recycle-bin delete as used for files/objects elsewhere in the CC).

### Validation of a source (save, create, copy, preview)

- JSON object with the top-level keys above (missing ones get the values `ConfigManager.loadDashboard` falls back
  to: `version` "1.2", colour `0xf4f4f4ff`, mode `default`, `Snap to Grid`/`Show Grid` false, grid 25, update rate
  900, zoom 1.0, 1863×886, `Data Period` `P1D`, no widgets);
  `width`/`height` 100–20000; zoom > 0 and ≤ 20 or 97/98/99; grid intervals 1–1000; update rate 1–86400;
  `"Data Period"` a `TimeFrameFactory` id (`TimeFrameFactory.getAll`: the standard ids without `CUSTOM`, plus
  `LASTVALUE`) or a custom period the user may read; an unchanged stored value is always accepted.
- Widgets (at most 1000): known type; `uuid` int ≥ 0 and unique (missing or 0: the next free one is assigned); `xPos`/`yPos` finite, `width`/`height` 10–20000; `layer` int;
  colours valid; `fontSize` 1–400; `decimals` 0–20; references to other widgets (`limit.source`, `percent.source`,
  `"Time Frame".selectedWidget.id`) are uuids of the same dashboard or empty.
- Data rows (`dataHandler.chartModels[].chartData[]`): object readable by the user and of a selectable class (as
  E3) with the attribute existing; enums (`chartType`, `aggregationPeriod`, `manipulationMode`, `bubbleType`) valid.
  **Rows, link targets, alarm objects and image objects the user may not read are accepted only if the same widget
  (`uuid`) of the stored source already has them** (so saving does not drop them and the web cannot add them).
- Link targets (`analysisLinker.graphAnalysisObject`, `dashboardLinker.dashboardObject`, `reportLinker.reportObject`,
  `image.objectID`, `alarmCount.alarmObject`): objects of the right class (Analysis / Dashboard Analysis / Report /
  File / any object for alarms) the user may read (or unchanged, see above).

### Endpoints

- `GET /web/dashboards/{id}` (W3) additionally: `version` (timestamp of the latest "Data Model File" sample, ISO,
  `"none"` if never saved), `canWrite`, `canDelete`, `directoryId` (parent "Dashboard Directory" or `null`), per
  widget `canEnter` (input widgets: write right on the row's object, as E1 manual values; else `false`).
- `GET /web/dashboards/{id}/source` → `{ "version", "source": {…} }` (read right). A dashboard without file gets the
  CC's empty dashboard.
- `POST /web/dashboards/preview` with `{ "source", "id"?, "directoryId"?, …the W3 data request… }` →
  `{ "dashboard": <W3 GET format of the unsaved source>, "data": <W3 data answer> }`. Same validation (`400`); `id`
  allows the stored unreadable items of that dashboard and gives the building's workday; `directoryId` the workday
  for new dashboards. Image/background URLs of the GET format refer to `id`.
- `POST /web/dashboards?lang=de` with `{ "directoryId", "name", "source" }` → `201` GET format (+ `version`). A
  failure after creating the object deletes it again. Name: trimmed, 1–100 characters, no control characters.
- `PUT /web/dashboards/{id}?lang=de` with `{ "version", "source"?, "name"? }` → GET format. Only `name`: rename
  without a new file (version unchanged).
- `POST /web/dashboards/{id}/copy?lang=de` with `{ "directoryId", "name", "source"? }` → `201` ("Speichern unter"):
  read right on the source dashboard, create right in the target; copies the latest "Background" sample (as the CC
  does); image widgets keep referencing their File objects.
- `DELETE /web/dashboards/{id}?version=…` → `204`, recycle bin, delete right.
- `GET /web/dashboards/directories` → `[{ "id", "name", "path", "canCreate" }]` (as E3, class "Dashboard Directory").
- `PUT /web/dashboards/{id}/background` (multipart `file`, PNG/JPEG/GIF/BMP/WebP/SVG recognised by content, max
  10 MB) → `204`; writes a new "Background" sample (write right on the dashboard). `413`/`400` as E2.
- `POST /web/dashboards/{id}/images` (multipart `file`, image types as above, max 10 MB) → `201 { "objectId", "name" }`:
  creates a "File" child of the dashboard (create right) for an Image widget, as `ImageConfig` does. The dashboard
  must exist (a new dashboard is created first).
- `POST /web/dashboards/{id}/widgets/{uuid}/value` with `{ "value": number|string|boolean, "rowKey"? }` → the
  widget's data answer for that widget: input widgets (`Value Editor`, `Toggle Switch`, `Slider`, `PlusMinus`) write
  the value **as the CC widget does** (check `ValueEditWidget`, `ToggleSwitchWidget`, `SliderWidget`,
  `PlusMinusWidget`: timestamp, attribute, value type, PlusMinus = latest value ± increment computed on the server)
  into the widget's row; write right on that object (`403` otherwise); `400` for a widget that is no input widget or
  a value of the wrong type; Slider within `minMax`. Logged as `web:dashboard-value`.
- Logged actions: `web:dashboard-create`, `web:dashboard-save`, `web:dashboard-copy`, `web:dashboard-delete`,
  `web:dashboard-background`, `web:dashboard-image`, `web:dashboard-value`.

**Details settled in the implementation**

- Empty dashboard (`/source` of a dashboard without file, `POST` without `source`): as the CC's new dashboard,
  1853×876, colour `0xf4f4f4ff`, grid 25, zoom 1.0, `Data Period` `P1D`, update rate 900 (the CC's `DashboardPojo`
  starts with 10 s; 900 is the reader default and spares the server).
- `/source` normalises like the CC reader: widgets without uuid get the next free one (same order as W3, so the
  W3 keys match), legacy `SimpleDataHandler` widgets are converted (`DashboardReader.convertLegacyDataHandler`: one
  chart model, rows with aggregation/manipulation, unit, colour `0xrrggbbff`, `forcedInterval`).
- Further checks: `fontWeight` (`FontWeight` names), `fontPosture` (`REGULAR`/`ITALIC`), `"Title Position"` (`Pos`
  names or legacy `left`/`right`/`center`) — unchanged stored values are accepted; `borderSize` 0–100,
  `cornerRadius` 0–10000, texts ≤ 10000 characters, booleans, `dataHandler.type` must be `AnalysisHandler` (a
  legacy handler sent is converted), `dataHandler.forcedInterval` empty or a known time frame, row `axis` 0/1,
  `decimalDigits` −1–20. Unknown keys are kept everywhere; nothing else is normalised except colours
  (`#rrggbb`/`#rrggbbaa`/`0xrrggbb` → `0xrrggbbaa`, unchanged stored spellings kept).
- An unreadable stored row sent back is stored **as stored** (its settings cannot be changed by the web, as E3).
- `calculation: true` without `calculationId` sets it (as E3, `400` if the row is not calculated); `calculation:
  false` sets `calculationId` −1.
- File name from the object name (as the CC, not the local name); `?lang=` is only used for the note of the Value
  Editor. Copy writes the original's latest background at now (file name kept).
- Order of checks for `PUT`: `404`, `403`, `400 version missing`, validation `400`, `409` version, `409 nameTaken`.
- Uploads: `400` without file or if it is no image (by content), `413` above 10 MB. The image object is named like
  the file without extension (`ImageConfig`), answer `name` = object name.
- `…/value` (order: `404` dashboard, `404` widget, `400` no input widget / no row / `rowKey` not the first row,
  `400` missing value, `404` row not readable, `403`, `400` wrong value): the input widgets use their **first** row.
  `Toggle Switch`: `true`/`false` → Double `1`/`0`; `Slider`: number, rounded to the widget's `decimals` (HALF_UP),
  within `minMax.minimum`–`maximum` (default 0–0 as `MinMaxPojo`); `PlusMinus`: `"plus"`/`"minus"`, latest value of
  the row's attribute (none: 0) ± `minMax.Increment` (default 0.1, at most 10), kept within 0–100 (the TilesFX tile's
  default range, as the CC) and rounded. These three write a Double sample at now to `Value` of the row's object
  without note (`JEVisAttribute.buildSample(new DateTime(), value)`), no dependent reset. `Value Editor`: the CC opens
  the manual entry dialog, so it is the E1 manual entry (`POST /web/rows/{id}/values` rules: raw `Data`/`Base Data`
  only, note, plausibility `409` with warnings, `confirm`, dependents reset); optional `timestamp` (default now) and
  `confirm`. `canEnter` of a Value Editor = E1 `canEnter`. Optional `timeZone`. Answer `200` with the widget's data.
- Control Center (from E4): deleting a dashboard moves it to the recycle bin (`deleteObject(id, false)`, error dialog
  if the server refuses); a save without dashboard object reports an error instead of being skipped; a dashboard
  whose time frame was cleared is saved with `P1D`.

### Web app

- Dashboard view: "Bearbeiten" (with `canWrite`; with only a directory with `canCreate`: "Speichern unter" only)
  loads `/source` and switches to edit mode; the canvas renders the draft via `preview` (debounced; moving and
  resizing are shown immediately from the source, the preview follows).
- **Canvas:** select by click, add/remove by Ctrl/Shift+click, rubber band; move by dragging, resize by 8 handles;
  snapping to the dashboard's grid interval (Shift: a quarter of it); arrow keys move the selection by one grid step
  (with Shift by 1 px); Entf deletes; Strg+D duplicates (new uuid = max + 1, title + " #2", offset by one grid
  step); layer up/down; undo/redo of the whole source (Strg+Z, Strg+Y / Strg+Umschalt+Z, 30 steps, as the CC);
  "Widget hinzufügen" with all 23 types (CC defaults per type, placed in the visible area).
- **Side panel** with three tabs: **Dashboard** (name, width/height, background colour, mode, image upload, time
  frame, zoom, update rate, grid interval), **Widget** (for one or several selected widgets: title, tooltip,
  colours, font, title position, border, corner radius, shadow, decimals/auto, show value, position/size, layer, own
  time frame), **Daten/Typ** (data rows via the E3 picker incl. aggregation/manipulation, name, colour, unit,
  decimals, calculation; chart settings for `Chart`; the type settings of the selected widget's type).
- Buttons "Speichern", "Speichern unter", "Verwerfen", "Löschen"; "Neues Dashboard" in the navigation (name +
  directory, opens the new dashboard in edit mode — **wait until the loaded dashboard is the new one before starting
  edit mode**, the bug fixed for analyses in E3); unsaved-changes guard; `409` → reload or "Speichern unter".
- **Input widgets** in the normal view (not edit mode): with `canEnter` they can be used (enter a value, toggle,
  slide, plus/minus) and write via `…/value`; without, read-only as before.
- Phone width: editing is offered from tablet width on (≥ 900 px); below, the dashboard stays view-only with a hint.
