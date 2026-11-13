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
| C | Gas RLM - Musterwerk | Musterwerk | 15-min gas energy (clean 23572), hourly view | demand charge on the hourly peak, CO₂ price, energy tax |
| D | Gas SLP - Außenlager Süd | Außenlager Süd | monthly volume reading | energy = m³ × state number × heating value (23946) |
| E | Fernwärme - Außenlager Süd | Außenlager Süd | hourly heat meter | work price with price escalation clause (gas and heat price index), capacity price on the connected load, metering price |
| F | Ladestrom - Dienstwagen Musterwerk | Musterwerk | charge points 1–3 (23925, 23927, 23923) | charging bill per company car (plate, cost centre, personnel no.); no supplier invoice |

Every case except F has a column "Rechnung" (from the imported invoices of the case) and "Differenz" next to the
measured costs, and a line "Prüfung Rechnung" (invoiced net − contract price × invoiced quantity).

**Prices:** net, plausible 2025/2026 values. Unit prices (`NON_PERIODIC`) have one sample on 1 January 2025 and one on
1 January 2026. Yearly amounts (`YEARLY_VALUE`) have one sample per year 2025–2030 because the calculation only uses
a sample of the year of the time frame. Demand charges are ranging tables of the grid operator, read from the latest
sample only, so they have one value for both years.

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
`JEDemo/target/costdemo-table.md`); the live values differ with the real demo data, the shape is the same.

| Monat | A Messung | A Rechnung | B Messung | B Rechnung | C Messung | C Rechnung | D Messung | D Rechnung | E Messung | E Rechnung | F Messung |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 2025-01 | 9.127,00 | 9.126,99 | 1.003,65 | 1.003,65 | 3.194,71 | 3.194,70 | 1.022,31 | 1.022,29 | 4.300,47 | 4.300,45 | 238,34 |
| 2025-07 | 9.250,76 | 9.250,69 | 832,40 | 832,42 | 565,57 | 565,56 | 114,94 | 114,90 | 937,41 | 937,41 | 240,88 |
| 2026-01 | 8.637,47 | 8.637,48 | 955,53 | 955,41 | 2.989,91 | 2.989,94 | 1.000,29 | 1.049,96 | 4.136,47 | 4.136,46 | 251,83 |
| 2026-02 | 7.983,56 | 7.983,62 | 951,23 | 951,25 | 2.487,11 | 2.579,07 | 863,30 | 863,27 | 3.661,48 | 3.661,53 | 219,49 |
| 2026-03 | 8.657,24 | 8.695,47 | 944,44 | 944,53 | 2.220,51 | 2.220,50 | 671,21 | 671,18 | 3.141,36 | 3.141,39 | 250,26 |
| 2026-04 | 8.555,88 | 8.555,87 | 904,28 | 904,23 | 1.538,19 | 1.538,21 | 447,80 | 447,81 | 2.198,77 | 2.218,55 | 244,10 |
| 2026-07 | 8.832,42 | 9.322,93 | 807,74 | 807,64 | 544,91 | 544,94 | 113,82 | 113,78 | 959,86 | 959,85 | 259,46 |
| 2026-10 | 8.669,46 | 8.669,49 | 879,43 | 879,28 | 1.447,49 | 1.447,46 | 428,32 | 428,33 | 2.087,57 | 2.087,62 | 249,37 |

## Code and generated files

| Class | Purpose |
|---|---|
| `CostCases` | the cases, the objects they need (new site, contracts, rows, base data, invoice folders) and the invoice cases |
| `TemplateFactory` | builds the Result Calculation Template of a case (inputs, formulas, output grid) and its Accounting Configuration |
| `History` | deterministic synthetic histories of the new rows (SLP counters, heat meter, price indices) |
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
3. **Templates** (attribute "Template File") next to the old template, then the **configurations** with the keys
   resolved to the new ids.
4. **JEDemo service object:** write "Invoice Cases" (keys resolved). From then on the old attributes Invoice Directory,
   Electricity/Gas Row and the prices are ignored.
5. **Fixes** of existing objects: dashboard 24054 "01 - Übersicht" loses the image widget 21 whose image points to a
   Clean Data row (new "Data Model File" sample, every other key kept); skipped if the widget no longer looks like that.
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
a setting is written only if the latest value differs, a template / configuration file only if its content (keys
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
java -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <organisation id> -o costSetup=dryrun
less "$(ls -t var/log/costdemo-setup-dryrun-*.md | head -1)"
```

Read the report: "Result" must not say "refused"; the sections "Problems" and "Warnings" list what blocks or what
to check (e.g. a unit that differs, an object to delete that was renamed); "Objects" lists every object to create
(class, name, parent path), every sample to write (histories with count and time range) and every update (old → new);
"Delete" lists each deletion with the result of its checks. Then apply:

```bash
# 2. apply: backup to var/backup/costdemo-<time>/, then the writes, deletions last; report costdemo-setup-apply-<time>.md
java -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <organisation id> -o costSetup=apply
less "$(ls -t var/log/costdemo-setup-apply-*.md | head -1)"

# 3. a second dry run must report "nothing to do"
java -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <organisation id> -o costSetup=dryrun
```

If the apply stopped at a step, fix the cause (the report names the step and the error) and run the apply again.
Then the manual steps (the apply report lists them with the new ids):

```bash
# 4. JEDataProcessor for the new clean data rows (ids from the report: B.clean, C.hourly, D.clean, E.clean);
#    the running jedataprocessor.service does it as well in its next cycles. With the key of the services:
source /etc/jevis/jevis.env
java -jar JEDataProcessor/target/JEDataProcessor-1.5-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <B.clean>,<C.hourly>,<D.clean>,<E.clean>

# 5. invoice backfill with the demo key, first dry
source /etc/jevis/jevis.env; export JEVIS_API_KEY="$JEDEMO_API_KEY"
java -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <organisation id> -o invoicesFrom=2025-01 -o invoicesOnly=true -o dryrun=true
java -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <organisation id> -o invoicesFrom=2025-01 -o invoicesOnly=true

# 6. start the job again
sudo systemctl start jedemo.service
tail -f var/log/jedemo.log
```

Then the checks of the plan (listed at the end of the report). Options: `-o costSetupReport=<directory>` (default
`var/log`), `-o costSetupBackup=<directory>` (default `var/backup/costdemo-<time>`); `-o dryrun=true` turns an apply into
a dry run. To undo a deletion, restore the object from the recycle bin (Control Center); values that were replaced
are in the backup JSON.
