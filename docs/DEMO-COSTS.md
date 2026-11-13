# Demo organisation: cost calculation cases

The demo organisation ("Sun and Charge Demo") shows the cost calculation (Control Center: AccountingPlugin, web:
"Kostenrechnung") with six cases. All names, prices and meter points are neutral or synthetic (no customer data).
The cases are defined in code (`JEDemo`, package `org.jevis.jedemo.costs`); the files that go to the server are
generated from it, and JEDemo creates the supplier invoices of each month, so measurement and invoice can be compared.

## The cases

| Key | Configuration | Site | Metering | What it shows |
|---|---|---|---|---|
| A | Strom RLM - Musterwerk Hauptanschluss | Musterwerk | 15-min grid purchase (clean 23734) | work price, grid demand charge on the peak of the year up to the month, grid work price, metering, levies, electricity tax |
| B | Strom SLP - Außenlager Süd | Außenlager Süd (new) | monthly meter reading | base price, work price, grid base/work price, levies |
| C | Gas RLM - Musterwerk | Musterwerk | 15-min gas energy (clean 23572), hourly view | demand charge on the hourly peak, CO₂ price, energy tax; from 11/2025 (the live row 23572 has data only from then on, the invoice backfill skips the months before with a warning) |
| D | Gas SLP - Außenlager Süd | Außenlager Süd | monthly volume reading | energy = m³ × state number × heating value (23946) |
| E | Fernwärme - Außenlager Süd | Außenlager Süd | hourly heat meter | work price with price escalation clause (gas and heat price index), capacity price on the connected load, metering price |
| F | Ladestrom - Dienstwagen Musterwerk | Musterwerk | charge points 1–3 (clean 23925, 23927, 23923 of the raw rows 23922, 23920, 23919; synthetic 15-min history, see below) | charging bill per company car (plate, cost centre, personnel no.); no supplier invoice |

Every case except F has a column "Rechnung" (from the imported invoices of the case) and "Differenz" next to the
measured costs, and a line "Prüfung Rechnung" (invoiced net − contract price × invoiced quantity).

**Prices:** net, plausible 2025/2026 values. Unit prices (`NON_PERIODIC`) have one sample on 1 January 2025 and one on
1 January 2026. Yearly amounts (`YEARLY_VALUE`) have one sample per year 2025–2030 because the calculation only uses
a sample of the year of the time frame. Demand charges are ranging tables of the grid operator, read from the latest
sample only, so they have one value for both years.

**Taxes and levies** (shared "Staatliche Abgaben" object, ct/kWh net):

| | 2025 | 2026 | 2027 |
|---|---:|---:|---:|
| KWKG-Umlage (Allocation 02) | 0.277 | 0.446 | 0.451 |
| Aufschlag für besondere Netznutzung A, up to 1 GWh, formerly § 19 StromNEV (Allocation 03) | 1.558 | 1.559 | 2.027 |
| Offshore-Netzumlage (Allocation 05) | 0.816 | 0.941 | 1.143 |
| Stromsteuer | 2.05 | 2.05 | 2.05 |
| CO₂ price BEHG, gas (Allocation 08) | 1.001 (55 €/t) | 1.092 (60 €/t) | as 2026 (corridor 55–65 €/t not final yet) |
| Gasspeicherumlage (Allocation 09) | 0.299 (from 1/2025), 0.289 (from 7/2025) | 0 (abolished) | 0 |
| Energiesteuer Gas | 0.55 | 0.55 | 0.55 |

VAT 19 % for all media. The 2027 electricity levies are the values the transmission system operators published in
10/2026 (netztransparenz.de). A levy at zero is not printed on the invoices (the gas storage levy from 2026 on); the
cost calculation still shows its line with 0.

**Deliberate invoice errors** (the "Differenz" is ≈ 0 in all other months, cents from rounding):

