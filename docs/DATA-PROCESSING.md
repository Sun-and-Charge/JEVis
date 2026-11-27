# Data processing: period conversion (JEDataProcessor)

How "Clean Data" turns raw values of one period into clean values of another ("Period" of the raw row and of the
clean row, "Value is a Quantity", "Conversion to Differential"). Code: `JECommons/.../dataprocessing/processor`
(`PrepareStep`, `DifferentialStep`, `AggregationAlignmentStep`); the service works in batches of "processing size"
clean periods (50,000 by default), each batch starts at the last written clean value.

## Rules (2026-11-27)

| Raw → clean | Consumption (quantity) | Counter readings (differential) | Mean values (no quantity) |
|---|---|---|---|
| shorter raw period (e.g. 15 min → hour, day, month) | sum of the raw values in the clean period | reading at the end of the clean period − reading at its start | mean of the raw values |
| same period | the raw value | days, weeks, months, years: reading at the end of the period − reading at its start (the value on 1 January is the consumption of January); shorter periods: difference to the reading before, stamped at the end | the raw value |
| longer raw period (e.g. month → day, hour → 15 min) | the raw value split evenly over the clean periods of its raw period (stamped at the start of the raw period) | the consumption since the reading before, split evenly over the clean periods between the two readings | the raw value for the clean periods of its raw period |

- Clean periods shorter than a day are stamped at their end, days/weeks/months/years at their start.
- Counting clean periods uses local time: a month with the change to summer time has 31 days, not 30.96.
- No clean value before the first raw value: intervals without raw data before it stay empty (no 0). Mean values
  without raw data stay empty everywhere (a temperature of 0 is a value).
- The last raw value of a split covers its whole raw period, also when a batch stopped inside it; a row whose last clean
  value is newer than its last raw value is processed until the end of that raw period (`CleanDataObject.checkConfig`).
- Batches start at the start of a raw period (date and time of day); counter readings use the reading before the
  batch from the raw values, so batch boundaries and gaps between readings do not lose consumption.

## Changes of 2026-11-27 (values of existing rows change when they are processed again)

- Counter readings into days, weeks, months, years: the reading at the start of the period belongs to the period
  before. Before, the values were shifted by one raw step (e.g. a day value ran from 23:45 to 23:45) and the last raw
  step of the last period was lost.
- Monthly clean data of counters: the last month was not calculated (one month was subtracted in UTC); monthly
  values in general: the last month was missing (one month was added in UTC to the last raw value).
- Counter readings into the same calendar period: the value on the 1st is the consumption of that month (user
  2026-11-27, aligned with the other day and month values and the cost calculation); before, the bugs above put the
  consumption of January on 1 February in some cases.
- Splitting into shorter periods: the rest of the last raw period, the share of each clean period across batch
  boundaries and gaps, the day count across the change to summer time, 15 → 5 minutes (wrong interpolation for
  counters, widened intervals).
- Zeros before the first raw value and zeros for mean values without data are no longer written.
- First run without clean data: starts on the local day of the first raw value (in UTC it was a day earlier).

## Tests

`JECommons/src/test/.../processor/CleanPeriodMatrixTest` runs the real steps in batches (harness
`CleanPipelineHarness`, like `ProcessManager.start()` and `ImportStep`) for 19 period pairs (1 min, 5 min, 15 min,
hour, day, month, year in both directions and equal) × consumption, counter readings and mean values × batch size
50,000 and 997 (batch boundaries inside raw periods), over both changes of daylight saving time; `CleanPeriodStampTest` checks the time stamps of day, month and year
values (start of the period). Checks: the clean
values add up to the raw consumption (counters: last − first reading), every clean period of a split has a value, no
value before the first raw value, mean values of a constant row stay constant. Month → 15 minutes is not run with
997: a batch must hold the clean periods of one raw period (2,976 quarter hours a month). Debugging one case:
environment `HARNESS_ONLY="PT15M P1M COUNTER 50000"` prints the intervals of each batch.
