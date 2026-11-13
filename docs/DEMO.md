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
   - Data notes (`Value`, `Tag`, `User`): the samples move by the same weeks, local time kept, so the notes stay on
     the replayed curves and in the web app's default time frame. The old samples are deleted first, then the moved
     ones written; this needs the **delete** (or execute) right on the notes. Without it the job logs an error per
     notes object and changes nothing there.
   - Then **Shifted Until** moves forward by the same number of weeks.
3. **Monthly energy invoices.** In the first cycle of a month, one ZUGFeRD PDF (EN16931) per **invoice case** for the
   previous month is created and imported into the case's **Energy Invoices Directory** (same import as in the Control
   Center), once the case's data reach the new month. Invoice number `EN-<yyyyMM>-<suffix>` (e.g. `EN-202610-SR`).
   An invoice that already exists (HTTP 409) is skipped, so restarts are harmless.
   - The cases are the JSON list in **Invoice Cases** (see below); the demo cost cases (docs/DEMO-COSTS.md) use them so
     that the column "Rechnung" of the cost calculation can be compared with the measurement.
   - Lines are priced from the contract objects exactly as the result calculation templates price them: unit prices
     = last sample at or before the start of the month; yearly amounts (EUR/a) = sample of the calendar year × days of
     the month / days of the year (unit DAY); demand charge = peak × ranging table of the grid operator / 12 (unit
     KWT, peak = maximum of the peak row × factor from 1 January to the end of the month); capacity price = connected
     load × EUR/kW/a / 12; indexed work price = base × (constant + Σ weight × index / base index). Consumption = sum of
     the row in the month, rounded to whole kWh (for gas in m³: × state number × heating value).
   - **Deviations** declared per case and month (`"2026-03"`, or `"03"` = every March) make deliberate billing errors:
     `DUPLICATE_LINE` (a line billed twice), `PREVIOUS_YEAR_PRICE` (a line priced one year earlier),
     `QUANTITY_FACTOR` (estimated consumption, e.g. 1.04).
   - Backfill: `-o invoicesFrom=2025-01` also bills all months from then on (existing invoices are skipped),
     `-o invoicesOnly=true` skips replay and date rolling. Example:
     `java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedemo-single.log -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <organisation id> -o invoicesFrom=2025-01 -o invoicesOnly=true -o dryrun=true`
   - Without "Invoice Cases" the old fixed pair is created as before: electricity and gas of **Electricity Row** /
     **Gas Row**, supplier "Energieversorger Nord", work and base price from the service object, 19 % VAT, number
     `EN-<yyyyMM>-S` / `-G`, imported into **Invoice Directory**.

## Safety

- Every configured organisation must have **"Demo" in its name** (case-insensitive), otherwise it is refused with an
  error in the log and nothing below it is touched.
- Only objects below the accepted organisations are written; the invoice directory (of every invoice case) must be
  one of them. Prices and rows of the invoice cases are only read. On the
  service object the job writes only `Status`, `Shifted Until` and `Replay State`.
- The service user only has write rights on the demo (see below), so a misconfiguration cannot reach customer data.
- Dry run: `-o dryrun=true` logs what would be written and writes nothing.

## Service object

Create an object of class **JEDemo** in the Service Directory:

| Attribute | Value |
|---|---|
| Enable | on |
| Cycle Time | 3600000 (1 h) |
| Organization | the demo organisation(s) |
| Shifted Until | the day the module dates were last moved to (e.g. the snapshot date) |
| Excluded Objects | optional: rows or folders the job must not touch, e.g. rows with manual entries |
| Invoice Cases | optional: JSON list of invoice cases (below); if set, the five attributes after it and the prices are not used |
| Invoice Directory | optional (old fixed invoices): an Energy Invoices Directory below the demo organisation |
| Electricity Row / Gas Row | clean data rows in kWh whose monthly sums are billed |
| Electricity Price / Gas Price | net EUR/kWh (default 0.28 / 0.09) |
| Electricity Base Price / Gas Base Price | net EUR per month (default 12.50 / 15.00, 0 = no line) |
| Replay State | leave empty, written by the job |

An invoice case (all object references are ids; the setup plan of the demo cost cases writes them with `"@key"` until
the objects exist, such a case is refused with an error in the log):