| Case | Month | Error |
|---|---|---|
| A | 2026-03 | metering line charged twice |
| A | 2026-07 | previous year's work price |
| C | 2026-02 | estimated consumption (+4 %) |
| D | 2026-01 | previous year's work price |
| E | 2026-04 | metering price charged twice |

## Monthly net amounts

From the test `CostCasesTest` with synthetic stand-ins for the live rows (written to
`JEDemo/target/costdemo-table.md`); the live values differ with the real demo data, the shape is the same. F uses the
charge point profile from 2025-01 on (on live it starts 2025-11): 150–250 kWh per point and month, 28 ct/kWh net in
2025 and 30 ct/kWh in 2026.

| Monat | A Messung | A Rechnung | B Messung | B Rechnung | C Messung | C Rechnung | D Messung | D Rechnung | E Messung | E Rechnung | F Messung |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 2025-01 | 9.127,00 | 9.126,99 | 1.003,65 | 1.003,65 | 3.335,07 | 3.335,06 | 1.051,81 | 1.051,79 | 4.300,47 | 4.300,45 | 182,31 |
| 2025-07 | 9.250,76 | 9.250,69 | 832,40 | 832,42 | 582,53 | 582,52 | 117,45 | 117,40 | 937,41 | 937,41 | 160,04 |
| 2026-01 | 8.637,47 | 8.637,48 | 955,53 | 955,41 | 2.989,91 | 2.989,94 | 1.000,29 | 1.049,96 | 4.136,47 | 4.136,46 | 195,91 |
| 2026-02 | 7.983,56 | 7.983,62 | 951,23 | 951,25 | 2.487,11 | 2.579,07 | 863,30 | 863,27 | 3.661,48 | 3.661,53 | 197,49 |
| 2026-03 | 8.657,24 | 8.695,47 | 944,44 | 944,53 | 2.220,51 | 2.220,50 | 671,21 | 671,18 | 3.141,36 | 3.141,39 | 193,42 |
| 2026-04 | 8.555,88 | 8.555,87 | 904,28 | 904,23 | 1.538,19 | 1.538,21 | 447,80 | 447,81 | 2.198,77 | 2.218,55 | 184,87 |
| 2026-07 | 8.832,42 | 9.322,93 | 807,74 | 807,64 | 544,91 | 544,94 | 113,82 | 113,78 | 959,86 | 959,85 | 176,00 |
| 2026-10 | 8.669,46 | 8.669,49 | 879,43 | 879,28 | 1.447,49 | 1.447,46 | 428,32 | 428,33 | 2.087,57 | 2.087,62 | 188,38 |

## Charge point data (case F)

The live charge point rows hold unusable values (read 2026-11-13): 23922 "Strom - Ladestrom - Ladepunkt 1" monthly
6–17 kWh (values like 0.0000955) and its clean row 23925 only 0.3–3.7 kWh; 23920 "… Ladepunkt 2" 8–35 kWh (clean
23927); 23919 "… Ladepunkt 3" zeros since 2026-05 and a negative month 2025-12 (clean 23923). 23921 "Strom - Ladestrom
Summe" (Data, below 23677 like the three rows) is the output of a calculation that sums the points, 23924 below it its
clean row. The plan fix `REPLACE_HISTORY` replaces them:

1. **Raw rows** 23922, 23920, 23919 (checked by id, class Data, exact name, parent 23677 and below the organisation;
   otherwise skipped with a report line): all "Value" samples are deleted and the profile `CHARGING_POINT`
   (`History.chargingPoint`, seeded by the row id) is written from 2025-11-01 00:00 (Europe/Berlin) up to the time of
   the run: kWh per quarter hour (not a counter) at every quarter hour, zero outside the sessions. Company car
   charging: on working days mostly one session (arrival 07:00–09:30 or 16:00–18:00, sometimes a second one in the
   other half of the day, some days without), 11 kW (2.75 kWh per quarter hour) for 1–3 h with a partial first
   quarter hour and a short taper, weekends and public holidays rarely. Per month 150–250 kWh per point: Ladepunkt 1
   ≈ 190–222 kWh, Ladepunkt 2 ≈ 185–214 kWh, Ladepunkt 3 ≈ 208–230 kWh (2025-11 … 2026-10), together ≈ 600–675 kWh,
   i.e. ≈ 170–200 € net per month in configuration F. The raw rows' "Period" is set to PT15M from the start.
