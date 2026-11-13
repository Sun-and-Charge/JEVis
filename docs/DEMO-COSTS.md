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
| `SetupPlan` | the setup plan: objects, templates, configurations, invoice cases, backfill, deletions, checks |
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
plan that does not exist yet; numbers are existing ids of the live demo.

1. **Backup** the objects that are changed or deleted (templates 23951/23952, configuration 23518, the base data and
   invoice folders in `delete`) and the JEDemo service object.
2. **Objects** in plan order (parents first) with their attribute samples, units and the synthetic histories;
   the clean data rows copy the settings named in the plan (e.g. hourly view of gas from 23686).
3. **Templates** (attribute "Template File") next to the old template, then the **configurations** with the keys
   resolved to the new ids.
4. **JEDemo service object:** write "Invoice Cases" (keys resolved). From then on the old attributes Invoice Directory,
   Electricity/Gas Row and the prices are ignored.
5. Let JEDataProcessor process the new clean data rows for the whole history, then **backfill the invoices** (first
   with `-o dryrun=true`):
   `java -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid <organisation id> -o invoicesFrom=2025-01 -o invoicesOnly=true`
6. **Delete** what the plan lists (old templates, old configuration and its broken linked outputs, empty duplicate
   grid operator, manual invoice values, the invoices of the old fixed electricity/gas billing), each only after the
   check named there.
7. Work through the **checks** of the plan, then open each configuration for a month of 2026 in the Control Center and
   the web app: "Differenz Netto" ≈ 0 except in the deviation months.

The live server needs the new JEDemo jar (invoice cases) and web service (calculation fixes) before step 5.
