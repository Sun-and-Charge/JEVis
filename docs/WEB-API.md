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