2. **Clean rows** 23925, 23927, 23923 (must be Clean Data below their raw row): Enabled = true, Conversion to
   Differential = false, Value is a Quantity = true, Period = PT15M, written at the configuration time stamp where they
   do not hold from the start of the history; then all their values are deleted.
3. **Sum** 23921 and its clean row 23924: all values deleted; 23924 gets the same clean settings. The dry run names
   the calculation whose "Output" targets 23921 ("is the output of the Calculation … [id]").

The values of the clean rows and of the sum are deleted before the replacement and again after it (something a
service computed from half-written rows meanwhile). Everything is backed up first (`var/backup/costdemo-<time>/`, the
raw rows with all their old samples). A second run checks the raw rows at every quarter hour of two fixed windows
(2025-11-03 … 2025-11-16 and 2026-10-05 … 2026-10-18): if they hold the profile, it reports "history CHARGING_POINT in
place" and does nothing. Template F and configuration F stay as they are (they read the clean rows). JEDemo replays
each raw row from its own previous year (P52W), so the new history is continued afterwards.

```bash
cd /home/jevis/jevis
git pull
mvn -t <toolchains.xml> package -pl JEDemo -am           # never -P jevisserver
sudo systemctl stop jedemo.service
source /etc/jevis/jevis.env; export JEVIS_API_KEY="$JEDEMO_API_KEY"
JEDEMO="java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedemo-single.log -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid 23048"

# dry run: section "Fixes" lists the REPLACE_HISTORY lines and the calculation of 23921
$JEDEMO -o costSetup=dryrun
less "$(ls -t var/log/costdemo-setup-dryrun-*.md | head -1)"
$JEDEMO -o costSetup=apply
less "$(ls -t var/log/costdemo-setup-apply-*.md | head -1)"
$JEDEMO -o costSetup=dryrun                              # must say "nothing to do"
sudo systemctl start jedemo.service

# with the key of the services: clean rows of the points, then the sum (JECalc), then the clean row of the sum
source /etc/jevis/jevis.env
java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedataprocessor-single.log -jar JEDataProcessor/target/JEDataProcessor-1.5-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid 23925,23927,23923
java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jecalc-single.log -jar JECalc/target/JECalc-1.0.1-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <calculation id from the report>
java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedataprocessor-single.log -jar JEDataProcessor/target/JEDataProcessor-1.5-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid 23924
```

If the report finds no calculation for 23921 (not readable for the demo user), skip the JECalc line: the jecalc
service computes the sum again in its next cycle, the JEDataProcessor then 23924. The apply report lists the same
commands under "Manual steps". Then open configuration F for a month of 2026: about 190–220 kWh per charge point.

## Code and generated files

| Class | Purpose |
|---|---|
| `CostCases` | the cases, the objects they need (new site, contracts, rows, base data, invoice folders) and the invoice cases |
| `TemplateFactory` | builds the Result Calculation Template of a case (inputs, formulas, output grid) and its Accounting Configuration |
| `History` | deterministic synthetic histories of the new rows (SLP counters, heat meter, price indices) and the charge point profile of case F |
| `SetupPlan` | the setup plan: objects, templates, configurations, invoice cases, backfill, deletions (with their checks), fixes, checks |
| `CostSetup`, `SetupReport`, `SetupBackup`, `DashboardWidgetFix` | carry out the plan on the server (`-o costSetup=dryrun` or `apply`): report, backup, writes, deletions, the dashboard fix |
| `CostDemoGenerator` | writes `JEDemo/src/main/resources/costdemo/` (templates, configurations, `setup-plan.json`) |
| `InvoiceCase`, `InvoiceCalculator`, `ContractValues` (package `org.jevis.jedemo`) | the monthly invoices of the JEDemo job: lines from the contract prices of the case, deviations per month |

