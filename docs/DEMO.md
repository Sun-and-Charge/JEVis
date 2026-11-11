# Demo organisation: the JEDemo job

The public demo organisation (e.g. "Sun and Charge Demo") is test material for tenders and the app. Its data come
from an anonymised snapshot, so without help they would stop on the day of the snapshot. The server job **JEDemo**
(module `JEDemo`, Java 25) keeps the demo current. JEDataProcessor, JECalc and JEAlarm run as usual: JEDemo only
writes **raw data**, clean data, calculations and alarms follow from the normal pipeline.

## What the job does (each cycle, default 1 h)

1. **Replays raw data.** Rows of class `Data` and `String Data` below the organisation are continued with their own
   history:
   - Week rows (period one day or shorter): offset 52 weeks if the history spans at least 364 days, otherwise the
     history in whole weeks. Weekday and time of day stay the same (Europe/Berlin local time).
   - Calendar rows (period `P1M`, `P3M`, `P1Y`, ... or, without period, a median sample spacing of 28 days or more):
     offset 12 months if the history spans 12 months, otherwise the history in whole months. Day of month
     and time stay, month ends are clamped (31 Jan + 1 month = 28 Feb).
   - Source samples in `(last − offset, now − offset]` are written at `ts + offset`, never after now. At most
     8 weeks (week rows) or 3 months (calendar rows) per row and cycle, so a long gap closes over several cycles.
   - DST: samples whose target hour does not exist (March) or that would land on an already used timestamp
     (October, clamped month ends) are skipped.
   - Counters (a Clean Data child has "Conversion to Differential" = true **and** the source values rise, at most
     1 % falling steps): the copy is the row's last value plus the source difference; a negative difference (counter
     reset) counts as 0, so the reading keeps rising. Rows whose Clean Data converts to differential but which hold
     interval values are copied as they are.
   - Values that are not numbers are copied as they are. Notes are not copied.
   - Not replayed: rows written by a calculation (`Output` targets) or a data source channel / data point, rows
     without samples, rows with a single sample (constants, states), rows with less than one week of history (one
     month for calendar rows, e.g. a few hours of test data), objects in **Excluded Objects**, and other
     classes such as Base Data.
   - The offset of a row with less than a year of history is stored in **Replay State** when the job first sees the
     row and kept from then on (otherwise every copy would make the history longer and the start would be copied
     again). Delete the row's entry after changing its history by hand.
2. **Rolls module dates.** `weeks = floor((today − Shifted Until) / 7)`. If at least 1, all dates below the
   organisation move by that many weeks, so the mix of done, due and overdue stays as in the snapshot:
   - Appointments (`due`, `lastDone`, `created`) and maintenance messages (`start`, `doneAt`, `created`,
     `informRequested`): new sample of the `Data` string. **Entries with mail recipients are not shifted** (logged),
     so the demo never triggers reminder mails.
   - Actions, nonconformities (`Create Date`, `Planned Date`, `Done Date`) and obligations (`issue date`,
     `current version date`, `Date of Examination`): new file sample `DataModel_v2_<yyyyMMddHHmm>.json` like the
     Control Center writes it. The JSON is edited as a tree, unknown keys are kept.
   - Then **Shifted Until** moves forward by the same number of weeks.
3. **Monthly energy invoices.** In the first cycle of a month in which the configured clean data rows reach the new
   month, one ZUGFeRD PDF (EN16931) each for electricity and gas for the previous month is created and imported
   into the **Invoice Directory** (same import as in the Control Center). Consumption = monthly sum of the row,
   neutral supplier "Energieversorger Nord", work price and base price from the service object, 19 % VAT, number
   `EN-<yyyyMM>-S` / `EN-<yyyyMM>-G`. An invoice that already exists (HTTP 409) is skipped, so restarts are
   harmless.

## Safety

- Every configured organisation must have **"Demo" in its name** (case-insensitive), otherwise it is refused with an
  error in the log and nothing below it is touched.
- Only objects below the accepted organisations are written; the invoice directory must be one of them. On the
  service object the job writes only `Status`, `Shifted Until` and `Replay State`.
- The service user only has write rights on the demo (see below), so a misconfiguration cannot reach customer data.
- Dry run: `-o dryrun=true` logs what would be written and writes nothing.

## Service object

Create an object of class **JEDemo** in the Service Directory:

| Attribute | Value |
|---|---|
| Enabled | on |
| Cycle Time | 3600000 (1 h) |
| Organization | the demo organisation(s) |
| Shifted Until | the day the module dates were last moved to (e.g. the snapshot date) |
| Excluded Objects | optional: rows or folders the job must not touch, e.g. rows with manual entries |
| Invoice Directory | optional: an Energy Invoices Directory below the demo organisation |
| Electricity Row / Gas Row | clean data rows in kWh whose monthly sums are billed |
| Electricity Price / Gas Price | net EUR/kWh (default 0.28 / 0.09) |
| Electricity Base Price / Gas Base Price | net EUR per month (default 12.50 / 15.00, 0 = no line) |
| Replay State | leave empty, written by the job |

## Service user and API key

1. Control Center, user administration: a user (e.g. `demo-replay`) in a new group with **write** rights on the
   demo organisation's owner group only, no other rights, no Sys Admin.
2. Create an API key for this user (user context menu, "API key" dialog, or `bin/jevis-create-apikey "demo-replay"`
   on the server, which writes the key to a file).
3. Put the key into `/etc/jevis/jevis.env` as `JEDEMO_API_KEY=...`. `bin/jedemo` passes it to the job as
   `JEVIS_API_KEY` and refuses to start without it. Do **not** use the shared `JEVIS_API_KEY` of the other
   services: the job would then run with their rights.

## Installation (Linux)

```bash
cd /home/jevis/jevis
mvn -t <toolchains.xml> package -pl JEDemo -am        # never -P jevisserver
sudo cp var/templates/jedemo.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now jedemo.service
tail -f var/log/jedemo.log
```

Dry run first (one cycle, nothing written), as user jevis:

```bash
source /etc/jevis/jevis.env; export JEVIS_API_KEY="$JEDEMO_API_KEY"
java -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <organisation id> -o dryrun=true
```

`-sm single -jid <ids>` runs one cycle for the given organisations (settings from the service object), `-sm complete`
one cycle for the configured ones, `-sm service` (systemd) runs every Cycle Time while the service object is enabled.
