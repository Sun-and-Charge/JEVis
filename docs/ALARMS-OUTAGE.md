# Alarms: data outage, several rules, time-dependent limits, daily summary

Tender points 09 (missing values / connection monitoring) and R16 (time and object based limits). User decisions
2026-11-26: mails at the start of an outage plus a daily summary (no "resolved" mail); sums of several rows via a
calculated sum row (no summing in JEAlarm); the bug that only the last rule of a row is evaluated is fixed in this
stage.

## Before (as found)

- JEAlarm evaluates limit notes (L1/L2), delta notes (D1/D2), filled gaps (GAP) and one rule per row from "Alarm
  Config" (STATIC / DYNAMIC). "Alarm Config" may hold a list, but each entry overwrote the previous one, so only the
  last rule took effect; silent/standby schedules of all entries were merged.
- Missing data raised nothing. Worse: if any row of a configuration had no data up to the end of the period, the whole
  run was skipped ("No new Data") and "Time Stamp" not advanced, so one dead meter silenced all alarms of its
  configuration.
- JEStatus sends one global status mail (late data sources, calculations, clean data); nothing per object.
- The alarm mail listed only STATIC/DYNAMIC alarms; GAP and D1/D2 triggered the mail but were missing from its body.

## Rules ("Alarm Config" of a clean data row, `JsonAlarmConfig`)

All entries of the list are evaluated, each with its own silent/standby time. New optional keys:

| Key | Meaning |
|---|---|
| `noData` | minutes without a new value of the row → rule kind "Datenausfall" (no `limit`/`limitData`/`operator`) |
| `validTime` | `JsonScheduler` like `silentTime`: the rule only applies to values inside these times (day/night limits) |
| `average` | minutes of a moving mean: the rule compares the mean of the values in (t − average, t] instead of the value |

A value corridor is two rules (`<` minimum, `>` maximum), each optionally with `average`. A limit for a sum of rows is
a rule on a calculated sum row.

Compatibility: Control Centers before this change reject unknown keys; a row using the new keys can only be edited in
the web app or the new Control Center. Rows without them stay as they were. `JsonAlarmConfig` and `JsonAlarm` ignore
unknown keys from now on.

## Data outage

Checked in every JEAlarm run, independent of the alarm period, for the rows of the configuration with a `noData` rule
and for data sources:

- **Row:** the newest value of the raw data row (arrival of data, not the processed clean data). No value newer than
  now − `noData` → outage starts at the newest value.
- **Data source** (selected in "Alarm Objects", or with scope COMPLETE all enabled data sources below the building):
  the newest "Last Readout" of its channels older than the source's "Latest reported" hours (as JEStatus; default
  24 h) → transmission fault. A row outage whose data source is in outage names that source as cause.
- **End:** a newer value arrives (resp. a newer readout). The end records whether the outage time was filled
  afterwards (late delivery: values with time stamps inside the outage arrived) or remains a gap.
- Rows in outage no longer block the period check of the other rows ("No new Data" only waits for rows that are not in
  outage).

Bookkeeping on the "Alarm Configuration":

- `Outage State` (String, JSON, latest sample): open outages by object (`since`, `opened`, `kind` ROW/SOURCE, `minutes`,
  `cause`), and `lastSummary`.
- `Outage Log` (String, one sample per run with events, a JSON array of `{event: START|END, object, kind, since,
  minutes, cause, resumed, backfilled}`; one sample per run because samples of the same second would overwrite each
  other). Kept apart from "Log File" so that older Control Centers' alarm lists keep working.
- An outage of an object that is no longer checked (rule removed, out of scope) ends without `resumed`.

## One configuration per row (user decision 2026-11-26)

Older sites have one configuration with scope `COMPLETE` for all rows; the alarm wizard later added one configuration
with scope `SELECTED` per row. Both reported the same row, so its alarms and outages were listed and mailed twice.
Now a configuration with scope `COMPLETE` or `WITHOUT_SELECTED` skips every row that an **enabled** `SELECTED`
configuration checks (`AlarmConfiguration.rowsWithOwnConfiguration`, used by JEAlarm and JEReport's alarm link; the
web service's "Gemeldet von" list follows the same rule). Sites with only the site-wide configuration are unchanged; a
disabled own configuration hands the row back to the site-wide one. Outages a site-wide configuration had open for
such a row end as "no longer monitored". The outage lists of the web app and the Control Center still merge an outage
reported by several configurations into one entry.

## Mails

- **Start of an outage:** one mail per run with the newly started outages (row/source, since, how long, cause), via the
  configuration's notification as today.
- **Daily summary** (new attributes `Daily Summary` Boolean, `Daily Summary Time` "HH:mm", time zone of the
  configuration): limit alarms of the last 24 h from "Log File", outages started and ended in the last 24 h, and the
  outages still open; sent once per day after the time; nothing sent when there is nothing to report.
- **Alarm mail body:** lists all alarm types (also gaps and deltas), not only STATIC/DYNAMIC.

## Web app and Control Center

- Web service: `GET /web/alarms` additionally returns `outages` (open and recent, with start/end/cause); data row
  alarm settings read and write the new rule kinds and keys; configurations read and write the daily summary; a status
  `noDataSince` for rows with a `noData` rule (meters, data row settings).
- Web app: rule kind "Datenausfall", "Gültig" schedule and averaging per rule; outage list (open / ended) in the
  alarms page; daily summary in the configuration editor; badge "keine Daten seit …".
- Control Center (J25): the alarm editor shows and keeps the new keys; the alarm plugin lists outages.

## Tests

Pure logic in JECommons (`AlarmRuleEvaluator`, `OutageLogic`) with tests for: several rules, valid time (day/night),
average and corridor, silent/standby per rule; outage start, no repeat, end with late delivery vs. gap, source outage as
cause, rows in outage not blocking the period; daily summary due once per day.