After a change of the cases run `CostDemoGenerator` (or copy `JEDemo/target/costdemo-generated` after `mvn test`) and
commit the files; `CostDemoFilesTest` fails while they are out of date. `CostCasesTest` calculates every month of
2025-01 … 2026-10 with the web's `ResultCalculator` and checks: no warnings, gross = net × 1.19, the template reads the
invoices exactly, measured ≈ invoiced except in the deviation months.

The templates use only what the Control Center's AccountingPlugin can evaluate as well. Two calculation fixes in
`org.jevis.commons.trc.ResultCalculator` came with the cases: `YEARLY_VALUE` is prorated to the whole days of the time
frame (a month counted one day less before), and a time restriction with reducing time frame "none" runs from the
start of the fixed time frame to the end of the requested one (peak of the year up to the month).

## Setup on a server (one time)

`setup-plan.json` lists, top to bottom, what is created, changed and deleted. `"@key"` stands for an object of the
plan that does not exist yet; numbers are existing ids of the live demo. The JEDemo job carries it out
(`-o costSetup=dryrun|apply`, class `CostSetup`):

1. **Backup** of every existing object that is changed or deleted (with all objects below a deleted one), the JEDemo
   service object and the dashboard of the fix: per object a JSON file with all attributes, units and samples, file
   samples (templates, dashboard, invoice PDFs) as files, into `var/backup/costdemo-<time>/` (`manifest.json` lists
   them). If the backup fails, nothing is written.
2. **Objects** in plan order (parents first) with their attribute samples, units and the synthetic histories (up to
   the time of the run); the clean data rows copy the settings named in the plan (e.g. hourly view of gas from 23686).
   Settings of data rows (Data: Period; Clean Data: Enabled, Conversion to Differential, Value is a Quantity, Period,
   Period Alignment and the copied ones) are written at the configuration time stamp `1989-12-31T23:00:00Z`
   (`CostSetup.CONFIG_TIMESTAMP`, as the Control Center does): the JEDataProcessor applies a setting only from its time
   stamp on. Settings of other objects get the time of the run, dated prices keep their dates.
3. **Templates** (attribute "Template File") next to the old template, then the **configurations** with the keys
   resolved to the new ids.
4. **JEDemo service object:** write "Invoice Cases" (keys resolved). From then on the old attributes Invoice Directory,
   Electricity/Gas Row and the prices are ignored.
5. **Fixes** of existing objects: dashboard 24054 "01 - Übersicht" loses the image widget 21 whose image points to a
   Clean Data row (new "Data Model File" sample, every other key kept); skipped if the widget no longer looks like that.
   `CLEAN_REPROCESS` (rows B.clean, C.hourly, D.clean, E.clean): if a setting of such a row or of its raw row was not
   in force at the start of the row's history (and was therefore written early in step 2), all "Value" samples of the
   clean row are deleted (backed up first) so the JEDataProcessor computes it again from the first raw value.
   `REPLACE_HISTORY` (charge points of case F, see "Charge point data (case F)" below).
   `REISSUE_INVOICES` (folders of C and D): the gas invoices `EN-2025MM-GR` / `-GS` that were issued without the gas
   storage levy go to the recycle bin, and their amounts are taken out of the folder's series (the sample whose note
   names the invoice number: deleted if it holds only this invoice, else reduced and the number removed from the note,
   in the same step, so a repeated run never subtracts twice). The invoice backfill (step 7) then issues them again
   with the levy; invoices that already have the line are skipped.