```json
{ "key": "A", "name": "Strom RLM - Musterwerk Hauptanschluss", "medium": "ELECTRICITY", "suffix": "SR",
  "directory": 24700, "seller": "Energieversorger Nord", "sellerVatId": "DE000000000",
  "meterPointLabel": "Zählpunkt", "meterPoint": "DE0000000000000000000000000000001",
  "consumption": { "row": 23734 },
  "peak": { "row": 23734, "factor": 4.0, "yearToDate": true },
  "vat": { "object": 23519, "attribute": "VAT Electricity" },
  "lines": [
    { "key": "ENERGY", "text": "Arbeitspreis", "type": "ENERGY", "price": { "object": 23510, "attribute": "Energy Price HT" }, "priceUnit": "ct/kWh" },
    { "key": "GRID_DEMAND", "text": "Netzentgelt Leistungspreis", "type": "DEMAND", "price": { "object": 23515, "attribute": "Demand Charge" } },
    { "key": "MSB", "text": "Messstellenbetrieb", "type": "YEARLY", "price": { "object": 23511, "attribute": "Net Cost" } } ],
  "deviations": [ { "month": "2026-03", "type": "DUPLICATE_LINE", "line": "MSB", "note": "Messstellenbetrieb doppelt" } ] }
```

- `medium`: `ELECTRICITY`, `GAS` or `DISTRICT_HEATING` (medium of the import); `directory`: the case's Energy Invoices
  Directory, best with this `Medium`, then the import keeps invoices and series in it.
- `consumption`: `row` (summed per month), optional `completeRow` (whose latest sample must reach the next month,
  default `row`), optional `heatingValue` and `stateNumber` (`{object, attribute}`, row in m³).
- `lines[].type`: `ENERGY`, `INDEXED` (with `index: {constant, terms: [{weight, value: {object, attribute}, base}]}`),
  `YEARLY`, `MONTHLY`, `DEMAND` (needs `peak`), `CAPACITY` (with `capacity: {object, attribute}`); `priceUnit` of
  energy prices `ct/kWh` (default), `EUR/kWh` or `EUR/MWh`.

## Service user and API key

1. Control Center, user administration: a user (e.g. `demo-replay`) in a new group with **read, write, create and
   delete** rights on the demo organisation's owner group only, no other rights, no Sys Admin. Delete is needed to
   move the data notes.
2. Create an API key for this user, in the Control Center ("API key" dialog) or on the server **with its own key
   file**:
   ```bash
   bin/jevis-create-apikey "demo-replay" "jedemo@$(hostname)" /home/jevis/jevis/etc/jedemo.key
   ```
   **Never run `bin/jevis-create-apikey "demo-replay"` without the third argument:** it then overwrites
   `etc/service.key`, the key file of all other services (`<apikeyfile>` in `etc/jevis.xml`). After their next
   restart they would log in as `demo-replay`, see only the demo and stop processing every other organisation.
   If it happened: copy `etc/service.key` to `etc/jedemo.key`, then create a new key for the original service user
   with `bin/jevis-create-apikey "<service user>"` and revoke its lost old key in the "API key" dialog.
3. Put the key into `/etc/jevis/jevis.env` as `JEDEMO_API_KEY=...`, e.g.
   `echo "JEDEMO_API_KEY=$(cat etc/jedemo.key)" | sudo tee -a /etc/jevis/jevis.env`. `bin/jedemo` passes it to the
   job as `JEVIS_API_KEY` and refuses to start without it. Do **not** use the shared `JEVIS_API_KEY` of the other
   services: the job would then run with their rights, and they with the job's.

**Logging of single runs:** started directly with `java -jar` the job has no log configuration and log4j prints only
errors. The commands therefore pass the service log configuration (`-Dlog4j.configurationFile=etc/log4j2_service.xml`)
and a log file (`-DserviceLogFile=var/log/jedemo-single.log`); read it with `tail -80 var/log/jedemo-single.log`. The
cost setup also writes its own report file (see docs/DEMO-COSTS.md).

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
java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedemo-single.log -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <organisation id> -o dryrun=true
```

`-sm single -jid <ids>` runs one cycle for the given organisations (settings from the service object), `-sm complete`
one cycle for the configured ones, `-sm service` (systemd) runs every Cycle Time while the service object is enabled.
`-sm single -jid <organisation id> -o costSetup=dryrun|apply` runs the one-time setup of the demo cost cases instead of
a cycle (report in `var/log/costdemo-setup-*.md`, backup in `var/backup/`), see docs/DEMO-COSTS.md, "Setup on live".