6. **Delete** (recycle bin) what the plan lists, only after everything above was written and only if its checks pass:
   the old templates and configuration once the new ones exist (`AFTER_CREATED`), the empty duplicate grid operator
   and the manual invoice values if no template, configuration or dashboard names them (`NOT_REFERENCED`), their
   folder if it is empty then (`NO_CHILDREN`), and from the old invoice folders "Strom"/"Gas" only the invoices
   `EN-yyyyMM-S` / `EN-yyyyMM-G` (`OLD_INVOICES`; the folder with its series only if nothing else is in it).
7. By hand afterwards: JEDataProcessor for the new clean data rows, the **invoice backfill** (first with
   `-o dryrun=true`), the **checks** of the plan, then open each configuration for a month of 2026 in the Control
   Center and the web app: "Differenz Netto" ≈ 0 except in the deviation months.

The live server needs the new JEDemo jar (invoice cases, cost setup) and web service (calculation fixes) first.

**How it matches and why it can run twice.** New objects are found by parent + class + name (an existing one is
used, two of them are a problem); existing ones by id, and their class must be the one in the plan. Samples are only
added where the time stamp has none, a different value at the same time stamp is an update (old → new in the report),
a setting is written only if the latest value differs (for data rows: also if the value in force at the start of
the history is not the wanted one, then at the configuration time stamp; a later sample stays), a template / configuration file only if its content (keys
resolved) differs, a unit only if the attribute has none (a different unit is reported and kept). Histories are
generated up to the time of the run and only missing time stamps are written. Deleted objects are not found again,
a fix whose widget is gone is skipped. A second run therefore reports "nothing to do"; after a failure (the run
stops at the first failing step, before any deletion) the next run continues where it stopped.

**Safety.** The organisation's name must contain "Demo" and match the plan; every object that is created, written or
deleted must be below it (checked by walking the parents while planning and again right before each write); the only
object outside it that is written is the JEDemo service object whose "Organization" names the organisation, and only
its "Invoice Cases". A deletion whose object has another class or name than the plan says is skipped. With any
problem the apply writes nothing.

### Setup on live (step 3)

As user `jevis` in `/home/jevis/jevis`, after updating the code and building the jar as in docs/DEMO.md
(`mvn -t <toolchains.xml> package -pl JEDemo -am`, never `-P jevisserver`) and deploying the web service:

```bash
cd /home/jevis/jevis
sudo systemctl stop jedemo.service                       # no replay or invoices while the setup runs
source /etc/jevis/jevis.env; export JEVIS_API_KEY="$JEDEMO_API_KEY"

# 1. dry run: reads everything, writes nothing; report var/log/costdemo-setup-dryrun-<time>.md (and .json)
java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedemo-single.log -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <organisation id> -o costSetup=dryrun
less "$(ls -t var/log/costdemo-setup-dryrun-*.md | head -1)"
```

Read the report: "Result" must not say "refused"; the sections "Problems" and "Warnings" list what blocks or what
to check (e.g. a unit that differs, an object to delete that was renamed); "Objects" lists every object to create
(class, name, parent path), every sample to write (histories with count and time range) and every update (old → new);
"Delete" lists each deletion with the result of its checks. Then apply:

```bash
# 2. apply: backup to var/backup/costdemo-<time>/, then the writes, deletions last; report costdemo-setup-apply-<time>.md
java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedemo-single.log -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <organisation id> -o costSetup=apply
less "$(ls -t var/log/costdemo-setup-apply-*.md | head -1)"

# 3. a second dry run must report "nothing to do"
java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedemo-single.log -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <organisation id> -o costSetup=dryrun
```

If the apply stopped at a step, fix the cause (the report names the step and the error) and run the apply again.
Then the manual steps (the apply report lists them with the new ids):

```bash
# 4. JEDataProcessor for the new clean data rows (ids from the report: B.clean, C.hourly, D.clean, E.clean);
#    the running jedataprocessor.service does it as well in its next cycles. With the key of the services:
source /etc/jevis/jevis.env
java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedataprocessor-single.log -jar JEDataProcessor/target/JEDataProcessor-1.5-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <B.clean>,<C.hourly>,<D.clean>,<E.clean>

# 5. invoice backfill with the demo key, first dry
source /etc/jevis/jevis.env; export JEVIS_API_KEY="$JEDEMO_API_KEY"
java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedemo-single.log -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <organisation id> -o invoicesFrom=2025-01 -o invoicesOnly=true -o dryrun=true
java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedemo-single.log -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <organisation id> -o invoicesFrom=2025-01 -o invoicesOnly=true

# 6. start the job again
sudo systemctl start jedemo.service
tail -f var/log/jedemo.log
```

### Repair after the first apply (2026-11-13)

The first apply wrote the settings of the new rows at the time of the run (2026-11-13). The JEDataProcessor applies a
setting only from its time stamp on, so it computed 2025-01 … 2026-10 of the clean rows without them: B.clean and
D.clean hold the counter readings instead of the monthly consumption (the invoice dry run billed 42–72 MWh per month for
B, E even GWh), C.hourly stayed empty (no period before 2026-11-13). The repair is the same setup run with the fixed
jar: it writes every row setting again at the configuration time stamp and deletes the values computed before
(`CLEAN_REPROCESS`), then the JEDataProcessor computes the rows again. No invoice was imported yet (only the dry run);
if invoices of the cases were imported with the wrong values, delete them in the case folders before the backfill.

C.hourly ("Stundenwerte", Clean Data below the clean data row 23572) stays: the JEDataProcessor takes the first parent
of a clean data row as its raw row whatever its class (`CleanDataObject` → `ObjectHandler.getFirstParent`) and reads its "Value" and "Period"; it was empty only because its
Period (and the other settings) started on 2026-11-13, before that its period was zero.

```bash
cd /home/jevis/jevis
git pull
mvn -t <toolchains.xml> package -pl JEDemo -am           # never -P jevisserver
sudo systemctl stop jedemo.service
source /etc/jevis/jevis.env; export JEVIS_API_KEY="$JEDEMO_API_KEY"
JEDEMO="java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedemo-single.log -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <organisation id>"

# dry run: the row settings "… @ 1989-12-31T23:00Z" and "· Value: delete all … samples" of B.clean, D.clean, E.clean
$JEDEMO -o costSetup=dryrun
less "$(ls -t var/log/costdemo-setup-dryrun-*.md | head -1)"
$JEDEMO -o costSetup=apply
less "$(ls -t var/log/costdemo-setup-apply-*.md | head -1)"
$JEDEMO -o costSetup=dryrun                              # must say "nothing to do"

# JEDataProcessor with the key of the services on the four clean rows (ids in the report)
source /etc/jevis/jevis.env
java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedataprocessor-single.log -jar JEDataProcessor/target/JEDataProcessor-1.5-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <B.clean>,<C.hourly>,<D.clean>,<E.clean>

# invoice dry run with the demo key: monthly consumption now (B ~2.5–3 MWh, D ~80–900 m³ × heating value, E ~3–32 MWh)
export JEVIS_API_KEY="$JEDEMO_API_KEY"
$JEDEMO -o invoicesFrom=2025-01 -o invoicesOnly=true -o dryrun=true
$JEDEMO -o invoicesFrom=2025-01 -o invoicesOnly=true
sudo systemctl start jedemo.service
```

Then the checks of the plan (listed at the end of the report). Options: `-o costSetupReport=<directory>` (default
`var/log`), `-o costSetupBackup=<directory>` (default `var/backup/costdemo-<time>`); `-o dryrun=true` turns an apply into
a dry run. To undo a deletion, restore the object from the recycle bin (Control Center); values that were replaced
are in the backup JSON.
